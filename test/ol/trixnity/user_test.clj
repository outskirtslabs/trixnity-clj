(ns ol.trixnity.user-test
  (:require
   [clojure.test :refer [deftest is]]
   [missionary.core :as m]
   [ol.trixnity.internal :as internal]
  [ol.trixnity.internal.bridge :as bridge]
  [ol.trixnity.schemas :as schemas]
  [ol.trixnity.user :as sut])
  (:import
   [de.connect2x.trixnity.core.model.events EmptyEventContent]
   [java.io Closeable]))

(defn- realize-task [task]
  (m/? task))

(defn- collect-values [flow n]
  (m/? (->> flow
            (m/eduction (take n))
            (m/reduce conj []))))

(defn- resolve-var [ns-sym var-sym]
  (ns-resolve ns-sym var-sym))

(deftest load-members-task-surface-returns-a-missionary-task-test
  (let [load-members-var        (resolve-var 'ol.trixnity.user 'load-members)
        bridge-load-members-var (resolve-var 'ol.trixnity.internal.bridge
                                             'load-members)
        calls                   (atom [])]
    (is (some? load-members-var)
        "ol.trixnity.user/load-members is missing")
    (is (some? bridge-load-members-var)
        "ol.trixnity.internal.bridge/load-members is missing")
    (when (and load-members-var bridge-load-members-var)
      (with-redefs-fn
        {bridge-load-members-var
         (fn [client room-id wait on-success _]
           (swap! calls conj [client room-id wait])
           (on-success nil)
           (reify Closeable
             (close [_] nil)))}
        #(do
           (is (nil?
                (realize-task
                 ((var-get load-members-var)
                  :client-handle
                  "!room:example.org"))))
           (is (nil?
                (realize-task
                 ((var-get load-members-var)
                  :client-handle
                  "!room:example.org"
                  {::schemas/wait false}))))))
      (is (= [[:client-handle "!room:example.org" true]
              [:client-handle "!room:example.org" false]]
             @calls)))))

(deftest load-members-validates-wait-option-before-bridge-test
  (let [load-members-var        (resolve-var 'ol.trixnity.user 'load-members)
        bridge-load-members-var (resolve-var 'ol.trixnity.internal.bridge
                                             'load-members)
        called?                 (atom false)]
    (is (some? load-members-var)
        "ol.trixnity.user/load-members is missing")
    (is (some? bridge-load-members-var)
        "ol.trixnity.internal.bridge/load-members is missing")
    (when (and load-members-var bridge-load-members-var)
      (with-redefs-fn
        {bridge-load-members-var
         (fn [& _]
           (reset! called? true)
           (throw (ex-info "bridge should not be called" {})))}
        #(is (try
               ((var-get load-members-var)
                :client-handle
                "!room:example.org"
                {::schemas/wait :later})
               false
               (catch clojure.lang.ExceptionInfo _ true))))
      (is (false? @called?)))))

(deftest user-surfaces-stay-thin-test
  (let [calls (atom {})
        user  {::schemas/user-id "@alice:example.org"}]
    (with-redefs [bridge/user-all                   (fn [client room-id] (swap! calls assoc :all [client room-id]) ::all-flow)
                  bridge/user-by-id                 (fn [client room-id user-id] (swap! calls assoc :by-id [client room-id user-id]) ::by-id-flow)
                  bridge/user-all-receipts          (fn [client room-id] (swap! calls assoc :all-receipts [client room-id]) ::all-receipts-flow)
                  bridge/user-receipts-by-id        (fn [client room-id user-id] (swap! calls assoc :receipts-by-id [client room-id user-id]) ::receipts-by-id-flow)
                  bridge/user-power-level           (fn [client room-id user-id] (swap! calls assoc :power-level [client room-id user-id]) ::power-level-flow)
                  bridge/can-kick-user              (fn [& args] (swap! calls assoc :kick args) ::kick-flow)
                  bridge/can-ban-user               (fn [& args] (swap! calls assoc :ban args) ::ban-flow)
                  bridge/can-unban-user             (fn [& args] (swap! calls assoc :unban args) ::unban-flow)
                  bridge/can-invite-user            (fn [& args] (swap! calls assoc :invite-user args) ::invite-user-flow)
                  bridge/can-invite                 (fn [& args] (swap! calls assoc :invite args) ::invite-flow)
                  bridge/can-redact-event           (fn [& args] (swap! calls assoc :redact args) ::redact-flow)
                  bridge/can-set-power-level-to-max (fn [& args] (swap! calls assoc :max-power args) ::max-power-flow)
                  bridge/can-send-event-by-class    (fn [& args] (swap! calls assoc :send-class args) ::send-class-flow)
                  bridge/can-send-event-by-content  (fn [& args] (swap! calls assoc :send-content args) ::send-content-flow)
                  bridge/user-presence              (fn [& args] (swap! calls assoc :presence args) ::presence-flow)
                  bridge/user-account-data          (fn [& args] (swap! calls assoc :account-data args) ::account-data-flow)
                  internal/observe-flow             (fn [_ kotlin-flow]
                                                      (m/observe
                                                       (fn [emit]
                                                         (future
                                                           (emit (case kotlin-flow
                                                                   ::by-id-flow nil
                                                                   ::presence-flow nil
                                                                   ::account-data-flow nil
                                                                   ::power-level-flow {::schemas/kind :user}
                                                                   ::kick-flow false
                                                                   ::ban-flow false
                                                                   ::unban-flow false
                                                                   ::invite-user-flow false
                                                                   ::invite-flow false
                                                                   ::redact-flow false
                                                                   ::max-power-flow nil
                                                                   ::send-class-flow true
                                                                   ::send-content-flow true))
                                                           (emit (case kotlin-flow
                                                                   ::by-id-flow user
                                                                   ::presence-flow {::schemas/presence :online}
                                                                   ::account-data-flow {::schemas/raw :content}
                                                                   ::power-level-flow {::schemas/kind :creator}
                                                                   ::kick-flow true
                                                                   ::ban-flow true
                                                                   ::unban-flow true
                                                                   ::invite-user-flow true
                                                                   ::invite-flow true
                                                                   ::redact-flow true
                                                                   ::max-power-flow {::schemas/kind :user}
                                                                   ::send-class-flow false
                                                                   ::send-content-flow false)))
                                                         (constantly nil))))
                  internal/observe-keyed-flow-map   (fn [_ kotlin-flow]
                                                      (m/observe
                                                       (fn [emit]
                                                         (future
                                                           (emit {})
                                                           (emit (case kotlin-flow
                                                                   ::all-flow {"@alice:example.org" :user-flow}
                                                                   ::all-receipts-flow {"@alice:example.org" :receipt-flow})))
                                                         (constantly nil))))]
      (is (= [{} {"@alice:example.org" :user-flow}]
             (collect-values (sut/get-all :client "!room") 2)))
      (is (= [nil user]
             (collect-values (sut/get-by-id :client "!room" "@alice:example.org") 2)))
      (is (= [{} {"@alice:example.org" :receipt-flow}]
             (collect-values (sut/get-all-receipts :client "!room") 2)))
      (is (= [{::schemas/kind :user} {::schemas/kind :creator}]
             (collect-values (sut/get-power-level :client "!room" "@alice:example.org") 2)))
      (is (= [true false]
             (collect-values (sut/can-send-event :client "!room" EmptyEventContent) 2)))
      (is (= [true false]
             (collect-values (sut/can-send-event :client "!room" EmptyEventContent/INSTANCE) 2)))
      (is (= [nil {::schemas/presence :online}]
             (collect-values (sut/get-presence :client "@alice:example.org") 2)))
      (is (= [nil {::schemas/raw :content}]
             (collect-values (sut/get-account-data :client EmptyEventContent) 2))))))

(deftest user-event-content-class-surfaces-reject-non-trixnity-classes-test
  (is (try
        (sut/can-send-event :client "!room" String)
        false
        (catch clojure.lang.ExceptionInfo _ true)))
  (is (try
        (sut/get-account-data :client String)
        false
        (catch clojure.lang.ExceptionInfo _ true))))

(deftest user-event-content-surface-rejects-non-room-event-content-values-test
  (let [called? (atom false)]
    (with-redefs [bridge/can-send-event-by-content
                  (fn [& _]
                    (reset! called? true)
                    ::send-content-flow)]
      (is (try
            (sut/can-send-event :client "!room" :content)
            false
            (catch clojure.lang.ExceptionInfo _ true)))
      (is (false? @called?)))))
