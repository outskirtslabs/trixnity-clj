(ns ol.trixnity-poc.main
  (:require
   [missionary.core :as m]
   [ol.trixnity-poc.bot-logic :as bot-logic]
   [ol.trixnity-poc.config :as config]
   [ol.trixnity-poc.invite-error :as invite-error]
   [ol.trixnity-poc.room-state :as room-state]
   [ol.trixnity.client :as client]
   [ol.trixnity.event :as event]
   [ol.trixnity.repo :as repo]
   [ol.trixnity.room :as room]
   [ol.trixnity.room.message :as msg]
   [ol.trixnity.schemas :as mx])
  (:import
   [java.nio.file Files Path]
   [java.time Duration]))

(defn- create-dirs! [^Path path]
  (Files/createDirectories path
                           (make-array java.nio.file.attribute.FileAttribute
                                       0))
  path)

(defn- ->path [value]
  (when value
    (if (instance? Path value)
      value
      (Path/of (str value) (make-array String 0)))))

(defn- ensure-local-paths! [cfg]
  (some-> (:media-path cfg) ->path create-dirs!)
  (some-> (:database-path cfg) ->path .getParent create-dirs!)
  (some-> (:room-id-file cfg) ->path .getParent create-dirs!)
  nil)

(defn- handler [client room-id bot-user-id]
  (fn [ev]
    (when (and bot-user-id
               (event/sender ev)
               (not= (str bot-user-id) (str (event/sender ev))))
      (cond
        (event/text? ev)
        (room/send-message client
                           room-id
                           (-> (msg/text (bot-logic/mirrored-body (event/body ev)))
                               (msg/reply-to ev))
                           {::mx/timeout (Duration/ofSeconds 5)})

        (event/reaction? ev)
        (when-let [target-event-id (event/relation-event-id ev)]
          (room/send-reaction client
                              room-id
                              {::mx/room-id  room-id
                               ::mx/event-id target-event-id}
                              (event/key ev)))

        :else
        nil))))

(defn- start-client! [cfg]
  (let [client-config (merge
                       {::mx/homeserver-url (config/url->string (:homeserver-url cfg))
                        ::mx/user-id        (:user-id cfg)
                        ::mx/password       (:password cfg)}
                       (repo/sqlite4clj-config
                        {:database-path (:database-path cfg)
                         :media-path    (:media-path cfg)}))
        opened-client (m/? (client/open client-config))]
    (m/? (client/start-sync opened-client))
    (m/? (client/await-running opened-client
                               {::mx/timeout (Duration/ofSeconds 30)}))
    opened-client))

(defn- resolve-room! [client cfg]
  (let [stored-room-id (room-state/load-room-id (:room-id-file cfg))]
    (if stored-room-id
      (do
        (println "Reusing room from state file:" stored-room-id)
        stored-room-id)
      (let [created-room-id (m/? (room/create-room client
                                                   {::mx/room-name (:room-name cfg)}))]
        (room-state/save-room-id! (:room-id-file cfg) created-room-id)
        (println "Created room and persisted id to" (:room-id-file cfg))
        created-room-id))))

(defn- start-subscription! [client room-id bot-user-id]
  (future
    (m/? (m/reduce
          (fn [_ ev]
            (when-let [task ((handler client room-id bot-user-id) ev)]
              (m/? task))
            nil)
          nil
          (room/get-timeline-events-from-now-on
           client
           {::mx/decryption-timeout (Duration/ofSeconds 8)})))))

(defn run-poc! []
  (let [cfg          (config/load-config)
        _            (ensure-local-paths! cfg)
        client       (start-client! cfg)
        bot-user-id  (client/current-user-id client)
        room-id      (resolve-room! client cfg)
        subscription (start-subscription! client room-id bot-user-id)]
    (println "Room name:" (:room-name cfg))
    (println "Room id:" room-id)
    (println "Bot user:" (or bot-user-id :unknown))
    (when-let [invite-user (:invite-user cfg)]
      (try
        (println "Inviting user:" invite-user)
        (m/? (room/invite-user client room-id invite-user))
        (println "Invite completed for:" invite-user)
        (catch Throwable ex
          (if (invite-error/already-in-room-invite-failure? ex)
            (println "Invite skipped; user already in room:" invite-user)
            (println "Invite failed:" (ex-message ex))))))
    {:bot-user-id  bot-user-id
     :client       client
     :config       cfg
     :room-id      room-id
     :subscription subscription}))

(defn -main [& _]
  (run-poc!)
  @(promise))
