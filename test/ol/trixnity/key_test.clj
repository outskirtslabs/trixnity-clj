(ns ol.trixnity.key-test
  (:require
   [clojure.test :refer [deftest is testing]]
   [missionary.core :as m]
   [ol.trixnity.internal :as internal]
   [ol.trixnity.internal.bridge :as bridge]
   [ol.trixnity.key :as sut]
   [ol.trixnity.schemas :as schemas])
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

(deftest key-surfaces-stay-thin-test
  (let [calls          (atom {})
        trust-level    {::schemas/kind :cross-signed}
        backup-version {::schemas/version "1"}]
    (with-redefs [bridge/current-bootstrap-running (fn [client] (swap! calls assoc :current-bootstrap client) false)
                  bridge/bootstrap-running-flow    (fn [client] (swap! calls assoc :bootstrap-flow client) ::bootstrap-flow)
                  bridge/current-backup-version    (fn [client] (swap! calls assoc :current-backup client) backup-version)
                  bridge/backup-version-flow       (fn [client] (swap! calls assoc :backup-flow client) ::backup-flow)
                  bridge/user-trust-level          (fn [client user-id] (swap! calls assoc :user-trust [client user-id]) ::user-trust-flow)
                  bridge/device-trust-level        (fn [client user-id device-id] (swap! calls assoc :device-trust [client user-id device-id]) ::device-trust-flow)
                  bridge/timeline-trust-level      (fn [client room-id event-id] (swap! calls assoc :timeline-trust [client room-id event-id]) ::timeline-trust-flow)
                  bridge/device-keys-flow          (fn [client user-id] (swap! calls assoc :device-keys [client user-id]) ::device-keys-flow)
                  bridge/cross-signing-keys-flow   (fn [client user-id] (swap! calls assoc :cross-signing [client user-id]) ::cross-signing-flow)
                  m/relieve                        (fn [_ flow] flow)
                  internal/observe-flow
                  (fn [_ kotlin-flow]
                    (m/observe
                     (fn [emit]
                       (future
                         (emit (case kotlin-flow
                                 ::bootstrap-flow false
                                 ::backup-flow nil
                                 ::user-trust-flow trust-level
                                 ::device-trust-flow trust-level
                                 ::timeline-trust-flow trust-level
                                 ::device-keys-flow nil
                                 ::cross-signing-flow nil))
                         (emit (case kotlin-flow
                                 ::bootstrap-flow true
                                 ::backup-flow backup-version
                                 ::user-trust-flow trust-level
                                 ::device-trust-flow trust-level
                                 ::timeline-trust-flow trust-level
                                 ::device-keys-flow [{::schemas/raw :device-key}]
                                 ::cross-signing-flow [{::schemas/raw :cross-signing-key}])))
                       (constantly nil))))]
      (is (false? (sut/current-bootstrap-running :client)))
      (is (= [false true]
             (collect-values (sut/bootstrap-running :client) 2)))
      (is (= backup-version
             (sut/current-backup-version :client)))
      (is (= [nil backup-version]
             (collect-values (sut/backup-version :client) 2)))
      (is (= [trust-level trust-level]
             (collect-values (sut/get-trust-level :client "@alice:example.org") 2)))
      (is (= [trust-level trust-level]
             (collect-values (sut/get-trust-level :client "@alice:example.org" "DEVICE") 2)))
      (is (= [trust-level trust-level]
             (collect-values (sut/get-trust-level :client "!room:example.org" "$event") 2)))
      (is (= [nil [{::schemas/raw :device-key}]]
             (collect-values (sut/get-device-keys :client "@alice:example.org") 2)))
      (is (= [nil [{::schemas/raw :cross-signing-key}]]
             (collect-values (sut/get-cross-signing-keys :client "@alice:example.org") 2))))))

(deftest bootstrap-cross-signing-task-forwards-options-test
  (let [calls    (atom [])
        snapshot {::schemas/kind         "success"
                  ::schemas/recovery-key "RECOVERY"
                  ::schemas/uia          {::schemas/kind "success"}}]
    (with-redefs [bridge/bootstrap-cross-signing
                  (fn [client opts on-success _]
                    (swap! calls conj [client opts])
                    (on-success snapshot)
                    (->StubCloseable (atom 0)))]
      (is (= snapshot
             (realize-task
              (sut/bootstrap-cross-signing!
               :client
               {::schemas/password "secret"
                ::schemas/user-id  "@bot:example.org"}))))
      (is (= [[:client {::schemas/password "secret"
                        ::schemas/user-id  "@bot:example.org"}]]
             @calls)))))

(deftest bootstrap-cross-signing-from-passphrase-task-forwards-options-test
  (let [calls    (atom [])
        snapshot {::schemas/kind         "success"
                  ::schemas/recovery-key "RECOVERY"
                  ::schemas/uia          {::schemas/kind "success"}}]
    (with-redefs [bridge/bootstrap-cross-signing-from-passphrase
                  (fn [client passphrase opts on-success _]
                    (swap! calls conj [client passphrase opts])
                    (on-success snapshot)
                    (->StubCloseable (atom 0)))]
      (is (= snapshot
             (realize-task
              (sut/bootstrap-cross-signing-from-passphrase!
               :client
               "storage-passphrase"
               {::schemas/password "secret"}))))
      (is (= [[:client "storage-passphrase" {::schemas/password "secret"}]]
             @calls)))))

(deftest bootstrap-cross-signing-validates-options-test
  (testing "password must be a string when supplied"
    (is (thrown? clojure.lang.ExceptionInfo
                 (sut/bootstrap-cross-signing!
                  :client
                  {::schemas/password nil}))))
  (testing "uia user id must be a string when supplied"
    (is (thrown? clojure.lang.ExceptionInfo
                 (sut/bootstrap-cross-signing!
                  :client
                  {::schemas/user-id nil}))))
  (testing "passphrase must be a string"
    (is (thrown? clojure.lang.ExceptionInfo
                 (sut/bootstrap-cross-signing-from-passphrase!
                  :client
                  nil)))))

(deftest bootstrap-cross-signing-result-schema-test
  (let [registry (schemas/registry {})]
    (is (= {::schemas/kind         "success"
            ::schemas/recovery-key "RECOVERY"
            ::schemas/uia          {::schemas/kind "success"}}
           (schemas/validate!
            registry
            ::schemas/BootstrapCrossSigningResult
            {::schemas/kind         "success"
             ::schemas/recovery-key "RECOVERY"
             ::schemas/uia          {::schemas/kind "success"}})))
    (is (= {::schemas/kind         "uia-required"
            ::schemas/recovery-key "RECOVERY"
            ::schemas/uia          {::schemas/kind      "step"
                                    ::schemas/completed []
                                    ::schemas/flows     [["m.login.password"]]
                                    ::schemas/session   "session1"}}
           (schemas/validate!
            registry
            ::schemas/BootstrapCrossSigningResult
            {::schemas/kind         "uia-required"
             ::schemas/recovery-key "RECOVERY"
             ::schemas/uia          {::schemas/kind      "step"
                                     ::schemas/completed []
                                     ::schemas/flows     [["m.login.password"]]
                                     ::schemas/session   "session1"}})))
    (is (thrown? clojure.lang.ExceptionInfo
                 (schemas/validate!
                  registry
                  ::schemas/BootstrapCrossSigningResult
                  {::schemas/kind         "done"
                   ::schemas/recovery-key "RECOVERY"
                   ::schemas/uia          {::schemas/kind "success"}})))))

(deftest backup-version-schema-rejects-invalid-auth-payload-type-test
  (is (try
        (schemas/validate!
         (schemas/registry {})
         ::schemas/BackupVersion
         {::schemas/version   "1"
          ::schemas/algorithm "m.megolm_backup.v1.curve25519-aes-sha2"
          ::schemas/auth      :not-backup-auth})
        false
        (catch clojure.lang.ExceptionInfo _ true))))
