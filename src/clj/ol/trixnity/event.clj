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

(defn type [event]
  (::mx/type event))

(defn room-id [event]
  (::mx/room-id event))

(defn event-id [event]
  (::mx/event-id event))

(defn sender [event]
  (::mx/sender event))

(defn sender-display-name [event]
  (::mx/sender-display-name event))

(defn body [event]
  (::mx/body event))

(defn key [event]
  (::mx/key event))

(defn relates-to [event]
  (::mx/relates-to event))

(defn relation-event-id [event]
  (get-in event [::mx/relates-to ::mx/relation-event-id]))

(defn raw [event]
  (::mx/raw event))

(defn text? [event]
  (= "m.room.message" (type event)))

(defn reaction? [event]
  (= "m.reaction" (type event)))
