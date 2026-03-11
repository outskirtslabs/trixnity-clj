(ns ol.trixnity-poc.main-test
  (:require
   [clojure.test :refer [deftest is testing]]
   [ol.trixnity.client :as client]
   [ol.trixnity.room :as room]
   [ol.trixnity.room.message :as msg]
   [ol.trixnity.schemas :as schemas]
   [ol.trixnity.timeline :as timeline]
   [ol.trixnity-poc.config :as config]
   [ol.trixnity-poc.main :as sut]
   [ol.trixnity-poc.invite-error :as invite-error]
   [ol.trixnity-poc.room-state :as room-state])
  (:import
   [java.io Closeable]
   [java.time Duration]
   [java.util.concurrent CompletableFuture]))

(defn- completed-future [value]
  (CompletableFuture/completedFuture value))

(deftype StubSubscription [closed?]
  Closeable
  (close [_]
    (reset! closed? true)))

(deftest run-poc-opens-starts-subscribes-and-replies-using-the-new-surface-test
  (let [calls         (atom [])
        room-state*   (atom nil)
        handler*      (atom nil)
        closed?       (atom false)
        open-future   (completed-future :client-handle)
        start-future  (completed-future nil)
        await-future  (completed-future nil)
        invite-future (completed-future nil)
        send-future   (completed-future "$txn")
        react-future  (completed-future "$reaction")
        subscription  (->StubSubscription closed?)]
    (with-redefs [config/load-config
                  (fn []
                    {:homeserver-url "https://matrix.example.org"
                     :username       "bot"
                     :password       "secret"
                     :room-name      "Bot Room"
                     :room-id-file   "./tmp/facade-room-id.txt"
                     :database-path  "./tmp/facade-db"
                     :media-path     "./tmp/facade-media"
                     :invite-user    "@alice:example.org"})

                  room-state/load-room-id
                  (fn [_]
                    @room-state*)

                  room-state/save-room-id!
                  (fn [_ room-id]
                    (reset! room-state* room-id)
                    room-id)

                  client/open!
                  (fn [cfg]
                    (swap! calls conj [:open cfg])
                    open-future)

                  client/start-sync!
                  (fn [opened-client]
                    (swap! calls conj [:start-sync opened-client])
                    start-future)

                  client/await-running!
                  (fn [opened-client opts]
                    (swap! calls conj [:await-running opened-client opts])
                    await-future)

                  client/current-user-id
                  (fn [_]
                    "@bot:example.org")

                  room/create!
                  (fn [opened-client opts]
                    (swap! calls conj [:create-room opened-client opts])
                    (completed-future "!new:example.org"))

                  room/invite!
                  (fn [opened-client room-id user-id]
                    (swap! calls conj [:invite opened-client room-id user-id])
                    invite-future)

                  room/send!
                  (fn [opened-client room-id message opts]
                    (swap! calls conj [:send opened-client room-id message opts])
                    send-future)

                  room/react!
                  (fn [opened-client room-id ev key]
                    (swap! calls conj [:react opened-client room-id ev key])
                    react-future)

                  timeline/subscribe!
                  (fn [opened-client opts handler]
                    (swap! calls conj [:subscribe opened-client opts])
                    (reset! handler* handler)
                    subscription)]
      (let [runtime                                  (sut/run-poc!)
            text-ev                                  {::schemas/type       "m.room.message"
                                                      ::schemas/room-id    "!new:example.org"
                                                      ::schemas/event-id   "$event-1"
                                                      ::schemas/sender     "@human:example.org"
                                                      ::schemas/body       "hello"
                                                      ::schemas/relates-to {::schemas/relation-type     "m.thread"
                                                                            ::schemas/relation-event-id "$root"}}
            reaction-ev
            {::schemas/type     "m.reaction"
             ::schemas/room-id  "!new:example.org"
             ::schemas/event-id "$event-2"
             ::schemas/sender   "@human:example.org"
             ::schemas/key      "🔥"}]
        (is (= "!new:example.org" (:room-id runtime)))
        (is (= :client-handle (:client runtime)))
        (is (identical? subscription (:subscription runtime)))

        (testing "the lifecycle uses futures with explicit timeouts"
          (is (some #{[:start-sync :client-handle]} @calls))
          (is (some #{[:await-running
                       :client-handle
                       {::schemas/timeout (Duration/ofSeconds 30)}]}
                    @calls))
          (is (some #{[:subscribe
                       :client-handle
                       {::schemas/decryption-timeout (Duration/ofSeconds 8)}]}
                    @calls)))

        (testing "timeline callbacks reply and react through room/message namespaces"
          (@handler* text-ev)
          (@handler* reaction-ev)
          (@handler* (assoc text-ev ::schemas/sender "@bot:example.org"))
          (@handler* (assoc reaction-ev ::schemas/sender "@bot:example.org"))

          (is (some #{[:send
                       :client-handle
                       "!new:example.org"
                       (-> (msg/text "HELLO")
                           (msg/reply-to text-ev))
                       {::schemas/timeout (Duration/ofSeconds 5)}]}
                    @calls))
          (is (some #{[:react
                       :client-handle
                       "!new:example.org"
                       reaction-ev
                       "🔥"]}
                    @calls)))))))

(deftest run-poc-reuses-stored-room-id-without-creating-a-room-test
  (let [calls        (atom [])
        open-future  (completed-future :client-handle)
        start-future (completed-future nil)
        await-future (completed-future nil)]
    (with-redefs [config/load-config
                  (fn []
                    {:homeserver-url "https://matrix.example.org"
                     :username       "bot"
                     :password       "secret"
                     :room-name      "Bot Room"
                     :room-id-file   "./tmp/facade-room-id.txt"
                     :database-path  "./tmp/facade-db"
                     :media-path     "./tmp/facade-media"})

                  room-state/load-room-id
                  (fn [_]
                    "!existing:example.org")

                  room-state/save-room-id!
                  (fn [_ _]
                    (throw (ex-info "save-room-id! should not be called" {})))

                  client/open!
                  (fn [_]
                    open-future)

                  client/start-sync!
                  (fn [_]
                    start-future)

                  client/await-running!
                  (fn [_ _]
                    await-future)

                  client/current-user-id
                  (fn [_]
                    "@bot:example.org")

                  room/create!
                  (fn [_ _]
                    (swap! calls conj :create-room)
                    (completed-future "!new:example.org"))

                  timeline/subscribe!
                  (fn [_ _ _]
                    (swap! calls conj :subscribe)
                    (->StubSubscription (atom false)))]
      (let [runtime (sut/run-poc!)]
        (is (= "!existing:example.org" (:room-id runtime)))
        (is (= [:subscribe] @calls))))))

(deftest run-poc-continues-when-invite-fails-test
  (with-redefs [config/load-config
                (fn []
                  {:homeserver-url "https://matrix.example.org"
                   :username       "bot"
                   :password       "secret"
                   :room-name      "Bot Room"
                   :room-id-file   "./tmp/facade-room-id.txt"
                   :database-path  "./tmp/facade-db"
                   :media-path     "./tmp/facade-media"
                   :invite-user    "@alice:example.org"})

                room-state/load-room-id
                (fn [_]
                  "!existing:example.org")

                client/open!
                (fn [_]
                  (completed-future :client-handle))

                client/start-sync!
                (fn [_]
                  (completed-future nil))

                client/await-running!
                (fn [_ _]
                  (completed-future nil))

                client/current-user-id
                (fn [_]
                  "@bot:example.org")

                room/invite!
                (fn [_ _ _]
                  (throw (RuntimeException.
                          "statusCode=500 Internal Server Error")))

                timeline/subscribe!
                (fn [_ _ _]
                  (->StubSubscription (atom false)))

                invite-error/already-in-room-invite-failure?
                (fn [_]
                  false)]
    (let [runtime (sut/run-poc!)]
      (is (= "!existing:example.org" (:room-id runtime)))
      (is (= "@bot:example.org" (:bot-user-id runtime))))))
