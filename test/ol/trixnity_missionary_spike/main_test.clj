(ns ol.trixnity-missionary-spike.main-test
  (:require
   [clojure.test :refer [deftest is testing]]
   [missionary.core :as m]
   [ol.trixnity.client :as client]
   [ol.trixnity.repo :as repo]
   [ol.trixnity.room :as room]
   [ol.trixnity.schemas :as schemas]
   [ol.trixnity-missionary-spike.main :as sut])
  (:import
   [java.time Duration]))

(defn- task-of [value]
  (m/sp value))

(deftest run-spike-loads-env-starts-client-and-observes-rooms-test
  (let [calls       (atom [])
        printed     (atom [])
        room-id     "!focus:example.org"
        flat-flow   :flat-flow
        nested-flow :nested-flow
        room-flow   :room-flow
        close-calls (atom [])]
    (with-redefs [sut/read-env
                  (fn [name]
                    ({"MATRIX_HOMESERVER_URL" "https://matrix.example.org"
                      "MATRIX_BOT_USER_ID"    "@bot:example.org"
                      "MATRIX_BOT_PASSWORD"   "secret"
                      "MATRIX_ROOM_ID"        room-id}
                     name))

                  sut/println!
                  (fn [& args]
                    (swap! printed conj (apply str args)))

                  repo/sqlite4clj-config
                  (fn [config]
                    (swap! calls conj [:repo-config config])
                    {::schemas/database-path (:database-path config)
                     ::schemas/media-path    (:media-path config)})

                  client/open
                  (fn [config]
                    (swap! calls conj [:open config])
                    (task-of :client-handle))

                  client/start-sync
                  (fn [client]
                    (swap! calls conj [:start-sync client])
                    (task-of :sync-started))

                  client/await-running
                  (fn [client opts]
                    (swap! calls conj [:await-running client opts])
                    (task-of :running))

                  room/get-all-flat
                  (fn [client]
                    (swap! calls conj [:get-all-flat client])
                    flat-flow)

                  room/get-all
                  (fn [client]
                    (swap! calls conj [:get-all client])
                    nested-flow)

                  room/get-by-id
                  (fn [client selected-room-id]
                    (swap! calls conj [:get-by-id client selected-room-id])
                    room-flow)

                  sut/start-flow-drain!
                  (fn [label flow printer]
                    (swap! calls conj [:drain label flow])
                    (printer label :started)
                    (keyword label))

                  sut/close-drain!
                  (fn [drain]
                    (swap! close-calls conj drain))

                  client/close
                  (fn [client]
                    (swap! calls conj [:close client])
                    (task-of :closed))]
      (let [runtime (sut/run-spike!)]
        (testing "the lifecycle is driven by the public Missionary-first namespaces"
          (is (= :client-handle (:client runtime)))
          (is (= [:flat :nested (keyword (str "room " room-id))]
                 (:drains runtime)))
          (is (= room-id (:room-id runtime))))
        (testing "env vars feed the happy-path client config and local spike paths"
          (is (some
               #{[:repo-config {:database-path "./dev-data/missionary-spike/trixnity.sqlite"
                                :media-path    "./dev-data/missionary-spike/media"}]}
               @calls))
          (is (some
               #{[:open
                  {::schemas/homeserver-url "https://matrix.example.org"
                   ::schemas/user-id        "@bot:example.org"
                   ::schemas/password       "secret"
                   ::schemas/database-path  "./dev-data/missionary-spike/trixnity.sqlite"
                   ::schemas/media-path     "./dev-data/missionary-spike/media"}]}
               @calls))
          (is (some
               #{[:await-running
                  :client-handle
                  {::schemas/timeout (Duration/ofSeconds 30)}]}
               @calls)))
        (testing "the dev slice observes flat, keyed, and direct room views"
          (is (some #{[:get-all-flat :client-handle]} @calls))
          (is (some #{[:get-all :client-handle]} @calls))
          (is (some #{[:get-by-id :client-handle room-id]} @calls)))
        (testing "startup prints compact progress lines"
          (is (some #{"opening client"} @printed))
          (is (some #{"starting sync"} @printed))
          (is (some #{"awaiting RUNNING"} @printed))
          (is (some #{"starting room observers"} @printed))
          (is (some #{(str "spike running; waiting for updates. Ctrl-C to stop. room-id="
                           room-id)}
                    @printed)))
        (sut/close-runtime! runtime)
        (is (= [(keyword (str "room " room-id)) :nested :flat]
               @close-calls))
        (is (some #{[:close :client-handle]} @calls))))))
