(ns ol.trixnity-poc.bot-logic
  (:require
   [clojure.string :as str]))

(defn mirrored-body [original]
  (str/upper-case original))
