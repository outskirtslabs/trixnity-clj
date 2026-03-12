(ns ol.trixnity-missionary-spike.main
  (:require
   [clojure.string :as str]
   [missionary.core :as m]
   [ol.trixnity.client :as client]
   [ol.trixnity.repo :as repo]
   [ol.trixnity.room :as room]
   [ol.trixnity.schemas :as mx])
  (:import
   [java.nio.file Files Path]
   [java.time Duration]))

(set! *warn-on-reflection* true)

(def ^:private spike-root "./dev-data/missionary-spike")

(defn read-env [name]
  (System/getenv name))

(defn println! [& args]
  (apply println args))

(defn- required-env [name]
  (or (read-env name)
      (throw (ex-info (str "Missing required environment variable " name)
                      {:env name}))))

(defn- create-dirs! [^Path path]
  (Files/createDirectories path
                           (make-array java.nio.file.attribute.FileAttribute
                                       0))
  path)

(defn- ensure-local-paths! [{:keys [database-path media-path]}]
  (some-> database-path
          (Path/of (make-array String 0))
          .getParent
          create-dirs!)
  (some-> media-path
          (Path/of (make-array String 0))
          create-dirs!)
  nil)

(defn- load-config []
  {:homeserver-url (required-env "MATRIX_HOMESERVER_URL")
   :username       (required-env "MATRIX_BOT_USERNAME")
   :password       (required-env "MATRIX_BOT_PASSWORD")
   :room-id        (required-env "MATRIX_ROOM_ID")
   :database-path  (str spike-root "/trixnity.sqlite")
   :media-path     (str spike-root "/media")})

(defn- run-task [task]
  (m/? task))

(defn- cancel-exception? [ex]
  (or (instance? InterruptedException ex)
      (= "missionary.Cancelled" (.getName (class ex)))))

(defn- room-summary [room]
  (if (nil? room)
    "nil"
    (str (name (::mx/membership room))
         " "
         (::mx/room-id room)
         (when-let [room-name (::mx/room-name room)]
           (str " " room-name))
         (when (::mx/is-direct room)
           " [direct]"))))

(defn- flat-summary [rooms]
  (if (seq rooms)
    (str/join " | " (map room-summary rooms))
    "<empty>"))

(defn- nested-summary [room-flow-map]
  (if (seq room-flow-map)
    (str/join ", " (sort (keys room-flow-map)))
    "<empty>"))

(defn log-flow! [label value]
  (println! (str label ":" value)))

(defn start-flow-drain! [label flow printer]
  (future
    (try
      (m/? (m/reduce (fn [_ value]
                       (printer label value)
                       nil)
                     nil
                     flow))
      (catch Throwable ex
        (when-not (cancel-exception? ex)
          (println! (str label ":error:" (ex-message ex))))))))

(defn close-drain! [drain]
  (future-cancel drain)
  nil)

(defn close-runtime! [{:keys [client drains]}]
  (doseq [drain (reverse drains)]
    (close-drain! drain))
  (when client
    (run-task (client/close client)))
  nil)

(defn run-spike! []
  (let [cfg           (load-config)
        _             (ensure-local-paths! cfg)
        client-config (merge
                       {::mx/homeserver-url (:homeserver-url cfg)
                        ::mx/username       (:username cfg)
                        ::mx/password       (:password cfg)}
                       (repo/sqlite4clj-config
                        {:database-path (:database-path cfg)
                         :media-path    (:media-path cfg)}))
        _             (println! "opening client")
        client        (run-task (client/open client-config))
        drains*       (atom [])]
    (try
      (println! "starting sync")
      (run-task (client/start-sync client))
      (println! "awaiting RUNNING")
      (run-task (client/await-running client
                                      {::mx/timeout
                                       (Duration/ofSeconds 30)}))
      (println! "starting room observers")
      (let [flat-flow   (room/get-all-flat client)
            nested-flow (room/get-all client)
            room-flow   (room/get-by-id client (:room-id cfg))
            flat-drain
            (start-flow-drain! "flat"
                               (m/eduction (map flat-summary) flat-flow)
                               log-flow!)
            nested-drain
            (start-flow-drain! "nested"
                               (m/eduction (map nested-summary) nested-flow)
                               log-flow!)
            room-drain
            (start-flow-drain! (str "room " (:room-id cfg))
                               (m/eduction (map room-summary) room-flow)
                               log-flow!)
            drains      [flat-drain nested-drain room-drain]]
        (reset! drains* drains)
        (println! (str "spike running; waiting for updates. Ctrl-C to stop. room-id="
                       (:room-id cfg)))
        {:client  client
         :config  cfg
         :drains  drains
         :room-id (:room-id cfg)})
      (catch Throwable ex
        (doseq [drain (reverse @drains*)]
          (close-drain! drain))
        (run-task (client/close client))
        (throw ex)))))

(defn -main [& _]
  (let [runtime       (run-spike!)
        shutdown-hook (Thread. #(close-runtime! runtime))]
    (.addShutdownHook (Runtime/getRuntime) shutdown-hook)
    @(promise)))
