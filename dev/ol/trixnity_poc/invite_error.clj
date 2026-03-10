(ns ol.trixnity-poc.invite-error
  (:require
   [clojure.string :as str]))

(defn already-in-room-invite-failure? [error]
  (boolean
   (some-> error
           ex-message
           str/lower-case
           (str/includes? "already in the room"))))
