(ns ol.trixnity-poc.bot-logic-test
  (:require
   [clojure.test :refer [deftest is]]
   [ol.trixnity-poc.bot-logic :as sut]))

(deftest mirrored-body-test
  (is (= "HELLO MATRIX"
         (sut/mirrored-body "Hello Matrix"))))
