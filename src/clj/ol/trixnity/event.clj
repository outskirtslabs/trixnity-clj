(ns ol.trixnity.event
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

(defn body [event]
  (::mx/body event))

(defn key [event]
  (::mx/key event))

(defn relates-to [event]
  (::mx/relates-to event))

(defn raw [event]
  (::mx/raw event))

(defn text? [event]
  (= "m.room.message" (type event)))

(defn reaction? [event]
  (= "m.reaction" (type event)))
