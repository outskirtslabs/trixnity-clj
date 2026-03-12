(ns ol.trixnity.notification-test
  (:require
   [clojure.test :refer [deftest is]]
   [missionary.core :as m]
   [ol.trixnity.internal :as internal]
   [ol.trixnity.internal.bridge :as bridge]
   [ol.trixnity.notification :as sut]
   [ol.trixnity.schemas :as schemas])
  (:import
   [de.connect2x.trixnity.clientserverapi.model.sync Sync$Response]
   [java.time Duration]))

(defn- collect-values [flow n]
  (m/? (->> flow
            (m/eduction (take n))
            (m/reduce conj []))))

(deftest notification-surfaces-stay-thin-test
  (let [calls        (atom {})
        timeout      (Duration/ofSeconds 8)
        notification {::schemas/id "n1"}
        update       {::schemas/id "u1"}]
    (with-redefs [bridge/notifications                                                                       (fn [client timeout-ms buffer-size] (swap! calls assoc :notifications [client timeout-ms buffer-size]) ::notifications-flow)
                  bridge/notifications-from-response                                                         (fn [client response timeout-ms] (swap! calls assoc :notifications-response [client response timeout-ms]) ::notifications-response-flow)
                  bridge/notification-all                                                                    (fn [client] (swap! calls assoc :all [client]) ::all-flow)
                  bridge/notification-all-flat                                                               (fn [client] (swap! calls assoc :all-flat [client]) ::all-flat-flow)
                  bridge/notification-by-id                                                                  (fn [client id] (swap! calls assoc :by-id [client id]) ::by-id-flow)
                  bridge/notification-count                                                                  (fn [& args] (swap! calls assoc :count args) ::count-flow)
                  bridge/notification-unread                                                                 (fn [client room-id] (swap! calls assoc :unread [client room-id]) ::unread-flow)
                  bridge/notification-all-updates                                                            (fn [client] (swap! calls assoc :updates [client]) ::updates-flow)
                  internal/observe-flow
                  (fn [_ kotlin-flow]
                    (m/observe
                     (fn [emit]
                       (future
                         (emit (case kotlin-flow
                                 ::notifications-flow notification
                                 ::notifications-response-flow notification
                                 ::all-flat-flow []
                                 ::by-id-flow nil
                                 ::count-flow 0
                                 ::unread-flow false
                                 ::updates-flow update))
                         (emit (case kotlin-flow
                                 ::notifications-flow notification
                                 ::notifications-response-flow notification
                                 ::all-flat-flow [notification]
                                 ::by-id-flow notification
                                 ::count-flow 1
                                 ::unread-flow true
                                 ::updates-flow update)))
                       (constantly nil))))
                  internal/observe-flow-list
                  (fn [_ kotlin-flow]
                    (is (= ::all-flow kotlin-flow))
                    (m/observe (fn [emit] (future (emit []) (emit [:notification-flow])) (constantly nil))))]
      (is (= [notification notification]
             (collect-values
              #_{:clj-kondo/ignore [:deprecated-var]}
              (sut/get-notifications :client {::schemas/decryption-timeout timeout})
              2)))
      (is (= [notification notification]
             (collect-values
              #_{:clj-kondo/ignore [:deprecated-var]}
              (sut/get-notifications
               :client
               (Sync$Response. "next" nil nil nil nil nil nil nil)
               {})
              2)))
      (is (= [[] [:notification-flow]]
             (collect-values (sut/get-all :client) 2)))
      (is (= [[] [notification]]
             (collect-values (sut/get-all-flat :client) 2)))
      (is (= [nil notification]
             (collect-values (sut/get-by-id :client "n1") 2)))
      (is (= [0 1]
             (collect-values (sut/get-count :client) 2)))
      (is (= [false true]
             (collect-values (sut/is-unread :client "!room") 2)))
      (is (= [update update]
             (collect-values (sut/get-all-updates :client) 2))))))

(deftest notification-update-schema-rejects-arbitrary-content-maps-test
  (is (try
        (schemas/validate!
         (schemas/registry {})
         ::schemas/NotificationUpdate
         {::schemas/id                       "u1"
          ::schemas/sort-key                 "001"
          ::schemas/notification-update-kind "new"
          ::schemas/content                  {:bogus true}})
        false
        (catch clojure.lang.ExceptionInfo _ true))))
