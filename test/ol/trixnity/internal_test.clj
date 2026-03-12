(ns ol.trixnity.internal-test
  (:require
   [clojure.test :refer [deftest is testing]]
   [missionary.core :as m]
   [ol.trixnity.internal :as sut])
  (:import
   [java.io Closeable]))

(defn- collect-values [flow n]
  (m/? (->> flow
            (m/eduction (take n))
            (m/reduce conj []))))

(deftype StubCloseable [closed-count]
  Closeable
  (close [_]
    (swap! closed-count inc)))

(deftest suspend-task-appends-success-and-failure-callbacks-and-closes-on-cancel-test
  (let [calls        (atom [])
        closed-count (atom 0)
        callbacks    (atom nil)
        task         (sut/suspend-task
                      (fn [client room-id on-success on-failure]
                        (swap! calls conj [client room-id])
                        (reset! callbacks {:success on-success
                                           :failure on-failure})
                        (->StubCloseable closed-count))
                      :client-handle
                      "!room:example.org")
        successes    (atom [])
        failures     (atom [])
        cancel       (task #(swap! successes conj %)
                           #(swap! failures conj %))]
    ((:success @callbacks) :ok)
    ((:failure @callbacks) (ex-info "boom" {}))
    (cancel)
    (is (= [[:client-handle "!room:example.org"]] @calls))
    (is (= [:ok] @successes))
    (is (= ["boom"] (mapv ex-message @failures)))
    (is (= 1 @closed-count))))

(deftest observe-flow-emits-values-and-closes-on-termination-test
  (let [emit*        (promise)
        closed-count (atom 0)]
    (with-redefs [sut/observe-kotlin-flow
                  (fn [_ _ emit]
                    (deliver emit* emit)
                    (->StubCloseable closed-count))]
      (let [result (future (collect-values (sut/observe-flow :client-handle :flow) 3))]
        (@emit* :one)
        (@emit* :two)
        (@emit* :three)
        (is (= [:one :two :three] @result))
        (is (= 1 @closed-count))))))

(deftest observe-keyed-flow-map-reuses-keys-and-drops-removed-entries-test
  (let [snapshots [{}
                   {"!a:example.org" :a-1}
                   {"!a:example.org" :a-2
                    "!b:example.org" :b-1}
                   {"!b:example.org" :b-2}
                   {"!a:example.org" :a-3}]]
    (with-redefs [sut/observe-flow
                  (fn [_ kotlin-flow]
                    (if (= kotlin-flow ::outer)
                      (m/observe
                       (fn [emit]
                         (future
                           (doseq [snapshot snapshots]
                             (emit snapshot)))
                         (constantly nil)))
                      [:wrapped kotlin-flow]))]
      (let [[empty-map
             first-map
             second-map
             third-map
             fourth-map]
            (collect-values (sut/observe-keyed-flow-map :client-handle ::outer) 5)]
        (testing "the outer shape tracks the current keys"
          (is (= {} empty-map))
          (is (= ["!a:example.org"] (sort (keys first-map))))
          (is (= ["!a:example.org" "!b:example.org"]
                 (sort (keys second-map))))
          (is (= ["!b:example.org"] (sort (keys third-map))))
          (is (= ["!a:example.org"] (sort (keys fourth-map)))))
        (testing "repeated keys reuse the original wrapped inner flow"
          (is (identical? (get first-map "!a:example.org")
                          (get second-map "!a:example.org"))))
        (testing "keys removed from the outer map lose their cached wrapper"
          (is (not (identical? (get first-map "!a:example.org")
                               (get fourth-map "!a:example.org")))))))))

(deftest observe-flow-list-wraps-inner-flows-in-order-test
  (let [snapshots [[:first :second]
                   [:third]]]
    (with-redefs [sut/observe-flow
                  (fn [_ kotlin-flow]
                    (if (= kotlin-flow ::outer)
                      (m/observe
                       (fn [emit]
                         (future
                           (doseq [snapshot snapshots]
                             (emit snapshot)))
                         (constantly nil)))
                      [:wrapped kotlin-flow]))]
      (is (= [[[:wrapped :first] [:wrapped :second]]
              [[:wrapped :third]]]
             (collect-values (sut/observe-flow-list :client-handle ::outer) 2))))))
