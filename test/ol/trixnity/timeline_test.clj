(ns ol.trixnity.timeline-test
  (:require
   [clojure.test :refer [deftest is testing]]
   [ol.trixnity.interop :as interop]
   [ol.trixnity.schemas :as schemas]
   [ol.trixnity.timeline :as sut])
  (:import
   [java.io Closeable]
   [java.time Duration]))

(deftype StubSubscription [closed? callback]
  Closeable
  (close [_]
    (reset! closed? true)))

(defn- request-payload [request]
  (into {} request))

(defn- emit! [^StubSubscription subscription event]
  (when-not @(.-closed? subscription)
    (try
      ((.-callback subscription) event)
      (catch Throwable _
        nil))))

(deftest subscribe-returns-a-closeable-handle-and-forwards-options-test
  (let [calls         (atom [])
        subscription* (atom nil)
        timeout       (Duration/ofSeconds 8)
        first-event   {::schemas/type     "m.room.message"
                       ::schemas/room-id  "!room:example.org"
                       ::schemas/event-id "$event-1"
                       ::schemas/body     "hello"}
        second-event  {::schemas/type     "m.room.message"
                       ::schemas/room-id  "!room:example.org"
                       ::schemas/event-id "$event-2"
                       ::schemas/body     "again"}
        third-event   {::schemas/type     "m.room.message"
                       ::schemas/room-id  "!room:example.org"
                       ::schemas/event-id "$event-3"
                       ::schemas/body     "after-close"}
        delivered     (atom [])
        failures      (atom 0)]
    (with-redefs [interop/subscribe-timeline
                  (fn [request]
                    (swap! calls conj (request-payload request))
                    (let [subscription
                          (->StubSubscription
                           (atom false)
                           (::schemas/on-event request))]
                      (reset! subscription* subscription)
                      subscription))]
      (let [subscription
            (sut/subscribe! :client-handle
                            {::schemas/decryption-timeout timeout}
                            (fn [ev]
                              (swap! delivered conj ev)
                              (when (= (::schemas/event-id ev) "$event-1")
                                (swap! failures inc)
                                (throw (ex-info "boom" {})))))]
        (testing "timeline subscriptions are closeable JVM handles"
          (is (instance? Closeable subscription)))

        (emit! @subscription* first-event)
        (emit! @subscription* second-event)
        (sut/close! subscription)
        (emit! @subscription* third-event)

        (is (= [{::schemas/type     "m.room.message"
                 ::schemas/room-id  "!room:example.org"
                 ::schemas/event-id "$event-1"
                 ::schemas/body     "hello"}
                {::schemas/type     "m.room.message"
                 ::schemas/room-id  "!room:example.org"
                 ::schemas/event-id "$event-2"
                 ::schemas/body     "again"}]
               @delivered))
        (is (= 1 @failures))
        (is (= [{::schemas/client             :client-handle
                 ::schemas/decryption-timeout timeout}]
               (mapv #(dissoc % ::schemas/on-event) @calls)))
        (is (fn? (::schemas/on-event (first @calls))))))))
