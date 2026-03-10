(ns ol.project-test
  (:require
   [clojure.test :refer [deftest is]]
   [ol.project :as sut]))

(deftest namespace-loads-test
  (is (some? (find-ns 'ol.project)))
  (is (= 'ol.project (ns-name (the-ns 'ol.project)))))
