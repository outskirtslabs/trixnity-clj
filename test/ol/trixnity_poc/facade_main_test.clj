(ns ol.trixnity-poc.facade-main-test
  (:require
   [clojure.string :as str]
   [clojure.test :refer [deftest is]]
   [ol.trixnity.client :as client]
   [ol.trixnity-poc.config :as config]
   [ol.trixnity-poc.facade-main :as sut]
   [ol.trixnity-poc.room-state :as room-state]))

(deftest run-poc-creates-room-and-wires-mirror-and-reaction-handlers-test
  (let [calls          (atom [])
        room-state*    (atom nil)
        reply-bodies   (atom [])
        reaction-keys  (atom [])
        start-handlers (atom nil)]
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
                  (fn [_] @room-state*)

                  room-state/save-room-id!
                  (fn [_ room-id]
                    (reset! room-state* room-id)
                    room-id)

                  client/start!
                  (fn [cfg handlers]
                    (swap! calls conj [:start cfg])
                    (reset! start-handlers handlers)
                    {:client :client-handle
                     :events (java.util.concurrent.LinkedBlockingQueue.)
                     :stop!  (fn [] nil)})

                  client/ensure-room!
                  (fn [runtime payload]
                    (swap! calls conj [:ensure-room runtime payload])
                    "!new:example.org")

                  client/invite-user!
                  (fn [runtime payload]
                    (swap! calls conj [:invite runtime payload])
                    :invited)]
      (let [result (sut/run-poc!)]
        (is (= "!new:example.org" (:room-id result)))
        (is (= :client-handle (:client result)))
        (is (= [[:start {:homeserver-url "https://matrix.example.org"
                         :username       "bot"
                         :password       "secret"
                         :store-path     "./tmp/facade-db"
                         :media-path     "./tmp/facade-media"
                         :encryption?    true}]
                [:ensure-room {:client :client-handle
                               :events (:events result)
                               :stop!  (:stop! result)}
                 {:room-name "Bot Room"}]
                [:invite {:client :client-handle
                          :events (:events result)
                          :stop!  (:stop! result)}
                 {:room-id "!new:example.org"
                  :user-id "@alice:example.org"}]]
               @calls))

        (let [on-text     (:on-text @start-handlers)
              on-reaction (:on-reaction @start-handlers)]
          (is (fn? on-text))
          (is (fn? on-reaction))

          (on-text {:sender "@human:example.org"
                    :body   "hello"
                    :reply! (fn [body] (swap! reply-bodies conj body))})
          (on-reaction {:sender "@human:example.org"
                        :key    "🔥"
                        :react! (fn [key] (swap! reaction-keys conj key))})

          (is (= ["HELLO"] @reply-bodies))
          (is (= ["🔥"] @reaction-keys)))))))

(deftest run-poc-reuses-stored-room-id-without-create-room-test
  (let [calls (atom [])]
    (with-redefs [config/load-config
                  (fn []
                    {:homeserver-url "https://matrix.example.org"
                     :username       "bot"
                     :password       "secret"
                     :room-name      "Bot Room"
                     :room-id-file   "./tmp/facade-room-id.txt"
                     :database-path  "./tmp/facade-db"
                     :media-path     "./tmp/facade-media"
                     :invite-user    nil})

                  room-state/load-room-id
                  (fn [_] "!existing:example.org")

                  room-state/save-room-id!
                  (fn [_ _]
                    (throw (ex-info "save-room-id! should not be called" {})))

                  client/start!
                  (fn [_ _]
                    {:client :client-handle
                     :events (java.util.concurrent.LinkedBlockingQueue.)
                     :stop!  (fn [] nil)})

                  client/ensure-room!
                  (fn [_ _]
                    (swap! calls conj :ensure-room)
                    "!new:example.org")

                  client/invite-user!
                  (fn [_ _]
                    (swap! calls conj :invite)
                    :invited)]
      (let [result (sut/run-poc!)]
        (is (= "!existing:example.org" (:room-id result)))
        (is (empty? @calls))))))

(deftest facade-main-source-avoids-legacy-reflection-interop-test
  (let [source (slurp "dev/ol/trixnity_poc/facade_main.clj")]
    (is (str/includes? source "ol.trixnity.client"))
    (is (not (str/includes? source "clojure.lang.Reflector/invoke")))
    (is (not (str/includes? source "$default")))))
