(ns ol.trixnity.internal
  (:require
   [missionary.core :as m])
  (:import
   [java.time Duration]
   [java.io Closeable]
   [ol.trixnity.bridge FlowBridge]))

(set! *warn-on-reflection* true)

(def ^:private observe-kotlin-flow
  (fn [client kotlin-flow emit]
    (FlowBridge/observe kotlin-flow
                        (FlowBridge/clientScope client)
                        emit)))

(defn observe-flow [client kotlin-flow]
  (m/observe
   (fn [emit]
     (let [sub (observe-kotlin-flow client kotlin-flow emit)]
       #(.close ^Closeable sub)))))

(defn observe-keyed-flow-map [client kotlin-outer-flow]
  (let [wrapped* (volatile! {})]
    (m/eduction
     (map (fn [kotlin-map]
            (if (nil? kotlin-map)
              (do
                (vreset! wrapped* {})
                nil)
              (let [next
                    (into {}
                          (map (fn [[k inner-flow]]
                                 [k
                                  (or (get @wrapped* k)
                                      (observe-flow client inner-flow))]))
                          kotlin-map)]
                (vreset! wrapped* next)
                next))))
     (observe-flow client kotlin-outer-flow))))

(defn observe-flow-list [client kotlin-outer-flow]
  (m/eduction
   (map (fn [kotlin-list]
          (when kotlin-list
            (mapv #(observe-flow client %) kotlin-list))))
   (observe-flow client kotlin-outer-flow)))

(defn suspend-task [bridge-fn & args]
  (fn [success failure]
    (let [sub (apply bridge-fn (concat args [success failure]))]
      #(.close ^Closeable sub))))

(defn duration->millis [duration]
  (some-> ^Duration duration .toMillis))
