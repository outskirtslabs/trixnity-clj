(ns ol.trixnity.schemas-test
  (:require
   [clojure.test :refer [deftest is]]
   [malli.core :as m]
   [ol.trixnity.schemas :as sut])
  (:import
   [de.connect2x.trixnity.core.model.events EmptyEventContent]
   [java.time Duration]))

(deftest timeout-schemas-reference-the-named-duration-schema-test
  (let [schema-entries (sut/schemas {})
        registry       (sut/registry {})
        duration       (Duration/ofSeconds 1)
        duration-keys  [::sut/timeout
                        ::sut/decryption-timeout
                        ::sut/fetch-timeout]]
    (is (contains? schema-entries ::sut/duration))
    (doseq [k duration-keys]
      (is (= ::sut/duration (get schema-entries k)))
      (is (m/validate (m/schema k {:registry registry}) duration))
      (is (not (m/validate (m/schema k {:registry registry}) "PT1S"))))))

(deftest event-content-class-schemas-require-trixnity-event-content-types-test
  (let [registry   (sut/registry {})
        valid-type EmptyEventContent
        class-ids  [::sut/room-event-content-class
                    ::sut/state-event-content-class
                    ::sut/global-account-data-event-content-class
                    ::sut/room-account-data-event-content-class]]
    (doseq [schema-id class-ids]
      (is (m/validate (m/schema schema-id {:registry registry}) valid-type))
      (is (not (m/validate (m/schema schema-id {:registry registry}) String))))))

(deftest room-event-content-schema-requires-room-event-content-values-test
  (let [registry (sut/registry {})]
    (is (m/validate (m/schema ::sut/room-event-content {:registry registry})
                    EmptyEventContent/INSTANCE))
    (is (not (m/validate (m/schema ::sut/room-event-content {:registry registry})
                         :content)))))

(deftest validate-uses-shared-schema-registry-by-default-test
  (let [duration (Duration/ofSeconds 1)]
    (is (= duration (sut/validate! ::sut/timeout duration)))
    (is (thrown-with-msg?
         clojure.lang.ExceptionInfo
         #"Schema validation failed"
         (sut/validate! ::sut/timeout "PT1S")))))

(deftest validate-allows-an-explicit-custom-registry-test
  (let [schema-registry-var (ns-resolve 'ol.trixnity.schemas 'schema-registry)]
    (is schema-registry-var)
    (when schema-registry-var
      (let [custom-registry (assoc (var-get schema-registry-var)
                                   ::even-int
                                   [:fn even?])]
        (is (= 2 (sut/validate! custom-registry ::even-int 2)))
        (is (thrown-with-msg?
             clojure.lang.ExceptionInfo
             #"Schema validation failed"
             (sut/validate! custom-registry ::even-int 3)))))))
