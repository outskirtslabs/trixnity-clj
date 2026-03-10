(ns ol.trixnity-poc.facade-main
  (:require
   [ol.trixnity.client :as client]
   [ol.trixnity.repo :as repo]
   [ol.trixnity.schemas :as mx]
   [ol.trixnity-poc.bot-logic :as bot-logic]
   [ol.trixnity-poc.config :as config]
   [ol.trixnity-poc.invite-error :as invite-error]
   [ol.trixnity-poc.room-state :as room-state])
  (:import
   (java.nio.file Files Path)))

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

(defn- handlers [bot-user-id]
  (letfn [(should-handle-sender? [sender]
            (and bot-user-id
                 sender
                 (not= (str sender) (str bot-user-id))))]
    {:on-text
     (fn [{:keys [sender body reply!]}]
       (when (and reply!
                  (should-handle-sender? sender))
         (reply! (bot-logic/mirrored-body body))))

     :on-reaction
     (fn [{:keys [sender key react!]}]
       (when (and react!
                  key
                  (should-handle-sender? sender))
         (react! key)))

     :on-error
     (fn [{:keys [stage ex]}]
       (println "facade event error at" stage ":" (ex-message ex)))}))

(defn- start-runtime! [cfg]
  (let [facade-config (merge
                       {::mx/homeserver-url (config/url->string (:homeserver-url cfg))
                        ::mx/username       (:username cfg)
                        ::mx/password       (:password cfg)
                        :encryption?        true}
                       (repo/sqlite4clj-config
                        {:database-path (:database-path cfg)
                         :media-path    (:media-path cfg)}))
        client-handle (client/open-client! facade-config)
        bot-user-id   (client/current-user-id client-handle)
        runtime       (client/start! (assoc facade-config ::mx/client client-handle)
                                     (handlers bot-user-id))]
    (assoc runtime :bot-user-id bot-user-id)))

(defn- resolve-room! [runtime cfg]
  (let [stored-room-id (room-state/load-room-id (:room-id-file cfg))]
    (if stored-room-id
      (do
        (println "Reusing room from state file:" stored-room-id)
        stored-room-id)
      (let [created-room-id (str (client/ensure-room! runtime
                                                      {:room-name (:room-name cfg)}))]
        (room-state/save-room-id! (:room-id-file cfg) created-room-id)
        (println "Created room and persisted id to" (:room-id-file cfg))
        created-room-id))))

(defn run-poc! []
  (let [cfg     (config/load-config)
        _       (ensure-local-paths! cfg)
        runtime (start-runtime! cfg)
        room-id (resolve-room! runtime cfg)]
    (println "Room name:" (:room-name cfg))
    (println "Room id:" room-id)
    (println "Bot user:" (or (:bot-user-id runtime) :unknown))
    (when-let [invite-user (:invite-user cfg)]
      (try
        (println "Inviting user:" invite-user)
        (client/invite-user! runtime
                             {:room-id room-id
                              :user-id invite-user})
        (println "Invite completed for:" invite-user)
        (catch Throwable ex
          (if (invite-error/already-in-room-invite-failure? ex)
            (println "Invite skipped; user already in room:" invite-user)
            (println "Invite failed:" (ex-message ex))))))
    (assoc runtime
           :config cfg
           :room-id room-id)))

(defn -main [& _]
  (let [runtime (run-poc!)
        events  (:events runtime)]
    (loop []
      (.take ^java.util.concurrent.BlockingQueue events)
      (recur))))
