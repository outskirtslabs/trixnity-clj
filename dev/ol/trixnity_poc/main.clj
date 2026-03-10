(ns ol.trixnity-poc.main
  (:require
   [clojure.java.io :as io]
   [ol.trixnity-poc.config :as config]
   [ol.trixnity-poc.synapse-register :as synapse-register]))

(defn- ensure-parent-dir! [path]
  (some-> path io/file .getParentFile .mkdirs)
  path)

(defn- prepare-local-paths! [cfg]
  (-> (:media-path cfg) str io/file .mkdirs)
  (-> (:database-path cfg) str ensure-parent-dir!)
  (-> (:room-id-file cfg) str ensure-parent-dir!))

(defn- maybe-shared-secret-register! [cfg]
  (when-let [shared-secret (:registration-shared-secret cfg)]
    (try
      (synapse-register/register!
       {:base-url      (config/url->string (:homeserver-url cfg))
        :username      (:username cfg)
        :password      (:password cfg)
        :shared-secret shared-secret
        :admin?        (:bot-admin cfg)})
      (println "Shared-secret registration completed for" (:username cfg))
      (catch Throwable t
        (println "Shared-secret registration failed, will continue:" (ex-message t))))))

(defn run-poc! []
  (let [cfg (config/load-config)]
    (prepare-local-paths! cfg)
    (maybe-shared-secret-register! cfg)
    (println "Phase 2 helper runtime initialized.")
    (println "Homeserver:" (config/url->string (:homeserver-url cfg)))
    (println "Room name:" (:room-name cfg))
    (println "Room id file:" (str (:room-id-file cfg)))
    cfg))

(defn -main [& _]
  (run-poc!))
