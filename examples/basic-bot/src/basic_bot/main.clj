(ns basic-bot.main
  (:require
   [clojure.string :as str]
   [missionary.core :as m]
   [ol.trixnity.client :as client]
   [ol.trixnity.event :as event]
   [ol.trixnity.repo :as repo]
   [ol.trixnity.room :as room]
   [ol.trixnity.room.message :as msg]
   [ol.trixnity.schemas :as mx])
  (:import
   [java.nio.file Files Path]
   [java.nio.file.attribute FileAttribute]
   [java.time Duration]))

(set! *warn-on-reflection* true)

(def ^:private help-text
  "Commands: !help, !ping, !echo <text>, !topic")

(def ^:private echo-usage-text
  "Usage: !echo <text>")

(def ^:private topic-prompts
  ["Which campy spaceship from a bargain-bin sci-fi movie would you trust least with your luggage?"
   "If a 1980s android burst into song, what power ballad would introduce its evil plan?"
   "What is the most dramatic line reading imaginable for 'Captain, the disco nebula is unstable'?"
   "Which seventies or eighties musical deserves a totally unnecessary laser-cannon remake?"
   "You must cast one glam-rock villain for a moon-base musical. Who gets the role?"])

(def ^:private greeting-pattern
  #"(?i)\b(hi|hello|hey)\b")

(def ^:private thanks-pattern
  #"(?i)\b(thanks|thank you|thx)\b")

(defn load-env
  "Loads the required bot login environment variables.

  Returns a namespaced config map for [[ol.trixnity.client/open]]. Throws
  when any required variable is missing."
  []
  (let [required-env
        {"MATRIX_HOMESERVER_URL" ::mx/homeserver-url
         "MATRIX_BOT_USER_ID"    ::mx/user-id
         "MATRIX_BOT_PASSWORD"   ::mx/password}
        missing
        (into []
              (comp
               (filter (fn [[env-name _]]
                         (str/blank? (System/getenv env-name))))
               (map key))
              required-env)]
    (when (seq missing)
      (throw (ex-info "Missing required Matrix bot environment variables."
                      {:missing-env missing})))
    (into {}
          (map (fn [[env-name schema-key]]
                 [schema-key (System/getenv env-name)]))
          required-env)))

(defn ensure-data-dir!
  "Creates the local bot data directory and returns the resulting `Path`."
  [path]
  (Files/createDirectories
   (if (instance? Path path)
     ^Path path
     (Path/of (str path) (make-array String 0)))
   (make-array FileAttribute 0)))

(defn next-invite-joins
  "Calculates which invited rooms should be joined from the latest snapshot.

  Input is the current in-memory set of invite room ids plus the latest
  room snapshot vector from [[ol.trixnity.room/get-all-flat]]."
  [joining-room-ids rooms]
  (let [invite-room-ids    (->> rooms
                                (keep (fn [room-snapshot]
                                        (when (= :invite (::mx/membership room-snapshot))
                                          (::mx/room-id room-snapshot))))
                                vec)
        invite-room-id-set (set invite-room-ids)]
    {:joining-room-ids invite-room-id-set
     :room-ids-to-join (into []
                             (remove joining-room-ids)
                             invite-room-ids)}))

(defn- reply-action [ev body]
  {:kind     :send-message
   :body     body
   :reply-to ev})

(defn decide-action
  "Chooses at most one bot action for a normalized timeline event.

  Commands take precedence over passive replies. Returns `nil` when the
  event should be ignored."
  [{:keys [bot-user-id topic-picker]} ev]
  (let [sender      (event/sender ev)
        body        (event/body ev)
        command-end (when body
                      (or (str/index-of body " ")
                          (count body)))
        command     (when (and body
                               (str/starts-with? body "!"))
                      (subs body 0 command-end))]
    (when (and (event/text? ev)
               body
               (not= (str bot-user-id) (str sender)))
      (cond
        (str/starts-with? body "!")
        (cond
          (= command "!help")
          (reply-action ev help-text)

          (= command "!ping")
          (reply-action ev "pong")

          (= body "!echo")
          (reply-action ev echo-usage-text)

          (str/starts-with? body "!echo ")
          (reply-action ev (subs body 6))

          (= command "!topic")
          (reply-action ev (topic-picker))

          :else
          nil)

        (re-find thanks-pattern body)
        {:kind     :send-reaction
         :key      "❤️"
         :event-id (event/event-id ev)}

        (re-find greeting-pattern body)
        (reply-action ev (str "Hi, "
                              (or (event/sender-display-name ev)
                                  sender)
                              "!"))

        :else
        nil))))

(defn- apply-action! [client ev action]
  (case (:kind action)
    :send-message
    (room/send-message
     client
     (event/room-id ev)
     (-> (msg/text (:body action))
         (msg/reply-to (:reply-to action)))
     {::mx/timeout (Duration/ofSeconds 5)})

    :send-reaction
    (room/send-reaction
     client
     (event/room-id ev)
     {::mx/room-id  (event/room-id ev)
      ::mx/event-id (:event-id action)}
     (:key action))

    nil))

(defn- start-virtual-thread! [f]
  (Thread/startVirtualThread
   (reify Runnable
     (run [_]
       (f)))))

(defn start-invite-loop!
  "Starts the background invite watcher for the example bot.

  The loop reduces [[ol.trixnity.room/get-all-flat]], tracks invite room ids
  in memory, and joins new invites with [[ol.trixnity.room/join-room]]."
  [client]
  (start-virtual-thread!
   (fn []
     (m/?
      (m/reduce
       (fn [joining-room-ids rooms]
         (let [{:keys [joining-room-ids room-ids-to-join]}
               (next-invite-joins joining-room-ids rooms)]
           (reduce
            (fn [seen room-id]
              (try
                (m/? (room/join-room client room-id))
                seen
                (catch Throwable ex
                  (binding [*out* *err*]
                    (println "Failed to join invited room:" room-id)
                    (println (ex-message ex)))
                  (disj seen room-id))))
            joining-room-ids
            room-ids-to-join)))
       #{}
       (room/get-all-flat client))))))

(defn start-timeline-loop!
  "Starts the background timeline watcher for the example bot.

  The loop reduces [[ol.trixnity.room/get-timeline-events-from-now-on]],
  passes each event through [[decide-action]], and sends the chosen reply or
  reaction."
  [client bot-user-id]
  (start-virtual-thread!
   (fn []
     (m/?
      (m/reduce
       (fn [_ ev]
         (when-let [action (decide-action
                            {:bot-user-id  bot-user-id
                             :topic-picker #(rand-nth topic-prompts)}
                            ev)]
           (m/? (apply-action! client ev action)))
         nil)
       nil
       (room/get-timeline-events-from-now-on
        client
        {::mx/decryption-timeout (Duration/ofSeconds 8)}))))))

(defn run-bot!
  "Opens the example client, starts sync, and launches the background loops.

  Returns a small runtime map containing the client handle, bot user id, and
  the invite and timeline loop threads."
  []
  (let [env           (load-env)
        data-dir      (Path/of "./dev-data/basic-bot" (make-array String 0))
        _             (ensure-data-dir! data-dir)
        client-config
        (merge
         env
         (repo/sqlite4clj-config
          {:database-path (str (.resolve data-dir "trixnity.sqlite"))
           :media-path    (str (.resolve data-dir "media"))}))
        opened-client (m/? (client/open client-config))]
    (m/? (client/start-sync opened-client))
    (m/? (client/await-running opened-client
                               {::mx/timeout (Duration/ofSeconds 30)}))
    (let [bot-user-id   (client/current-user-id opened-client)
          invite-loop   (start-invite-loop! opened-client)
          timeline-loop (start-timeline-loop! opened-client bot-user-id)]
      {:client        opened-client
       :bot-user-id   bot-user-id
       :invite-loop   invite-loop
       :timeline-loop timeline-loop})))

(defn- stop-runtime! [{:keys [client]}]
  (when client
    (try
      (m/? (client/stop-sync client))
      (catch Throwable _
        nil))
    (try
      (m/? (client/close client))
      (catch Throwable _
        nil))))

(defn -main [& _]
  (let [runtime                     (run-bot!)
        shutdown-thread
        (Thread.
         (fn []
           (stop-runtime! runtime))
         "basic-bot-shutdown")]
    (.addShutdownHook (Runtime/getRuntime) shutdown-thread)
    (println "Bot user:" (or (:bot-user-id runtime) :unknown))
    @(promise)))
