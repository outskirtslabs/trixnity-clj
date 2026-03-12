(ns ol.trixnity.room.message
  "Helpers for constructing normalized room message payloads.

  This namespace provides small builders for the message-spec maps accepted by
  [[ol.trixnity.room/send-message]].

  The helpers here focus on common text-message cases:

  - [[text]] builds a normalized text message payload
  - [[reply-to]] attaches reply metadata from a normalized event map

  Use [[ol.trixnity.event]] to inspect the events you are replying to."
  (:require
   [ol.trixnity.event :as event]
   [ol.trixnity.schemas :as mx]))

(set! *warn-on-reflection* true)

(defn text
  "Builds a text message-spec map understood by [[ol.trixnity.room/send-message]]."
  ([body]
   (text body {}))
  ([body opts]
   (mx/validate!
                 ::mx/MessageSpec
                 (cond-> {::mx/kind :text
                          ::mx/body body}
                   (::mx/format opts) (assoc ::mx/format (::mx/format opts))
                   (::mx/formatted-body opts)
                   (assoc ::mx/formatted-body (::mx/formatted-body opts))))))

(defn reply-to
  "Associates reply metadata from a normalized event map."
  [message ev]
  (mx/validate!
                ::mx/MessageSpec
                (assoc message
                       ::mx/reply-to
                       (cond-> {::mx/event-id (event/event-id ev)}
                         (event/relates-to ev)
                         (assoc ::mx/relates-to
                                (event/relates-to ev))))))
