(ns ol.trixnity.verification-test
  (:require
   [clojure.test :refer [deftest is testing]]
   [missionary.core :as m]
   [ol.trixnity.internal :as internal]
   [ol.trixnity.internal.bridge :as bridge]
   [ol.trixnity.schemas :as schemas]
   [ol.trixnity.verification :as sut])
  (:import
   [java.io Closeable]))

(defn- collect-values [flow n]
  (m/? (->> flow
            (m/eduction (take n))
            (m/reduce conj []))))

(defn- realize-task [task]
  (m/? task))

(deftype StubCloseable [closed-count]
  Closeable
  (close [_]
    (swap! closed-count inc)))

(deftest verification-surfaces-stay-thin-test
  (let [calls               (atom {})
        verification        {::schemas/their-user-id      "@alice:example.org"
                             ::schemas/timestamp          1
                             ::schemas/verification-state {::schemas/kind :ready}}
        verification-method {::schemas/kind :cross-signing-enabled}]
    (with-redefs [bridge/current-active-device-verification (fn [client] (swap! calls assoc :current-device client) verification)
                  bridge/active-device-verification-flow    (fn [client] (swap! calls assoc :device-flow client) ::device-flow)
                  bridge/current-active-user-verifications  (fn [client] (swap! calls assoc :current-users client) [verification])
                  bridge/active-user-verifications-flow     (fn [client] (swap! calls assoc :users-flow client) ::users-flow)
                  bridge/self-verification-methods          (fn [client] (swap! calls assoc :methods client) ::methods-flow)
                  m/relieve                                 (fn [_ flow] flow)
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

(deftest active-verification-snapshots-combine-device-and-user-state-test
  (let [device {::schemas/verification-id    "device:@alice:example.org:txn1"
                ::schemas/verification-kind  :device
                ::schemas/their-user-id      "@alice:example.org"
                ::schemas/their-device-id    "ALICEDEVICE"
                ::schemas/transaction-id     "txn1"
                ::schemas/timestamp          1
                ::schemas/verification-state {::schemas/kind :ready}}
        user   {::schemas/verification-id    "user:!room:example.org:$event"
                ::schemas/verification-kind  :user
                ::schemas/their-user-id      "@bob:example.org"
                ::schemas/room-id            "!room:example.org"
                ::schemas/request-event-id   "$event"
                ::schemas/timestamp          2
                ::schemas/verification-state {::schemas/kind :their-request}}]
    (with-redefs [bridge/current-active-device-verification (fn [client] (is (= :client client)) device)
                  bridge/current-active-user-verifications  (fn [client] (is (= :client client)) [user])]
      (is (= [device user]
             (sut/active-verification-snapshots :client)))
      (is (= [device user]
             (sut/status :client))))))

(deftest verification-mutations-forward-to-bridge-and-return-tasks-test
  (let [calls           (atom [])
        device-snapshot {::schemas/verification-id    "device:@alice:example.org:txn1"
                         ::schemas/verification-kind  :device
                         ::schemas/their-user-id      "@alice:example.org"
                         ::schemas/their-device-id    "ALICEDEVICE"
                         ::schemas/transaction-id     "txn1"
                         ::schemas/timestamp          1
                         ::schemas/verification-state {::schemas/kind :own-request}}
        user-snapshot   {::schemas/verification-id    "user:!room:example.org:$event"
                         ::schemas/verification-kind  :user
                         ::schemas/their-user-id      "@bob:example.org"
                         ::schemas/room-id            "!room:example.org"
                         ::schemas/request-event-id   "$event"
                         ::schemas/timestamp          2
                         ::schemas/verification-state {::schemas/kind :their-request}}
        ready-snapshot  (assoc-in user-snapshot [::schemas/verification-state ::schemas/kind] :ready)
        start-snapshot  (assoc-in ready-snapshot [::schemas/verification-state ::schemas/kind] :start)
        sas-snapshot    (assoc-in start-snapshot
                                  [::schemas/verification-state ::schemas/sas-state]
                                  {::schemas/kind :their-sas-start})
        match-snapshot  (assoc-in start-snapshot
                                  [::schemas/verification-state ::schemas/sas-state]
                                  {::schemas/kind :comparison-by-user})
        cancel-snapshot (assoc-in user-snapshot [::schemas/verification-state ::schemas/kind] :cancel)]
    (with-redefs [bridge/start-device-verification
                  (fn [client user-id device-id on-success _]
                    (swap! calls conj [:start-device client user-id device-id])
                    (on-success device-snapshot)
                    (->StubCloseable (atom 0)))
                  bridge/start-user-verification
                  (fn [client user-id on-success _]
                    (swap! calls conj [:start-user client user-id])
                    (on-success user-snapshot)
                    (->StubCloseable (atom 0)))
                  bridge/get-active-user-verification
                  (fn [client room-id event-id on-success _]
                    (swap! calls conj [:get-active-user client room-id event-id])
                    (on-success user-snapshot)
                    (->StubCloseable (atom 0)))
                  bridge/ready-verification
                  (fn [client verification-id on-success _]
                    (swap! calls conj [:ready client verification-id])
                    (on-success ready-snapshot)
                    (->StubCloseable (atom 0)))
                  bridge/start-sas-verification
                  (fn [client verification-id on-success _]
                    (swap! calls conj [:start-sas client verification-id])
                    (on-success start-snapshot)
                    (->StubCloseable (atom 0)))
                  bridge/accept-sas-verification
                  (fn [client verification-id on-success _]
                    (swap! calls conj [:accept-sas client verification-id])
                    (on-success sas-snapshot)
                    (->StubCloseable (atom 0)))
                  bridge/accept-verification
                  (fn [client verification-id on-success _]
                    (swap! calls conj [:accept client verification-id])
                    (on-success sas-snapshot)
                    (->StubCloseable (atom 0)))
                  bridge/confirm-verification
                  (fn [client verification-id on-success _]
                    (swap! calls conj [:confirm client verification-id])
                    (on-success match-snapshot)
                    (->StubCloseable (atom 0)))
                  bridge/no-match-verification
                  (fn [client verification-id on-success _]
                    (swap! calls conj [:no-match client verification-id])
                    (on-success cancel-snapshot)
                    (->StubCloseable (atom 0)))
                  bridge/cancel-verification
                  (fn [client verification-id reason on-success _]
                    (swap! calls conj [:cancel client verification-id reason])
                    (on-success cancel-snapshot)
                    (->StubCloseable (atom 0)))]
      (is (= device-snapshot
             (realize-task
              (sut/start-device-verification!
               :client "@alice:example.org" "ALICEDEVICE"))))
      (is (= user-snapshot
             (realize-task
              (sut/start-user-verification! :client "@bob:example.org"))))
      (is (= user-snapshot
             (realize-task
              (sut/get-active-user-verification!
               :client "!room:example.org" "$event"))))
      (is (= ready-snapshot
             (realize-task (sut/ready! :client "user:!room:example.org:$event"))))
      (is (= start-snapshot
             (realize-task (sut/start-sas! :client "user:!room:example.org:$event"))))
      (is (= sas-snapshot
             (realize-task (sut/accept-sas! :client "user:!room:example.org:$event"))))
      (is (= sas-snapshot
             (realize-task (sut/accept! :client "user:!room:example.org:$event"))))
      (is (= match-snapshot
             (realize-task (sut/confirm! :client "user:!room:example.org:$event"))))
      (is (= cancel-snapshot
             (realize-task (sut/no-match! :client "user:!room:example.org:$event"))))
      (is (= cancel-snapshot
             (realize-task (sut/cancel! :client "user:!room:example.org:$event"))))
      (is (= cancel-snapshot
             (realize-task
              (sut/cancel! :client "user:!room:example.org:$event" "not today"))))
      (is (= [[:start-device :client "@alice:example.org" "ALICEDEVICE"]
              [:start-user :client "@bob:example.org"]
              [:get-active-user :client "!room:example.org" "$event"]
              [:ready :client "user:!room:example.org:$event"]
              [:start-sas :client "user:!room:example.org:$event"]
              [:accept-sas :client "user:!room:example.org:$event"]
              [:accept :client "user:!room:example.org:$event"]
              [:confirm :client "user:!room:example.org:$event"]
              [:no-match :client "user:!room:example.org:$event"]
              [:cancel :client "user:!room:example.org:$event" nil]
              [:cancel :client "user:!room:example.org:$event" "not today"]]
             @calls)))))

(deftest verification-arguments-are-validated-before-bridge-test
  (testing "ids are checked before task creation"
    (is (thrown? clojure.lang.ExceptionInfo
                 (sut/start-device-verification!
                  :client nil "ALICEDEVICE")))
    (is (thrown? clojure.lang.ExceptionInfo
                 (sut/start-device-verification!
                  :client "@alice:example.org" nil)))
    (is (thrown? clojure.lang.ExceptionInfo
                 (sut/start-user-verification! :client nil)))
    (is (thrown? clojure.lang.ExceptionInfo
                 (sut/get-active-user-verification!
                  :client nil "$event")))
    (is (thrown? clojure.lang.ExceptionInfo
                 (sut/get-active-user-verification!
                  :client "!room:example.org" nil))))
  (testing "verification ids must be non-blank strings"
    (is (thrown? clojure.lang.ExceptionInfo
                 (sut/ready! :client "")))
    (is (thrown? clojure.lang.ExceptionInfo
                 (sut/cancel! :client "")))))
