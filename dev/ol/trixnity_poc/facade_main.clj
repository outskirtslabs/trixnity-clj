(ns ol.trixnity-poc.facade-main
  (:require
   [ol.trixnity.client :as client]
   [ol.trixnity.schemas :as mx]
   [ol.trixnity.store.sqlite :as sqlite]
   [ol.trixnity-poc.bot-logic :as bot-logic]
   [ol.trixnity-poc.config :as config]
   [ol.trixnity-poc.room-state :as room-state])
  (:import
   (java.nio.file Files Path)
   (net.folivo.trixnity.core.model RoomId)))

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

(defn create-database [cfg]
  (sqlite/connect-exposed (str (:database-path cfg))))

(defn- try-client-user-id [runtime]
  (try
    (some-> (:client runtime) (.getUserId))
    (catch Throwable _
      nil)))

(defn- handlers [bot-user-id*]
  (letfn [(should-handle-sender? [sender]
            (or (nil? @bot-user-id*)
                (not= (str sender) (str @bot-user-id*))))]
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
  (let [facade-config {::mx/homeserver-url (config/url->string (:homeserver-url cfg))
                       ::mx/username       (:username cfg)
                       ::mx/password       (:password cfg)
                       ::mx/database       (create-database cfg)
                       ::mx/media-path     (str (:media-path cfg))
                       :encryption?        true}
        bot-user-id*  (atom nil)
        runtime       (client/start! facade-config
                                     (handlers bot-user-id*))]
    (reset! bot-user-id* (try-client-user-id runtime))
    runtime))

(defn- resolve-room! [runtime cfg]
  (let [stored-room-id (room-state/load-room-id (:room-id-file cfg))]
    (if stored-room-id
      (do
        (println "Reusing room from state file:" stored-room-id)
        (str stored-room-id))
      (let [created-room-id (let [room-id (client/ensure-room! runtime
                                                               {:room-name (:room-name cfg)})]
                              (if (instance? RoomId room-id)
                                room-id
                                (RoomId. (str room-id))))]
        (room-state/save-room-id! (:room-id-file cfg) created-room-id)
        (println "Created room and persisted id to" (:room-id-file cfg))
        (str created-room-id)))))

(defn run-poc! []
  (let [cfg     (config/load-config)
        _       (ensure-local-paths! cfg)
        runtime (start-runtime! cfg)
        room-id (resolve-room! runtime cfg)]
    (when-let [invite-user (:invite-user cfg)]
      (client/invite-user! runtime
                           {:room-id room-id
                            :user-id (str invite-user)}))
    (println "Room name:" (:room-name cfg))
    (println "Room id:" room-id)
    (println "Bot user:" (or (try-client-user-id runtime) :unknown))
    (assoc runtime
           :config cfg
           :room-id room-id)))

(defn -main [& _]
  (let [runtime (run-poc!)
        events  (:events runtime)]
    (loop []
      (.take ^java.util.concurrent.BlockingQueue events)
      (recur))))
