(ns ol.trixnity-poc.synapse-register-test
  (:require
   [clojure.test :refer [deftest is testing]]
   [ol.trixnity-poc.synapse-register :as sut]))

(deftest generate-mac-test
  (testing "follows Synapse HMAC format for admin user"
    (let [mac (sut/generate-mac
               {:nonce         "thisisanonce"
                :username      "pepper_roni"
                :password      "pizza"
                :admin?        true
                :shared-secret "shared_secret"
                :user-type     nil})]
      (is (= "48715842ad67d5dc9a9ee938a3bda4fcfae8d7c7" mac))))

  (testing "appends user_type when provided"
    (let [mac (sut/generate-mac
               {:nonce         "n"
                :username      "u"
                :password      "p"
                :admin?        false
                :shared-secret "s"
                :user-type     "bot"})]
      (is (= "0b5f80dd2e57b2bc10ff27cfdb04303c72ca3d4c" mac)))))
