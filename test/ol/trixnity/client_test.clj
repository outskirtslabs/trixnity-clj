(ns ol.trixnity.client-test
  (:require
   [clojure.test :refer [deftest is testing]]
   [missionary.core :as m]
   [ol.trixnity.internal :as internal]
   [ol.trixnity.internal.bridge :as bridge]
   [ol.trixnity.client :as sut]
   [ol.trixnity.schemas :as schemas])
  (:import
   [java.io Closeable]
   [java.time Duration]))

(defn- realize-task [task]
  (m/? task))

(defn- collect-values [flow n]
  (m/? (->> flow
            (m/eduction (take n))
            (m/reduce conj []))))

(deftype StubCloseable [closed-count]
  Closeable
  (close [_]
    (swap! closed-count inc)))

(deftest open-returns-a-task-and-skips-the-bridge-for-existing-clients-test
  (let [calls  (atom [])
        config {::schemas/homeserver-url "https://matrix.example.org"
                ::schemas/username       "bot"
                ::schemas/password       "secret"
                ::schemas/database-path  "./tmp/state/trixnity.sqlite"
                ::schemas/media-path     "./tmp/media"}]
    (with-redefs [bridge/open-client
                  (fn [request on-success _]
                    (swap! calls conj request)
                    (on-success :opened-client)
                    (->StubCloseable (atom 0)))]
      (is (= :opened-client (realize-task (sut/open config))))
      (is (= :client-handle
             (realize-task (sut/open {::schemas/client :client-handle}))))
      (is (= [config] @calls)))))

(deftest lifecycle-operations-return-tasks-and-forward-timeout-opts-test
  (let [calls   (atom {})
        client  :client-handle
        timeout (Duration/ofSeconds 30)]
    (with-redefs [bridge/start-sync
                  (fn [bridge-client on-success _]
                    (swap! calls assoc :start-sync [bridge-client])
                    (on-success :sync-started)
                    (->StubCloseable (atom 0)))

                  bridge/await-running
                  (fn [bridge-client bridge-timeout on-success _]
                    (swap! calls assoc
                           :await-running
                           [bridge-client bridge-timeout])
                    (on-success :running)
                    (->StubCloseable (atom 0)))

                  bridge/stop-sync
                  (fn [bridge-client on-success _]
                    (swap! calls assoc :stop-sync [bridge-client])
                    (on-success :sync-stopped)
                    (->StubCloseable (atom 0)))

                  bridge/close-client
                  (fn [bridge-client on-success _]
                    (swap! calls assoc :close-client [bridge-client])
                    (on-success :closed)
                    (->StubCloseable (atom 0)))]
      (is (= :sync-started (realize-task (sut/start-sync client))))
      (is (= :running
             (realize-task (sut/await-running client
                                              {::schemas/timeout timeout}))))
      (is (= :sync-stopped (realize-task (sut/stop-sync client))))
      (is (= :closed (realize-task (sut/close client))))
      (is (= [client] (:start-sync @calls)))
      (is (= [client timeout] (:await-running @calls)))
      (is (= [client] (:stop-sync @calls)))
      (is (= [client] (:close-client @calls))))))

(deftest current-accessors-and-state-flows-stay-thin-test
  (let [calls (atom {})]
    (with-redefs [bridge/current-user-id
                  (fn [client]
                    (swap! calls assoc :current-user-id client)
                    "@bot:example.org")

                  bridge/current-sync-state
                  (fn [client]
                    (swap! calls assoc :current-sync-state client)
                    "RUNNING")

                  bridge/current-started
                  (fn [client]
                    (swap! calls assoc :current-started client)
                    true)

                  bridge/sync-state-flow
                  (fn [client]
                    (swap! calls assoc :sync-state-flow client)
                    ::sync-state-flow)

                  bridge/started-flow
                  (fn [client]
                    (swap! calls assoc :started-flow client)
                    ::started-flow)

                  m/relieve
                  (fn [_ flow]
                    flow)

                  internal/observe-flow
                  (fn [_ kotlin-flow]
                    (case kotlin-flow
                      ::sync-state-flow
                      (m/observe
                       (fn [emit]
                         (future
                           (emit "PREPARED")
                           (emit "RUNNING"))
                         (constantly nil)))

                      ::started-flow
                      (m/observe
                       (fn [emit]
                         (future
                           (emit false)
                           (emit true))
                         (constantly nil)))))]
      (testing "synchronous accessors normalize bridge results"
        (is (= "@bot:example.org"
               (sut/current-user-id :client-handle)))
        (is (= :running
               (sut/current-sync-state :client-handle)))
        (is (true? (sut/current-started :client-handle))))

      (testing "state flows are Missionary flows over the bridge seam"
        (is (= [:prepared :running]
               (collect-values (sut/sync-state :client-handle) 2)))
        (is (= [false true]
               (collect-values (sut/started :client-handle) 2))))

      (is (= :client-handle (:current-user-id @calls)))
      (is (= :client-handle (:current-sync-state @calls)))
      (is (= :client-handle (:current-started @calls)))
      (is (= :client-handle (:sync-state-flow @calls)))
      (is (= :client-handle (:started-flow @calls))))))

(deftest additional-client-stateflow-surfaces-stay-thin-test
  (let [calls             (atom {})
        profile-value     {::schemas/display-name "Bot"
                           ::schemas/avatar-url   "mxc://example.org/avatar"
                           ::schemas/raw          :profile-raw}
        server-data-value {::schemas/raw :server-data-raw}]
    (with-redefs [bridge/current-profile
                  (fn [client]
                    (swap! calls assoc :current-profile client)
                    profile-value)

                  bridge/profile-flow
                  (fn [client]
                    (swap! calls assoc :profile-flow client)
                    ::profile-flow)

                  bridge/current-server-data
                  (fn [client]
                    (swap! calls assoc :current-server-data client)
                    server-data-value)

                  bridge/server-data-flow
                  (fn [client]
                    (swap! calls assoc :server-data-flow client)
                    ::server-data-flow)

                  bridge/current-initial-sync-done
                  (fn [client]
                    (swap! calls assoc :current-initial-sync-done client)
                    false)

                  bridge/initial-sync-done-flow
                  (fn [client]
                    (swap! calls assoc :initial-sync-done-flow client)
                    ::initial-sync-done-flow)

                  bridge/current-login-state
                  (fn [client]
                    (swap! calls assoc :current-login-state client)
                    "LOGGED_OUT_SOFT")

                  bridge/login-state-flow
                  (fn [client]
                    (swap! calls assoc :login-state-flow client)
                    ::login-state-flow)

                  m/relieve
                  (fn [_ flow]
                    flow)

                  internal/observe-flow
                  (fn [_ kotlin-flow]
                    (case kotlin-flow
                      ::profile-flow
                      (m/observe
                       (fn [emit]
                         (future
                           (emit nil)
                           (emit profile-value))
                         (constantly nil)))

                      ::server-data-flow
                      (m/observe
                       (fn [emit]
                         (future
                           (emit nil)
                           (emit server-data-value))
                         (constantly nil)))

                      ::initial-sync-done-flow
                      (m/observe
                       (fn [emit]
                         (future
                           (emit false)
                           (emit true))
                         (constantly nil)))

                      ::login-state-flow
                      (m/observe
                       (fn [emit]
                         (future
                           (emit nil)
                           (emit "LOCKED"))
                         (constantly nil)))))]
      (is (= profile-value
             (sut/current-profile :client-handle)))
      (is (= [nil profile-value]
             (collect-values (sut/profile :client-handle) 2)))
      (is (= server-data-value
             (sut/current-server-data :client-handle)))
      (is (= [nil server-data-value]
             (collect-values (sut/server-data :client-handle) 2)))
      (is (false? (sut/current-initial-sync-done :client-handle)))
      (is (= [false true]
             (collect-values (sut/initial-sync-done :client-handle) 2)))
      (is (= :logged-out-soft
             (sut/current-login-state :client-handle)))
      (is (= [nil :locked]
             (collect-values (sut/login-state :client-handle) 2)))
      (is (= :client-handle (:current-profile @calls)))
      (is (= :client-handle (:profile-flow @calls)))
      (is (= :client-handle (:current-server-data @calls)))
      (is (= :client-handle (:server-data-flow @calls)))
      (is (= :client-handle (:current-initial-sync-done @calls)))
      (is (= :client-handle (:initial-sync-done-flow @calls)))
      (is (= :client-handle (:current-login-state @calls)))
      (is (= :client-handle (:login-state-flow @calls))))))

(deftest server-data-schema-rejects-invalid-opaque-payload-types-test
  (is (try
        (schemas/validate!
         (schemas/registry {})
         ::schemas/ServerData
         {::schemas/versions :not-versions})
        false
        (catch clojure.lang.ExceptionInfo _ true)))
  (is (try
        (schemas/validate!
         (schemas/registry {})
         ::schemas/ServerData
         {::schemas/media-config :not-media-config})
        false
        (catch clojure.lang.ExceptionInfo _ true)))
  (is (try
        (schemas/validate!
         (schemas/registry {})
         ::schemas/ServerData
         {::schemas/capabilities :not-capabilities})
        false
        (catch clojure.lang.ExceptionInfo _ true)))
  (is (try
        (schemas/validate!
         (schemas/registry {})
         ::schemas/ServerData
         {::schemas/auth :not-server-auth})
        false
        (catch clojure.lang.ExceptionInfo _ true))))
