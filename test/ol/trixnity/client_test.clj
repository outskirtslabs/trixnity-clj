(ns ol.trixnity.client-test
  (:require
   [clojure.test :refer [deftest is testing]]
   [ol.trixnity.schemas :as schemas]
   [ol.trixnity.client :as sut]
   [ol.trixnity.interop :as interop])
  (:import
   (java.util.concurrent BlockingQueue TimeUnit)))

(defn- request-payload [request]
  (into {} request))

(deftest start-and-stop-lifecycle-test
  (let [calls (atom {})
        stops (atom [])]
    (with-redefs [interop/login-with-password-blocking
                  (fn [request]
                    (swap! calls assoc :login (request-payload request))
                    :client-handle)

                  interop/from-store-blocking
                  (fn [request]
                    (swap! calls assoc :from-store (request-payload request))
                    nil)

                  interop/start-sync-blocking
                  (fn [request]
                    (swap! calls assoc :sync (request-payload request))
                    nil)

                  interop/start-timeline-pump
                  (fn [request]
                    (swap! calls assoc :pump (request-payload request))
                    :timeline-pump)

                  interop/stop-timeline-pump
                  (fn [request]
                    (swap! stops conj (request-payload request))
                    nil)]
      (let [runtime (sut/start! {::schemas/homeserver-url "https://matrix.example.org"
                                 ::schemas/username       "bot"})]
        (is (= :client-handle (:client runtime)))
        (is (instance? BlockingQueue (:events runtime)))
        (is (fn? (:stop! runtime)))
        (is (= "https://matrix.example.org"
               (get-in @calls [:login ::schemas/homeserver-url])))
        (is (= "bot"
               (get-in @calls [:login ::schemas/username])))
        (is (nil? (:from-store @calls)))
        (is (= :client-handle
               (get-in @calls [:sync ::schemas/client])))
        (is (fn? (get-in @calls [:pump ::schemas/on-event])))
        ((:stop! runtime))
        (is (= :timeline-pump
               (get-in (first @stops) [::schemas/timeline-pump])))))))

(deftest facade-operation-wrapper-test
  (let [calls   (atom {})
        runtime {:client :client-handle}]
    (with-redefs [interop/create-room-blocking
                  (fn [request]
                    (swap! calls assoc :create-room (request-payload request))
                    "!room:example.org")

                  interop/invite-user-blocking
                  (fn [request]
                    (swap! calls assoc :invite-user (request-payload request))
                    :invited)

                  interop/send-text-reply-blocking
                  (fn [request]
                    (swap! calls assoc :send-text (request-payload request))
                    :sent-text)

                  interop/send-reaction-blocking
                  (fn [request]
                    (swap! calls assoc :send-reaction (request-payload request))
                    :sent-reaction)]
      (is (= "!room:example.org"
             (sut/ensure-room! runtime {:room-name "My Room"})))
      (is (= :invited
             (sut/invite-user! runtime
                               {:room-id "!room:example.org"
                                :user-id "@alice:example.org"})))
      (is (= :sent-text
             (sut/send-text! runtime
                             {:room-id  "!room:example.org"
                              :event-id "$event"
                              :body     "HELLO"})))
      (is (= :sent-reaction
             (sut/send-reaction! runtime
                                 {:room-id  "!room:example.org"
                                  :event-id "$event"
                                  :key      "👍"})))
      (testing "runtime client is injected into each bridge request payload"
        (is (= :client-handle (get-in @calls [:create-room ::schemas/client])))
        (is (= :client-handle (get-in @calls [:invite-user ::schemas/client])))
        (is (= :client-handle (get-in @calls [:send-text ::schemas/client])))
        (is (= :client-handle (get-in @calls [:send-reaction ::schemas/client])))))))

(deftest start-registers-on-event-callback-with-normalized-helper-fns-test
  (let [calls      (atom {})
        callbacks  (atom [])
        text-sends (atom [])
        reacts     (atom [])]
    (with-redefs [interop/login-with-password-blocking
                  (fn [_] :client-handle)

                  interop/from-store-blocking
                  (fn [_] nil)

                  interop/start-sync-blocking
                  (fn [_] nil)

                  interop/start-timeline-pump
                  (fn [request]
                    (swap! calls assoc :pump (request-payload request))
                    :timeline-pump)

                  interop/stop-timeline-pump
                  (fn [_] nil)

                  interop/send-text-reply-blocking
                  (fn [request]
                    (swap! text-sends conj (request-payload request))
                    :ok)

                  interop/send-reaction-blocking
                  (fn [request]
                    (swap! reacts conj (request-payload request))
                    :ok)]
      (let [runtime  (sut/start! {::schemas/username "bot"}
                                 {:on-text
                                  (fn [event]
                                    (swap! callbacks conj [:text event])
                                    ((:reply! event) "ACK"))
                                  :on-reaction
                                  (fn [event]
                                    (swap! callbacks conj [:reaction event])
                                    ((:react! event) (:key event)))})
            on-event (get-in @calls [:pump ::schemas/on-event])]
        (on-event {:kind     :text
                   :room-id  "!r:example.org"
                   :sender   "@alice:example.org"
                   :event-id "$e1"
                   :body     "hello"})
        (on-event {:kind     :reaction
                   :room-id  "!r:example.org"
                   :sender   "@alice:example.org"
                   :event-id "$e2"
                   :key      "🔥"})
        (is (= 2 (count @callbacks)))
        (is (= "ACK" (get-in (first @text-sends) [:body])))
        (is (= "🔥" (get-in (first @reacts) [:key])))
        (is (= :text
               (:kind (.poll ^BlockingQueue (:events runtime)
                             1
                             TimeUnit/SECONDS))))
        (is (= :reaction
               (:kind (.poll ^BlockingQueue (:events runtime)
                             1
                             TimeUnit/SECONDS))))))))

(deftest queue-backpressure-blocks-producer-until-consumer-drains-test
  (let [calls (atom {})]
    (with-redefs [interop/login-with-password-blocking
                  (fn [_] :client-handle)

                  interop/from-store-blocking
                  (fn [_] nil)

                  interop/start-sync-blocking
                  (fn [_] nil)

                  interop/start-timeline-pump
                  (fn [request]
                    (swap! calls assoc :pump (request-payload request))
                    :timeline-pump)

                  interop/stop-timeline-pump
                  (fn [_] nil)]
      (let [runtime  (sut/start! {::schemas/username "bot"
                                  :event-queue-size  1})
            on-event (get-in @calls [:pump ::schemas/on-event])
            done     (promise)]
        (on-event {:kind     :text
                   :room-id  "!r:example.org"
                   :sender   "@a:example.org"
                   :event-id "$e1"
                   :body     "first"})

        (future
          (on-event {:kind     :text
                     :room-id  "!r:example.org"
                     :sender   "@b:example.org"
                     :event-id "$e2"
                     :body     "second"})
          (deliver done :finished))

        (is (= :blocked (deref done 100 :blocked)))
        (is (= :text (:kind (.take ^BlockingQueue (:events runtime)))))
        (is (= :finished (deref done 1000 :timeout)))
        (is (= :text (:kind (.take ^BlockingQueue (:events runtime)))))))))

(deftest start-normalizes-stringly-typed-bridge-events-test
  (let [calls (atom {})]
    (with-redefs [interop/login-with-password-blocking
                  (fn [_] :client-handle)

                  interop/from-store-blocking
                  (fn [_] nil)

                  interop/start-sync-blocking
                  (fn [_] nil)

                  interop/start-timeline-pump
                  (fn [request]
                    (swap! calls assoc :pump (request-payload request))
                    :timeline-pump)

                  interop/stop-timeline-pump
                  (fn [_] nil)]
      (let [runtime  (sut/start! {::schemas/username "bot"})
            on-event (get-in @calls [:pump ::schemas/on-event])]
        (on-event {"type"   "m.room.message"
                   "room"   "!r:example.org"
                   "id"     "$e1"
                   "sender" "@alice:example.org"
                   "text"   "hello"})
        (on-event {"type"     "m.reaction"
                   "room"     "!r:example.org"
                   "id"       "$e2"
                   "sender"   "@alice:example.org"
                   "reaction" "👍"})
        (let [text-event     (.take ^BlockingQueue (:events runtime))
              reaction-event (.take ^BlockingQueue (:events runtime))]
          (is (= :text (:kind text-event)))
          (is (= "!r:example.org" (:room-id text-event)))
          (is (= "$e1" (:event-id text-event)))
          (is (= "hello" (:body text-event)))
          (is (fn? (:reply! text-event)))
          (is (= :reaction (:kind reaction-event)))
          (is (= "👍" (:key reaction-event)))
          (is (fn? (:react! reaction-event))))))))

(deftest start-prefers-from-store-client-before-login-test
  (let [calls    (atom {})
        database (Object.)]
    (with-redefs [interop/from-store-blocking
                  (fn [request]
                    (swap! calls assoc :from-store (request-payload request))
                    :stored-client)

                  interop/login-with-password-blocking
                  (fn [_]
                    (throw (ex-info "login should not be called" {})))

                  interop/start-sync-blocking
                  (fn [request]
                    (swap! calls assoc :sync (request-payload request))
                    nil)

                  interop/start-timeline-pump
                  (fn [_] :timeline-pump)

                  interop/stop-timeline-pump
                  (fn [_] nil)]
      (let [runtime (sut/start! {::schemas/database   database
                                 ::schemas/media-path "./tmp/media"})]
        (is (= :stored-client (:client runtime)))
        (is (= database (get-in @calls [:from-store ::schemas/database])))
        (is (= "./tmp/media" (get-in @calls [:from-store ::schemas/media-path])))
        (is (= :stored-client (get-in @calls [:sync ::schemas/client])))))))
