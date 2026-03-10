(ns ol.trixnity-poc.bot-logic-test
  (:require
   [clojure.test :refer [deftest is testing]]
   [ol.trixnity-poc.bot-logic :as sut])
  (:import
   (net.folivo.trixnity.core.model EventId UserId)
   (net.folivo.trixnity.core.model.events.m ReactionEventContent RelatesTo$Annotation)))

(deftest mirrored-body-test
  (is (= "HELLO MATRIX"
         (sut/mirrored-body "Hello Matrix"))))

(deftest should-handle-sender-test
  (let [bot-user (UserId. "@bot:example.org")]
    (is (false? (sut/should-handle-sender? bot-user bot-user)))
    (is (true? (sut/should-handle-sender?
                (UserId. "@alice:example.org")
                bot-user)))))

(deftest reaction-to-mirror-test
  (testing "returns target event id and emoji key"
    (let [content  (ReactionEventContent.
                    (RelatesTo$Annotation.
                     (EventId. "$event:example.org")
                     "👍")
                    nil)
          mirrored (sut/reaction-to-mirror content)]
      (is (some? mirrored))
      (is (= (EventId. "$event:example.org")
             (:event-id mirrored)))
      (is (= "👍"
             (:key mirrored)))))

  (testing "returns nil when reaction has no annotation relation"
    (is (nil? (sut/reaction-to-mirror (ReactionEventContent.))))))
