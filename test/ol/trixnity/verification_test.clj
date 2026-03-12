(ns ol.trixnity.verification-test
  (:require
   [clojure.test :refer [deftest is]]
   [missionary.core :as m]
   [ol.trixnity.internal :as internal]
   [ol.trixnity.internal.bridge :as bridge]
   [ol.trixnity.schemas :as schemas]
   [ol.trixnity.verification :as sut]))

(defn- collect-values [flow n]
  (m/? (->> flow
            (m/eduction (take n))
            (m/reduce conj []))))

(deftest verification-surfaces-stay-thin-test
  (let [calls               (atom {})
        verification        {::schemas/their-user-id      "@alice:example.org"
                             ::schemas/timestamp          1
                             ::schemas/verification-state {::schemas/kind :ready}}
        verification-method {::schemas/kind :cross-signing-enabled}]
    (with-redefs [bridge/current-active-device-verification            (fn [client] (swap! calls assoc :current-device client) verification)
                  bridge/active-device-verification-flow               (fn [client] (swap! calls assoc :device-flow client) ::device-flow)
                  bridge/current-active-user-verifications             (fn [client] (swap! calls assoc :current-users client) [verification])
                  bridge/active-user-verifications-flow                (fn [client] (swap! calls assoc :users-flow client) ::users-flow)
                  bridge/self-verification-methods                     (fn [client] (swap! calls assoc :methods client) ::methods-flow)
                  m/relieve                                            (fn [_ flow] flow)
                  internal/observe-flow
                  (fn [_ kotlin-flow]
                    (m/observe
                     (fn [emit]
                       (future
                         (emit (case kotlin-flow
                                 ::device-flow nil
                                 ::users-flow []
                                 ::methods-flow verification-method))
                         (emit (case kotlin-flow
                                 ::device-flow verification
                                 ::users-flow [verification]
                                 ::methods-flow verification-method)))
                       (constantly nil))))]
      (is (= verification
             (sut/current-active-device-verification :client)))
      (is (= [nil verification]
             (collect-values (sut/active-device-verification :client) 2)))
      (is (= [verification]
             (sut/current-active-user-verifications :client)))
      (is (= [[] [verification]]
             (collect-values (sut/active-user-verifications :client) 2)))
      (is (= [verification-method verification-method]
             (collect-values (sut/get-self-verification-methods :client) 2))))))
