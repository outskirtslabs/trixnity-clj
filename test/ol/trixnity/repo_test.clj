(ns ol.trixnity.repo-test
  (:require
   [clojure.test :refer [deftest is]]
   [ol.trixnity.repo :as sut]
   [ol.trixnity.schemas :as schemas])
  (:import
   (java.nio.file Path)))

(deftest sqlite4clj-config-normalizes-path-like-values-test
  (let [config (sut/sqlite4clj-config {:database-path (Path/of "./tmp/state.sqlite" (make-array String 0))
                                       :media-path    (Path/of "./tmp/media" (make-array String 0))})]
    (is (= "./tmp/state.sqlite" (::schemas/database-path config)))
    (is (= "./tmp/media" (::schemas/media-path config)))))
