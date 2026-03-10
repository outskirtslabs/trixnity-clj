(ns ol.trixnity.interop-test
  (:require
   [clojure.test :refer [deftest is testing]]
   [ol.trixnity.interop :as sut]
   [ol.trixnity.schemas :as schemas]))

(deftest login-with-password-blocking-validates-namespaced-request-keys-test
  (testing "unqualified keys are rejected before bridge invocation"
    (let [request {:homeserver-url "https://matrix.example.org"
                   :username       "bot"
                   :password       "secret"
                   :database-path  "./tmp/state/trixnity.sqlite"
                   :media-path     "./tmp/media"}]
      (try
        (sut/login-with-password-blocking request)
        (is false "expected schema validation failure")
        (catch clojure.lang.ExceptionInfo ex
          (is (= :com.fulcrologic.guardrails/validation-error
                 (:type (ex-data ex))))
          (is (= :args
                 (:com.fulcrologic.guardrails/failure-point (ex-data ex))))
          (is (= [:catn [:request ::schemas/LoginRequest]]
                 (:com.fulcrologic.guardrails/spec (ex-data ex)))))))))

(deftest from-store-blocking-validates-namespaced-request-keys-test
  (testing "unqualified keys are rejected before bridge invocation"
    (let [request {:database-path "./tmp/state/trixnity.sqlite"
                   :media-path "./tmp/media"}]
      (try
        (sut/from-store-blocking request)
        (is false "expected schema validation failure")
        (catch clojure.lang.ExceptionInfo ex
          (is (= :com.fulcrologic.guardrails/validation-error
                 (:type (ex-data ex))))
          (is (= :args
                 (:com.fulcrologic.guardrails/failure-point (ex-data ex))))
          (is (= [:catn [:request ::schemas/FromStoreRequest]]
                 (:com.fulcrologic.guardrails/spec (ex-data ex)))))))))

(deftest login-with-password-blocking-rejects-non-string-database-path-test
  (testing "::database-path must be a string value"
    (let [request {::schemas/homeserver-url "https://matrix.example.org"
                   ::schemas/username       "bot"
                   ::schemas/password       "secret"
                   ::schemas/database-path  :not-a-path
                   ::schemas/media-path     "./tmp/media"}]
      (try
        (sut/login-with-password-blocking request)
        (is false "expected schema validation failure")
        (catch clojure.lang.ExceptionInfo ex
          (is (= :com.fulcrologic.guardrails/validation-error
                 (:type (ex-data ex))))
          (is (= :args
                 (:com.fulcrologic.guardrails/failure-point (ex-data ex))))
          (is (= [:catn [:request ::schemas/LoginRequest]]
                 (:com.fulcrologic.guardrails/spec (ex-data ex)))))))))

(deftest start-sync-blocking-validates-namespaced-request-keys-test
  (testing "unqualified keys are rejected before bridge invocation"
    (let [request {:client :not-a-client}]
      (try
        (sut/start-sync-blocking request)
        (is false "expected schema validation failure")
        (catch clojure.lang.ExceptionInfo ex
          (is (= :com.fulcrologic.guardrails/validation-error
                 (:type (ex-data ex))))
          (is (= :args
                 (:com.fulcrologic.guardrails/failure-point (ex-data ex))))
          (is (= [:catn [:request ::schemas/StartSyncRequest]]
                 (:com.fulcrologic.guardrails/spec (ex-data ex)))))))))
