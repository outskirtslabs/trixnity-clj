(ns ol.trixnity.room-test
  (:require
   [clojure.test :refer [deftest is testing]]
   [ol.trixnity.interop :as interop]
   [ol.trixnity.room :as sut]
   [ol.trixnity.room.message :as msg]
   [ol.trixnity.schemas :as schemas])
  (:import
   [java.time Duration]
   [java.util.concurrent CompletableFuture]))

(defn- completed-future [value]
  (CompletableFuture/completedFuture value))

(defn- request-payload [request]
  (into {} request))

(deftest create-and-invite-return-futures-test
  (let [calls         (atom {})
        create-future (completed-future "!room:example.org")
        invite-future (completed-future nil)
        timeout       (Duration/ofSeconds 5)]
    (with-redefs [interop/create-room
                  (fn [request]
                    (swap! calls assoc :create-room (request-payload request))
                    create-future)

                  interop/invite-user
                  (fn [request]
                    (swap! calls assoc :invite-user (request-payload request))
                    invite-future)]
      (is (identical? create-future
                      (sut/create! :client-handle
                                   {::schemas/room-name "Ops Bot"})))
      (is (identical? invite-future
                      (sut/invite! :client-handle
                                   "!room:example.org"
                                   "@alice:example.org"
                                   {::schemas/timeout timeout})))

      (is (= {::schemas/client    :client-handle
              ::schemas/room-name "Ops Bot"}
             (:create-room @calls)))
      (is (= {::schemas/client  :client-handle
              ::schemas/room-id "!room:example.org"
              ::schemas/user-id "@alice:example.org"
              ::schemas/timeout timeout}
             (:invite-user @calls))))))

(deftest send-uses-message-spec-maps-and-forwards-timeout-opts-test
  (let [calls       (atom {})
        send-future (completed-future "$txn")
        timeout     (Duration/ofSeconds 5)
        ev          {::schemas/type       "m.room.message"
                     ::schemas/room-id    "!room:example.org"
                     ::schemas/event-id   "$event"
                     ::schemas/sender     "@alice:example.org"
                     ::schemas/relates-to {::schemas/relation-type     "m.thread"
                                           ::schemas/relation-event-id "$root"}}
        message     (-> (msg/text "pong")
                        (msg/reply-to ev))]
    (with-redefs [interop/send-message
                  (fn [request]
                    (swap! calls assoc :send-message (request-payload request))
                    send-future)]
      (is (identical? send-future
                      (sut/send! :client-handle
                                 "!room:example.org"
                                 message
                                 {::schemas/timeout timeout})))
      (is (= {::schemas/client  :client-handle
              ::schemas/room-id "!room:example.org"
              ::schemas/message message
              ::schemas/timeout timeout}
             (:send-message @calls))))))

(deftest react-uses-normalized-events-instead-of-inline-helper-fns-test
  (let [calls        (atom {})
        react-future (completed-future "$reaction")
        ev           {::schemas/event-id "$event"}]
    (with-redefs [interop/send-reaction
                  (fn [request]
                    (swap! calls assoc :send-reaction (request-payload request))
                    react-future)]
      (is (identical? react-future
                      (sut/react! :client-handle
                                  "!room:example.org"
                                  ev
                                  "🔥")))
      (is (= {::schemas/client   :client-handle
              ::schemas/room-id  "!room:example.org"
              ::schemas/event-id "$event"
              ::schemas/key      "🔥"}
             (:send-reaction @calls))))))

(deftest send-rejects-unqualified-message-maps-before-interop-test
  (with-redefs [interop/send-message
                (fn [_]
                  (throw (ex-info "interop should not be reached" {})))]
    (testing "message builders are namespaced keyword maps"
      (is (thrown? clojure.lang.ExceptionInfo
                   (sut/send! :client-handle
                              "!room:example.org"
                              {:kind :text
                               :body "pong"}))))))
