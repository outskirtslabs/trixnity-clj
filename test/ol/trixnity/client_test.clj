(ns ol.trixnity.client-test
  (:require
   [clojure.test :refer [deftest is testing]]
   [ol.trixnity.client :as sut]
   [ol.trixnity.interop :as interop]
   [ol.trixnity.schemas :as schemas])
  (:import
   [java.time Duration]
   [java.util.concurrent CompletableFuture]))

(defn- completed-future [value]
  (CompletableFuture/completedFuture value))

(defn- request-payload [request]
  (into {} request))

(deftest open-begins-the-supported-public-async-story-test
  (let [calls  (atom [])
        future (completed-future :client-handle)
        config {::schemas/homeserver-url "https://matrix.example.org"
                ::schemas/username       "bot"
                ::schemas/password       "secret"
                ::schemas/database-path  "./tmp/state/trixnity.sqlite"
                ::schemas/media-path     "./tmp/media"}]
    (with-redefs [interop/open-client
                  (fn [request]
                    (swap! calls conj (request-payload request))
                    future)]
      (is (identical? future (sut/open! config)))
      (is (= [{::schemas/homeserver-url "https://matrix.example.org"
               ::schemas/username       "bot"
               ::schemas/password       "secret"
               ::schemas/database-path  "./tmp/state/trixnity.sqlite"
               ::schemas/media-path     "./tmp/media"}]
             @calls)))))

(deftest lifecycle-operations-return-futures-and-forward-timeout-opts-test
  (let [calls        (atom {})
        client       :client-handle
        start-future (completed-future nil)
        await-future (completed-future nil)
        stop-future  (completed-future nil)
        close-future (completed-future nil)
        timeout      (Duration/ofSeconds 30)]
    (with-redefs [interop/start-sync
                  (fn [request]
                    (swap! calls assoc :start-sync (request-payload request))
                    start-future)

                  interop/await-running
                  (fn [request]
                    (swap! calls assoc :await-running (request-payload request))
                    await-future)

                  interop/stop-sync
                  (fn [request]
                    (swap! calls assoc :stop-sync (request-payload request))
                    stop-future)

                  interop/close-client
                  (fn [request]
                    (swap! calls assoc :close-client (request-payload request))
                    close-future)]
      (is (identical? start-future (sut/start-sync! client)))
      (is (identical? await-future
                      (sut/await-running! client {::schemas/timeout timeout})))
      (is (identical? stop-future (sut/stop-sync! client)))
      (is (identical? close-future (sut/close! client)))

      (is (= {::schemas/client client}
             (:start-sync @calls)))
      (is (= {::schemas/client  client
              ::schemas/timeout timeout}
             (:await-running @calls)))
      (is (= {::schemas/client client}
             (:stop-sync @calls)))
      (is (= {::schemas/client client}
             (:close-client @calls))))))

(deftest current-user-id-and-sync-state-stay-thin-test
  (let [calls (atom {})]
    (with-redefs [interop/current-user-id
                  (fn [request]
                    (swap! calls assoc :current-user-id (request-payload request))
                    "@bot:example.org")

                  interop/sync-state
                  (fn [request]
                    (swap! calls assoc :sync-state (request-payload request))
                    :running)]
      (testing "non-blocking accessors delegate without changing the result"
        (is (= "@bot:example.org"
               (sut/current-user-id :client-handle)))
        (is (= :running
               (sut/sync-state :client-handle))))

      (is (= {::schemas/client :client-handle}
             (:current-user-id @calls)))
      (is (= {::schemas/client :client-handle}
             (:sync-state @calls))))))
