(ns ol.trixnity-poc.bot-logic
  (:require
   [clojure.string :as str])
  (:import
   (net.folivo.trixnity.core.model UserId)
   (net.folivo.trixnity.core.model.events.m ReactionEventContent RelatesTo$Annotation)))

(defn mirrored-body [original]
  (str/upper-case original))

(defn should-handle-sender? [^UserId sender ^UserId bot-user-id]
  (not= sender bot-user-id))

(defn reaction-to-mirror [^ReactionEventContent content]
  (let [rel (.getRelatesTo content)]
    (when (instance? RelatesTo$Annotation rel)
      (let [key (.getKey ^RelatesTo$Annotation rel)]
        (when key
          {:event-id (.getEventId ^RelatesTo$Annotation rel)
           :key      key})))))
