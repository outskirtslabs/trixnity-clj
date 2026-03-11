(ns ol.trixnity.room.message
  (:require
   [ol.trixnity.event :as event]
   [ol.trixnity.schemas :as mx]))

(set! *warn-on-reflection* true)

(def ^:private schema-registry
  (mx/registry {}))

(defn text
  "Builds a text message-spec map understood by [[ol.trixnity.room/send!]]."
  ([body]
   (text body {}))
  ([body opts]
   (mx/validate! schema-registry
                 ::mx/MessageSpec
                 (cond-> {::mx/kind :text
                          ::mx/body body}
                   (::mx/format opts) (assoc ::mx/format (::mx/format opts))
                   (::mx/formatted-body opts)
                   (assoc ::mx/formatted-body (::mx/formatted-body opts))))))

(defn reply-to
  "Associates reply metadata from a normalized event map."
  [message ev]
  (mx/validate! schema-registry
                ::mx/MessageSpec
                (assoc message
                       ::mx/reply-to
                       (cond-> {::mx/event-id (event/event-id ev)}
                         (event/relates-to ev)
                         (assoc ::mx/relates-to
                                (event/relates-to ev))))))
