(ns ol.trixnity-poc.main-test
  (:require
   [clojure.test :refer [deftest is testing]]
   [missionary.core :as m]
   [ol.trixnity.client :as client]
   [ol.trixnity.room :as room]
   [ol.trixnity.room.message :as msg]
   [ol.trixnity.schemas :as schemas]
   [ol.trixnity-poc.config :as config]
   [ol.trixnity-poc.invite-error :as invite-error]
   [ol.trixnity-poc.main :as sut]
   [ol.trixnity-poc.room-state :as room-state])
  (:import
   [java.time Duration]))

(defn- task-of [value]
  (m/sp value))

(deftest run-poc-opens-starts-resolves-room-and-starts-subscription-test
  (let [calls       (atom [])
        room-state* (atom nil)]
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

                  client/open
                  (fn [cfg]
                    (swap! calls conj [:open cfg])
                    (task-of :client-handle))

                  client/start-sync
                  (fn [opened-client]
                    (swap! calls conj [:start-sync opened-client])
                    (task-of nil))

                  client/await-running
                  (fn [opened-client opts]
                    (swap! calls conj [:await-running opened-client opts])
                    (task-of nil))

                  client/current-user-id
                  (fn [_]
                    "@bot:example.org")

                  room/create-room
                  (fn [opened-client opts]
                    (swap! calls conj [:create-room opened-client opts])
                    (task-of "!new:example.org"))

                  room/invite-user
                  (fn [opened-client room-id user-id]
                    (swap! calls conj [:invite opened-client room-id user-id])
                    (task-of nil))

                  sut/start-subscription!
                  (fn [opened-client room-id bot-user-id]
                    (swap! calls conj [:subscribe opened-client room-id bot-user-id])
                    :subscription-handle)]
      (let [runtime (sut/run-poc!)]
        (is (= "!new:example.org" (:room-id runtime)))
        (is (= :client-handle (:client runtime)))
        (is (= :subscription-handle (:subscription runtime)))
        (testing "the lifecycle uses Missionary tasks with explicit timeout opts"
          (is (some #{[:start-sync :client-handle]} @calls))
          (is (some #{[:await-running
                       :client-handle
                       {::schemas/timeout (Duration/ofSeconds 30)}]}
                    @calls)))
        (testing "room creation, invitation, and subscription all use the new room namespace"
          (is (some #{[:create-room :client-handle {::schemas/room-name "Bot Room"}]} @calls))
          (is (some #{[:invite :client-handle "!new:example.org" "@alice:example.org"]} @calls))
          (is (some #{[:subscribe :client-handle "!new:example.org" "@bot:example.org"]} @calls)))))))

(deftest handler-replies-to-text-and-reactions-through-room-namespace-test
  (let [calls       (atom [])
        text-ev     {::schemas/type       "m.room.message"
                     ::schemas/room-id    "!room:example.org"
                     ::schemas/event-id   "$event-1"
                     ::schemas/sender     "@human:example.org"
                     ::schemas/body       "hello"
                     ::schemas/relates-to {::schemas/relation-type     "m.thread"
                                           ::schemas/relation-event-id "$root"}}
        reaction-ev {::schemas/type     "m.reaction"
                     ::schemas/room-id  "!room:example.org"
                     ::schemas/event-id "$event-2"
                     ::schemas/sender   "@human:example.org"
                     ::schemas/relates-to
                     {::schemas/relation-type     "m.annotation"
                      ::schemas/relation-event-id "$event-1"}
                     ::schemas/key      "🔥"}]
    (with-redefs [room/send-message
                  (fn [client room-id message opts]
                    (swap! calls conj [:send client room-id message opts])
                    (task-of "$txn"))

                  room/send-reaction
                  (fn [client room-id ev key]
                    (swap! calls conj [:react client room-id ev key])
                    (task-of "$reaction"))]
      (let [handler (#'sut/handler :client-handle "!room:example.org" "@bot:example.org")]
        (m/? (handler text-ev))
        (m/? (handler reaction-ev))
        (handler (assoc text-ev ::schemas/sender "@bot:example.org"))
        (is (some #{[:send
                     :client-handle
                     "!room:example.org"
                     (-> (msg/text "HELLO")
                         (msg/reply-to text-ev))
                     {::schemas/timeout (Duration/ofSeconds 5)}]}
                  @calls))
        (is (some #{[:react
                     :client-handle
                     "!room:example.org"
                     {::schemas/room-id  "!room:example.org"
                      ::schemas/event-id "$event-1"}
                     "🔥"]}
                  @calls))))))

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

                client/open                                  (fn [_] (task-of :client-handle))
                client/start-sync                            (fn [_] (task-of nil))
                client/await-running                         (fn [_ _] (task-of nil))
                client/current-user-id                       (fn [_] "@bot:example.org")
                room/invite-user                             (fn [_ _ _] (throw (RuntimeException. "invite failed")))
                sut/start-subscription!                      (fn [_ _ _] :subscription)
                invite-error/already-in-room-invite-failure? (fn [_] false)]
    (let [runtime (sut/run-poc!)]
      (is (= "!existing:example.org" (:room-id runtime)))
      (is (= "@bot:example.org" (:bot-user-id runtime))))))
