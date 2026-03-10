(ns ol.trixnity-poc.room-state
  (:require
   [clojure.string :as str])
  (:import
   (java.nio.file Files Path StandardOpenOption)))

(defn- ->path ^Path [x]
  (if (instance? Path x)
    x
    (Path/of (str x) (make-array String 0))))

(defn load-room-id [path]
  (let [path (->path path)]
    (when (Files/exists path (make-array java.nio.file.LinkOption 0))
      (let [raw (str/trim (slurp (.toFile path)))]
        (when-not (str/blank? raw)
          raw)))))

(defn save-room-id! [path room-id]
  (let [path   (->path path)
        parent (.getParent path)]
    (when parent
      (Files/createDirectories parent (make-array java.nio.file.attribute.FileAttribute 0)))
    (Files/writeString path
                       (str room-id)
                       (into-array StandardOpenOption
                                   [StandardOpenOption/CREATE
                                    StandardOpenOption/TRUNCATE_EXISTING]))
    room-id))
