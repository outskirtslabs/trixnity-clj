(ns ol.trixnity.event
  "Accessors and predicates for normalized event maps.

  This namespace provides convenience accessors over
  the normalized event shapes defined in [[ol.trixnity.schemas]] without
  exposing callers to raw bridge internals.

  Use these helpers when consuming timeline events, notifications, or reply
  metadata from [[ol.trixnity.room]], [[ol.trixnity.notification]], and
  [[ol.trixnity.room.message]]."
  (:refer-clojure :exclude [key type])
  (:require
   [ol.trixnity.schemas :as mx]))

(set! *warn-on-reflection* true)

(defn type
  "Returns the Matrix event type string from normalized `event`."
  [event]
  (::mx/type event))

(defn room-id
  "Returns the Matrix room id string from normalized `event`."
  [event]
  (::mx/room-id event))

(defn event-id
  "Returns the Matrix event id string from normalized `event`."
  [event]
  (::mx/event-id event))

(defn sender
  "Returns the sender user id string from normalized `event`."
  [event]
  (::mx/sender event))

(defn sender-display-name
  "Returns the sender display name from normalized `event`, if present."
  [event]
  (::mx/sender-display-name event))

(defn body
  "Returns the normalized message body from `event`, if present."
  [event]
  (::mx/body event))

(defn msgtype
  "Returns the Matrix room-message subtype from normalized `event`, if present."
  [event]
  (::mx/msgtype event))

(defn key
  "Returns the normalized reaction key or relation key from `event`, if present."
  [event]
  (::mx/key event))

(defn relates-to
  "Returns the normalized `::mx/relates-to` map from `event`, if present."
  [event]
  (::mx/relates-to event))

(defn relation-event-id
  "Returns the related event id from `event` reply or relation metadata, if present."
  [event]
  (get-in event [::mx/relates-to ::mx/relation-event-id]))

(defn raw
  "Returns the upstream raw event object carried by normalized `event`, if present."
  [event]
  (::mx/raw event))

(defn url
  "Returns the normalized MXC URI from `event`, if present."
  [event]
  (::mx/url event))

(defn encrypted-file
  "Returns the normalized encrypted-file map from `event`, if present."
  [event]
  (::mx/encrypted-file event))

(defn file-name
  "Returns the normalized file name from `event`, if present."
  [event]
  (::mx/file-name event))

(defn mime-type
  "Returns the normalized MIME type from `event`, if present."
  [event]
  (::mx/mime-type event))

(defn size-bytes
  "Returns the normalized byte size from `event`, if present."
  [event]
  (::mx/size-bytes event))

(defn duration
  "Returns the normalized [[java.time.Duration]] from `event`, if present."
  [event]
  (::mx/duration event))

(defn height
  "Returns the normalized media height from `event`, if present."
  [event]
  (::mx/height event))

(defn width
  "Returns the normalized media width from `event`, if present."
  [event]
  (::mx/width event))

(defn thumbnail-url
  "Returns the event-provided thumbnail MXC URI from `event`, if present."
  [event]
  (::mx/thumbnail-url event))

(defn thumbnail-encrypted-file
  "Returns the event-provided encrypted thumbnail map from `event`, if present."
  [event]
  (::mx/thumbnail-encrypted-file event))

(defn text?
  "Returns true when `event` is a Matrix room-message event."
  [event]
  (= "m.room.message" (type event)))

(defn reaction?
  "Returns true when `event` is a Matrix reaction event."
  [event]
  (= "m.reaction" (type event)))
