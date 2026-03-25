(ns ol.trixnity-poc.room-state-test
  (:require
   [clojure.java.io :as io]
   [clojure.test :refer [deftest is testing]]
   [ol.trixnity-poc.room-state :as sut]))

(defn- delete-file-if-exists! [path]
  (let [file (io/file path)]
    (when (.exists file)
      (.delete file))))

(defn- ensure-parent-dir! [path]
  (io/make-parents path)
  path)

(deftest load-test
  (testing "returns nil when file missing"
    (let [path "./kotlin/build/test-room-state-missing.txt"]
      (delete-file-if-exists! path)
      (is (nil? (sut/load-room-id path)))))

  (testing "returns stored non-blank room id text"
    (let [path (ensure-parent-dir! "./kotlin/build/test-room-state-invalid.txt")]
      (spit path "room-not-an-id")
      (is (= "room-not-an-id" (sut/load-room-id path))))))

(deftest save-and-load-roundtrip-test
  (let [path    (ensure-parent-dir! "./kotlin/build/test-room-state-roundtrip-clj.txt")
        room-id "!abc123:example.org"]
    (delete-file-if-exists! path)
    (sut/save-room-id! path room-id)
    (is (= room-id (sut/load-room-id path)))))
