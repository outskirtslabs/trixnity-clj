(ns ol.trixnity.room-test
  (:require
   [clojure.test :refer [deftest is]]
   [missionary.core :as m]
   [ol.trixnity.internal :as internal]
   [ol.trixnity.internal.bridge :as bridge]
   [ol.trixnity.room :as sut]
   [ol.trixnity.room.message :as msg]
   [ol.trixnity.schemas :as schemas])
  (:import
   [de.connect2x.trixnity.clientserverapi.model.sync Sync$Response]
   [de.connect2x.trixnity.core.model.events EmptyEventContent]
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

(deftest task-surfaces-return-missionary-tasks-test
  (let [calls   (atom {})
        timeout (Duration/ofSeconds 5)
        ev      {::schemas/room-id  "!room:example.org"
                 ::schemas/event-id "$event"}
        message (-> (msg/text "pong")
                    (msg/reply-to {::schemas/event-id "$parent"}))]
    (with-redefs [bridge/create-room
                  (fn [client room-name on-success _]
                    (swap! calls assoc :create-room [client room-name])
                    (on-success "!room:example.org")
                    (->StubCloseable (atom 0)))

                  bridge/invite-user
                  (fn [client room-id user-id bridge-timeout on-success _]
                    (swap! calls assoc :invite-user [client room-id user-id bridge-timeout])
                    (on-success :invited)
                    (->StubCloseable (atom 0)))

                  bridge/send-message
                  (fn [client room-id sent-message bridge-timeout on-success _]
                    (swap! calls assoc :send-message [client room-id sent-message bridge-timeout])
                    (on-success "$txn")
                    (->StubCloseable (atom 0)))

                  bridge/send-reaction
                  (fn [client room-id event-id key bridge-timeout on-success _]
                    (swap! calls assoc :send-reaction [client room-id event-id key bridge-timeout])
                    (on-success "$reaction")
                    (->StubCloseable (atom 0)))]
      (is (= "!room:example.org"
             (realize-task
              (sut/create-room :client-handle {::schemas/room-name "Ops Bot"}))))
      (is (= :invited
             (realize-task
              (sut/invite-user :client-handle "!room:example.org" "@alice:example.org"
                               {::schemas/timeout timeout}))))
      (is (= "$txn"
             (realize-task
              (sut/send-message :client-handle "!room:example.org" message
                                {::schemas/timeout timeout}))))
      (is (= "$reaction"
             (realize-task
              (sut/send-reaction :client-handle "!room:example.org" ev "🔥"))))
      (is (= [:client-handle "Ops Bot"] (:create-room @calls)))
      (is (= [:client-handle "!room:example.org" "@alice:example.org" timeout]
             (:invite-user @calls)))
      (is (= [:client-handle "!room:example.org" message timeout]
             (:send-message @calls)))
      (is (= [:client-handle "!room:example.org" "$event" "🔥" nil]
             (:send-reaction @calls))))))

(deftest room-and-state-surfaces-stay-thin-test
  (let [calls        (atom {})
        room-value   {::schemas/room-id "!room:example.org"}
        typing-value {"!room:example.org" {::schemas/users #{"@alice:example.org"}}}
        state-flow   (m/observe (fn [emit] (future (emit nil) (emit room-value)) (constantly nil)))]
    (with-redefs [bridge/room-by-id
                  (fn [client room-id]
                    (swap! calls assoc :room-by-id [client room-id])
                    ::room-by-id-flow)

                  bridge/rooms
                  (fn [client]
                    (swap! calls assoc :rooms [client])
                    ::rooms-flow)

                  bridge/rooms-flat
                  (fn [client]
                    (swap! calls assoc :rooms-flat [client])
                    ::rooms-flat-flow)

                  bridge/current-users-typing
                  (fn [client]
                    (swap! calls assoc :current-users-typing [client])
                    typing-value)

                  bridge/users-typing-flow
                  (fn [client]
                    (swap! calls assoc :users-typing-flow [client])
                    ::users-typing-flow)

                  bridge/account-data
                  (fn [client room-id event-content-class key]
                    (swap! calls assoc :account-data [client room-id event-content-class key])
                    ::account-data-flow)

                  bridge/state
                  (fn [client room-id event-content-class state-key]
                    (swap! calls assoc :state [client room-id event-content-class state-key])
                    ::state-flow)

                  bridge/all-state
                  (fn [client room-id event-content-class]
                    (swap! calls assoc :all-state [client room-id event-content-class])
                    ::all-state-flow)

                  m/relieve
                  (fn [_ flow] flow)

                  internal/observe-flow
                  (fn [_ kotlin-flow]
                    (case kotlin-flow
                      ::room-by-id-flow state-flow
                      ::rooms-flat-flow (m/observe (fn [emit] (future (emit []) (emit [room-value])) (constantly nil)))
                      ::users-typing-flow (m/observe (fn [emit] (future (emit {}) (emit typing-value)) (constantly nil)))
                      ::account-data-flow (m/observe (fn [emit] (future (emit nil) (emit {::schemas/raw :content})) (constantly nil)))
                      ::state-flow (m/observe (fn [emit] (future (emit nil) (emit {::schemas/state-key ""})) (constantly nil)))))

                  internal/observe-keyed-flow-map
                  (fn [_ kotlin-flow]
                    (case kotlin-flow
                      ::rooms-flow (m/observe (fn [emit] (future (emit {}) (emit {"!room:example.org" :room-flow})) (constantly nil)))
                      ::all-state-flow (m/observe (fn [emit] (future (emit {}) (emit {"" :state-entry-flow})) (constantly nil)))))]
      (is (= [nil room-value]
             (collect-values (sut/get-by-id :client-handle "!room:example.org") 2)))
      (is (= [{} {"!room:example.org" :room-flow}]
             (collect-values (sut/get-all :client-handle) 2)))
      (is (= [[] [room-value]]
             (collect-values (sut/get-all-flat :client-handle) 2)))
      (is (= typing-value
             (sut/current-users-typing :client-handle)))
      (is (= [{} typing-value]
             (collect-values (sut/users-typing :client-handle) 2)))
      (is (= [nil {::schemas/raw :content}]
             (collect-values (sut/get-account-data :client-handle "!room:example.org" EmptyEventContent) 2)))
      (is (= [nil {::schemas/state-key ""}]
             (collect-values (sut/get-state :client-handle "!room:example.org" EmptyEventContent) 2)))
      (is (= [{} {"" :state-entry-flow}]
             (collect-values (sut/get-all-state :client-handle "!room:example.org" EmptyEventContent) 2)))
      (is (= [:client-handle "!room:example.org"] (:room-by-id @calls)))
      (is (= [:client-handle "!room:example.org" EmptyEventContent ""]
             (:account-data @calls)))
      (is (= [:client-handle "!room:example.org" EmptyEventContent ""]
             (:state @calls)))
      (is (= [:client-handle "!room:example.org" EmptyEventContent]
             (:all-state @calls))))))

(deftest room-event-content-class-surfaces-reject-non-trixnity-classes-test
  (is (try
        (sut/get-account-data :client-handle "!room:example.org" String)
        false
        (catch clojure.lang.ExceptionInfo _ true)))
  (is (try
        (sut/get-state :client-handle "!room:example.org" String)
        false
        (catch clojure.lang.ExceptionInfo _ true)))
  (is (try
        (sut/get-all-state :client-handle "!room:example.org" String)
        false
        (catch clojure.lang.ExceptionInfo _ true))))

(deftest outbox-surfaces-cover-all-public-arities-test
  (let [calls        (atom {})
        flat-message {::schemas/transaction-id "txn"}]
    (with-redefs [bridge/outbox
                  (fn [client]
                    (swap! calls assoc :outbox [client])
                    ::outbox-flow)

                  bridge/outbox-flat
                  (fn [client]
                    (swap! calls assoc :outbox-flat [client])
                    ::outbox-flat-flow)

                  bridge/outbox-by-room
                  (fn [client room-id]
                    (swap! calls assoc :outbox-by-room [client room-id])
                    ::outbox-by-room-flow)

                  bridge/outbox-by-room-flat
                  (fn [client room-id]
                    (swap! calls assoc :outbox-by-room-flat [client room-id])
                    ::outbox-by-room-flat-flow)

                  bridge/outbox-message
                  (fn [client room-id transaction-id]
                    (swap! calls assoc :outbox-message [client room-id transaction-id])
                    ::outbox-message-flow)

                  internal/observe-flow
                  (fn [_ kotlin-flow]
                    (case kotlin-flow
                      ::outbox-flat-flow (m/observe (fn [emit] (future (emit []) (emit [flat-message])) (constantly nil)))
                      ::outbox-by-room-flat-flow (m/observe (fn [emit] (future (emit []) (emit [flat-message])) (constantly nil)))
                      ::outbox-message-flow (m/observe (fn [emit] (future (emit nil) (emit flat-message)) (constantly nil)))))

                  internal/observe-flow-list
                  (fn [_ kotlin-flow]
                    (case kotlin-flow
                      ::outbox-flow (m/observe (fn [emit] (future (emit []) (emit [:message-flow])) (constantly nil)))
                      ::outbox-by-room-flow (m/observe (fn [emit] (future (emit []) (emit [:room-message-flow])) (constantly nil)))))]
      (is (= [[] [:message-flow]]
             (collect-values (sut/get-outbox :client-handle) 2)))
      (is (= [[] [flat-message]]
             (collect-values (sut/get-outbox-flat :client-handle) 2)))
      (is (= [[] [:room-message-flow]]
             (collect-values (sut/get-outbox :client-handle "!room:example.org") 2)))
      (is (= [[] [flat-message]]
             (collect-values (sut/get-outbox-flat :client-handle "!room:example.org") 2)))
      (is (= [nil flat-message]
             (collect-values (sut/get-outbox :client-handle "!room:example.org" "txn") 2)))
      (is (= [:client-handle "!room:example.org"] (:outbox-by-room @calls)))
      (is (= [:client-handle "!room:example.org"] (:outbox-by-room-flat @calls)))
      (is (= [:client-handle "!room:example.org" "txn"] (:outbox-message @calls))))))

(deftest timeline-surfaces-cover-selective-nested-and-helper-forms-test
  (let [calls          (atom {})
        timeline-event {::schemas/room-id  "!room:example.org"
                        ::schemas/event-id "$event"
                        ::schemas/raw      :timeline-raw}
        response       (Sync$Response. "next" nil nil nil nil nil nil nil)]
    (with-redefs [bridge/timeline-event
                  (fn [client room-id event-id d f size allow]
                    (swap! calls assoc :timeline-event [client room-id event-id d f size allow])
                    ::timeline-event-flow)

                  bridge/previous-timeline-event
                  (fn [client raw d f size allow]
                    (swap! calls assoc :previous [client raw d f size allow])
                    ::previous-flow)

                  bridge/next-timeline-event
                  (fn [client raw d f size allow]
                    (swap! calls assoc :next [client raw d f size allow])
                    ::next-flow)

                  bridge/last-timeline-event
                  (fn [client room-id d f size allow]
                    (swap! calls assoc :last [client room-id d f size allow])
                    ::last-flow)

                  bridge/response-timeline-events
                  (fn [client response d]
                    (swap! calls assoc :response [client response d])
                    ::response-flow)

                  bridge/timeline-event-chain
                  (fn [client room-id start direction d f size allow min-size max-size]
                    (swap! calls assoc :chain [client room-id start direction d f size allow min-size max-size])
                    ::chain-flow)

                  bridge/last-timeline-events
                  (fn [client room-id d f size allow min-size max-size]
                    (swap! calls assoc :last-chain [client room-id d f size allow min-size max-size])
                    ::last-chain-flow)

                  bridge/timeline-events-list
                  (fn [& args]
                    (swap! calls assoc :list args)
                    ::list-flow)

                  bridge/last-timeline-events-list
                  (fn [& args]
                    (swap! calls assoc :last-list args)
                    ::last-list-flow)

                  bridge/timeline-events-around
                  (fn [& args]
                    (swap! calls assoc :around args)
                    ::around-flow)

                  bridge/timeline-events-from-now-on
                  (fn [client d buffer-size]
                    (swap! calls assoc :from-now-on [client d buffer-size])
                    ::from-now-on-flow)

                  bridge/timeline-event-relations
                  (fn [client room-id event-id relation-type]
                    (swap! calls assoc :relations [client room-id event-id relation-type])
                    ::relations-flow)

                  internal/observe-flow
                  (fn [_ kotlin-flow]
                    (case kotlin-flow
                      ::timeline-event-flow (m/observe (fn [emit] (future (emit nil) (emit timeline-event)) (constantly nil)))
                      ::previous-flow (m/observe (fn [emit] (future (emit nil) (emit timeline-event)) (constantly nil)))
                      ::next-flow (m/observe (fn [emit] (future (emit nil) (emit timeline-event)) (constantly nil)))
                      ::last-flow (m/observe (fn [emit] (future (emit :inner-event-flow)) (constantly nil)))
                      ::response-flow (m/observe (fn [emit] (future (emit timeline-event)) (constantly nil)))
                      ::chain-flow (m/observe (fn [emit] (future (emit :event-flow)) (constantly nil)))
                      ::last-chain-flow (m/observe (fn [emit] (future (emit :chain-flow)) (constantly nil)))
                      ::from-now-on-flow (m/observe (fn [emit] (future (emit timeline-event)) (constantly nil)))
                      ::list-flow (m/observe (fn [emit] (future (emit [:list-event-flow])) (constantly nil)))
                      ::last-list-flow (m/observe (fn [emit] (future (emit [:last-list-event-flow])) (constantly nil)))
                      ::around-flow (m/observe (fn [emit] (future (emit [:around-event-flow])) (constantly nil)))
                      :chain-flow (m/observe (fn [emit] (future (emit :event-flow)) (constantly nil)))
                      :event-flow (m/observe (fn [emit] (future (emit timeline-event)) (constantly nil)))
                      :inner-event-flow (m/observe (fn [emit] (future (emit timeline-event)) (constantly nil)))))

                  internal/observe-flow-list
                  (fn [_ kotlin-flow]
                    (case kotlin-flow
                      ::list-flow (m/observe (fn [emit] (future (emit [:list-event-flow])) (constantly nil)))
                      ::last-list-flow (m/observe (fn [emit] (future (emit [:last-list-event-flow])) (constantly nil)))
                      ::around-flow (m/observe (fn [emit] (future (emit [:around-event-flow])) (constantly nil)))))

                  internal/observe-keyed-flow-map
                  (fn [_ kotlin-flow]
                    (is (= ::relations-flow kotlin-flow))
                    (m/observe (fn [emit] (future (emit nil) (emit {"$related" :relation-flow})) (constantly nil))))]
      (is (= [nil timeline-event]
             (collect-values (sut/get-timeline-event :client-handle "!room:example.org" "$event") 2)))
      (is (= [nil timeline-event]
             (collect-values (sut/get-previous-timeline-event :client-handle timeline-event) 2)))
      (is (= [nil timeline-event]
             (collect-values (sut/get-next-timeline-event :client-handle timeline-event) 2)))
      (is (= [timeline-event]
             (collect-values (first (collect-values (sut/get-last-timeline-event :client-handle "!room:example.org") 1)) 1)))
      (is (= [timeline-event]
             (collect-values (sut/get-timeline-events :client-handle response) 1)))
      (is (= [timeline-event]
             (collect-values (first (collect-values (sut/get-timeline-events :client-handle "!room:example.org" "$event" :backwards) 1)) 1)))
      (let [chain-flow (first (collect-values (sut/get-last-timeline-events :client-handle "!room:example.org") 1))
            event-flow (first (collect-values chain-flow 1))]
        (is (= [timeline-event]
               (collect-values event-flow 1))))
      (is (= [[:list-event-flow]]
             (collect-values (sut/get-timeline-events-list :client-handle "!room:example.org" "$event" :forwards 10 1) 1)))
      (is (= [[:last-list-event-flow]]
             (collect-values (sut/get-last-timeline-events-list :client-handle "!room:example.org" 10 1) 1)))
      (is (= [[:around-event-flow]]
             (collect-values (sut/get-timeline-events-around :client-handle "!room:example.org" "$event" 10 10) 1)))
      (is (= [timeline-event]
             (collect-values (sut/get-timeline-events-from-now-on :client-handle) 1)))
      (is (= [nil {"$related" :relation-flow}]
             (collect-values (sut/get-timeline-event-relations :client-handle "!room:example.org" "$event" "m.annotation") 2)))
      (is (= [:client-handle response nil] (:response @calls)))
      (is (= [:client-handle nil nil] (:from-now-on @calls))))))

(deftest previous-and-next-timeline-event-require-bridgeable-raw-events-test
  (let [previous-called? (atom false)
        next-called?     (atom false)
        timeline-event   {::schemas/room-id  "!room:example.org"
                          ::schemas/event-id "$event"}]
    (with-redefs [bridge/previous-timeline-event
                  (fn [& _]
                    (reset! previous-called? true)
                    ::previous-flow)

                  bridge/next-timeline-event
                  (fn [& _]
                    (reset! next-called? true)
                    ::next-flow)]
      (is (try
            (sut/get-previous-timeline-event :client-handle timeline-event)
            false
            (catch clojure.lang.ExceptionInfo _ true)))
      (is (try
            (sut/get-next-timeline-event :client-handle timeline-event)
            false
            (catch clojure.lang.ExceptionInfo _ true)))
      (is (false? @previous-called?))
      (is (false? @next-called?)))))
