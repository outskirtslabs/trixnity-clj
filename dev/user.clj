(ns user
  (:require
   #_[portal.api :as p]
   [clj-reload.core :as clj-reload]
   [missionary.core :as m]
   [ol.trixnity.room :as room]
   [ol.trixnity.schemas :as mx]))

((requiring-resolve 'hashp.install/install!))

(set! *warn-on-reflection* true)

;; Configure the paths containing clojure sources we want clj-reload to reload
(clj-reload/init {:dirs      ["src" "dev" "test"]
                  :no-reload #{'user 'dev 'ol.dev.portal}})

(defn- collect-values [flow n]
  (m/? (->> flow
            (m/eduction (take n))
            (m/reduce conj []))))

(defn latest-n-room-messages
  "Returns the latest `n` normalized timeline events for `room-id` in
  chronological order."
  ([client room-id n]
   (latest-n-room-messages client room-id n
                           (java.time.Duration/ofSeconds 5)))
  ([client room-id n fetch-timeout]
   (let [message-flows (first (collect-values
                               (room/get-last-timeline-events-list
                                client
                                room-id
                                n
                                1
                                {::mx/fetch-timeout fetch-timeout
                                 ::mx/fetch-size    n})
                               1))]
     (->> message-flows
          (mapv #(first (collect-values % 1)))
          reverse))))

(comment
  (defonce ps ((requiring-resolve 'ol.dev.portal/open-portals)))

  (clj-reload/reload)
  (clj-reload/reload {:only :all}) ;; rcf
  #_(reset! my-portal/portal-state nil)
  ;;(clojure.repl.deps/sync-deps)
  ;; Example:
  ;; (latest-n-room-messages client "!room:example.org" 20)
  ;;;
  )
