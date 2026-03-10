(ns ol.trixnity-poc.invite-error-test
  (:require
   [clojure.test :refer [deftest is testing]]
   [ol.trixnity-poc.invite-error :as sut]))

(deftest already-in-room-invite-failure-test
  (testing "already-in-room errors are treated as expected"
    (let [error (RuntimeException.
                 "statusCode=403 Forbidden errorResponse=Forbidden(error=@ramblurr:outskirtslabs.com is already in the room.)")]
      (is (true? (sut/already-in-room-invite-failure? error)))))

  (testing "other errors are not treated as expected"
    (let [error (RuntimeException. "statusCode=500 Internal Server Error")]
      (is (false? (sut/already-in-room-invite-failure? error))))))
