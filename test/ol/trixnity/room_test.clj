(ns ol.trixnity.room-test
  (:require
   [clojure.test :refer [deftest is]]
   [missionary.core :as m]
   [ol.trixnity.internal :as internal]
   [ol.trixnity.internal.bridge :as bridge]
   [ol.trixnity.room :as sut]
   [ol.trixnity.room.message :as msg]
   [ol.trixnity.schemas :as schemas])
  (:import
   [de.connect2x.trixnity.clientserverapi.model.sync Sync$Response]
   [de.connect2x.trixnity.core.model.events EmptyEventContent]
   [java.io Closeable]
   [java.time Duration]))

(defn- realize-task [task]
  (m/? task))

(defn- collect-values [flow n]
  (m/? (->> flow
            (m/eduction (take n))
            (m/reduce conj []))))

(defn- resolve-var [ns-sym var-sym]
  (ns-resolve ns-sym var-sym))

(deftype StubCloseable [closed-count]
  Closeable
  (close [_]
    (swap! closed-count inc)))

(deftest task-surfaces-return-missionary-tasks-test
  (let [calls                                       (atom {})
        timeout                                     (Duration/ofSeconds 5)
        ev                                          {::schemas/room-id  "!room:example.org"
                                                     ::schemas/event-id "$event"}
        state-event
        {::schemas/type "m.room.name"
         ::schemas/name "Ops Bot"}
        room-opts
        {::schemas/room-name "Ops Bot"
         ::schemas/topic     "Control room"
         ::schemas/invite    ["@alice:example.org"]
         ::schemas/preset    :private-chat
         ::schemas/is-direct true}]
    (with-redefs [bridge/create-room
                  (fn [client request on-success _]
                    (swap! calls assoc :create-room [client request])
                    (on-success "!room:example.org")
                    (->StubCloseable (atom 0)))

                  bridge/invite-user
                  (fn [client room-id user-id bridge-timeout on-success _]
                    (swap! calls assoc :invite-user [client room-id user-id bridge-timeout])
                    (on-success :invited)
                    (->StubCloseable (atom 0)))

                  bridge/send-reaction
                  (fn [client room-id event-id key bridge-timeout on-success _]
                    (swap! calls assoc :send-reaction [client room-id event-id key bridge-timeout])
                    (on-success "$reaction")
                    (->StubCloseable (atom 0)))

                  bridge/send-state-event
                  (fn [client room-id sent-state-event bridge-timeout on-success _]
                    (swap! calls assoc :send-state-event
                           [client room-id sent-state-event bridge-timeout])
                    (on-success "$state-event")
                    (->StubCloseable (atom 0)))]
      (is (= "!room:example.org"
             (realize-task
              (sut/create-room :client-handle room-opts))))
      (is (= :invited
             (realize-task
              (sut/invite-user :client-handle "!room:example.org" "@alice:example.org"
                               {::schemas/timeout timeout}))))
      (is (= "$reaction"
             (realize-task
              (sut/send-reaction :client-handle "!room:example.org" ev "🔥"))))
      (is (= "$state-event"
             (realize-task
              (sut/send-state-event :client-handle
                                    "!room:example.org"
                                    state-event
                                    {::schemas/timeout timeout}))))
      (is (= [:client-handle room-opts] (:create-room @calls)))
      (is (= [:client-handle "!room:example.org" "@alice:example.org" timeout]
             (:invite-user @calls)))
      (is (= [:client-handle "!room:example.org" "$event" "🔥" nil]
             (:send-reaction @calls)))
      (is (= [:client-handle "!room:example.org" state-event timeout]
             (:send-state-event @calls))))))

(deftest send-message-returns-a-send-handle-with-transaction-id-and-status-flow-test
  (let [calls         (atom {})
        timeout       (Duration/ofSeconds 5)
        message       (-> (msg/text "pong")
                          (msg/reply-to {::schemas/event-id "$parent"}))
        status-values [{::schemas/transaction-id "txn-123"
                        ::schemas/content        {:body "pong"}}
                       nil]]
    (with-redefs [bridge/send-message
                  (fn [client room-id sent-message bridge-timeout on-success _]
                    (swap! calls assoc :send-message [client room-id sent-message bridge-timeout])
                    (on-success "txn-123")
                    (->StubCloseable (atom 0)))

                  bridge/outbox-message
                  (fn [client room-id transaction-id]
                    (swap! calls assoc :outbox-message [client room-id transaction-id])
                    ::outbox-message-flow)

                  internal/observe-flow
                  (fn [_ kotlin-flow]
                    (is (= ::outbox-message-flow kotlin-flow))
                    (m/observe
                     (fn [emit]
                       (future
                         (doseq [status status-values]
                           (emit status)))
                       (constantly nil))))]
      (let [handle (realize-task
                    (sut/send-message :client-handle
                                      "!room:example.org"
                                      message
                                      {::schemas/timeout timeout}))]
        (is (map? handle))
        (when (map? handle)
          (is (= "txn-123" (::schemas/transaction-id handle)))
          (is (= status-values
                 (collect-values (::schemas/status handle) 2))))))
    (is (= [:client-handle "!room:example.org" message timeout]
           (:send-message @calls)))
    (is (= [:client-handle "!room:example.org" "txn-123"]
           (:outbox-message @calls)))))

(deftest send-message-accepts-and-forwards-rich-message-specs-test
  (let [calls   (atom [])
        timeout (Duration/ofSeconds 5)
        emote   (msg/reply-to
                 (msg/emote "/me waves")
                 {::schemas/event-id "$parent"})
        audio   (msg/reply-to
                 (msg/audio "/tmp/audio/intro.ogg"
                            {::schemas/body       "Intro clip"
                             ::schemas/file-name  "intro.ogg"
                             ::schemas/mime-type  "audio/ogg"
                             ::schemas/size-bytes 1024
                             ::schemas/duration   (Duration/ofSeconds 42)})
                 {::schemas/event-id "$thread"})]
    (with-redefs [bridge/send-message
                  (fn [client room-id sent-message bridge-timeout on-success _]
                    (swap! calls conj [client room-id sent-message bridge-timeout])
                    (on-success "$txn")
                    (->StubCloseable (atom 0)))

                  bridge/outbox-message
                  (fn [_ _ transaction-id]
                    (case transaction-id
                      "$txn" ::outbox-message-flow))

                  internal/observe-flow
                  (fn [_ kotlin-flow]
                    (is (= ::outbox-message-flow kotlin-flow))
                    (m/observe (fn [emit] (future (emit nil)) (constantly nil))))]
      (let [emote-handle (realize-task
                          (sut/send-message :client-handle "!room:example.org" emote
                                            {::schemas/timeout timeout}))
            audio-handle (realize-task
                          (sut/send-message :client-handle "!room:example.org" audio))]
        (is (map? emote-handle))
        (is (map? audio-handle))
        (when (and (map? emote-handle)
                   (map? audio-handle))
          (is (= "$txn" (::schemas/transaction-id emote-handle)))
          (is (= "$txn" (::schemas/transaction-id audio-handle))))))
    (is (= [[:client-handle "!room:example.org" emote timeout]
            [:client-handle "!room:example.org" audio nil]]
           @calls))))

(deftest join-room-task-surface-returns-a-missionary-task-test
  (let [join-room-var        (resolve-var 'ol.trixnity.room 'join-room)
        bridge-join-room-var (resolve-var 'ol.trixnity.internal.bridge 'join-room)
        timeout              (Duration/ofSeconds 5)
        calls                (atom nil)]
    (is (some? join-room-var)
        "ol.trixnity.room/join-room is missing")
    (is (some? bridge-join-room-var)
        "ol.trixnity.internal.bridge/join-room is missing")
    (when (and join-room-var bridge-join-room-var)
      (with-redefs-fn
        {bridge-join-room-var
         (fn [client room-id bridge-timeout on-success _]
           (reset! calls [client room-id bridge-timeout])
           (on-success "!room:example.org")
           (->StubCloseable (atom 0)))}
        #(is (= "!room:example.org"
                (realize-task
                 ((var-get join-room-var)
                  :client-handle
                  "!room:example.org"
                  {::schemas/timeout timeout})))))
      (is (= [:client-handle "!room:example.org" timeout]
             @calls)))))

(deftest join-room-allows-room-aliases-and-rejects-invalid-targets-test
  (let [calls   (atom [])
        timeout (Duration/ofSeconds 5)]
    (with-redefs [bridge/join-room
                  (fn [client room-id-or-alias bridge-timeout on-success _]
                    (swap! calls conj [client room-id-or-alias bridge-timeout])
                    (on-success "!joined:example.org")
                    (->StubCloseable (atom 0)))]
      (is (= "!joined:example.org"
             (realize-task
              (sut/join-room :client-handle "#ops:example.org"
                             {::schemas/timeout timeout}))))
      (is (thrown-with-msg?
           clojure.lang.ExceptionInfo
           #"Schema validation failed"
           (realize-task
            (sut/join-room :client-handle "ops")))))
    (is (= [[:client-handle "#ops:example.org" timeout]]
           @calls))))

(deftest leave-room-task-surface-returns-a-missionary-task-test
  (let [leave-room-var        (resolve-var 'ol.trixnity.room 'leave-room)
        bridge-leave-room-var (resolve-var 'ol.trixnity.internal.bridge
                                           'leave-room)
        timeout               (Duration/ofSeconds 5)
        calls                 (atom [])]
    (is (some? leave-room-var)
        "ol.trixnity.room/leave-room is missing")
    (is (some? bridge-leave-room-var)
        "ol.trixnity.internal.bridge/leave-room is missing")
    (when (every? some? [leave-room-var bridge-leave-room-var])
      (with-redefs-fn
        {bridge-leave-room-var
         (fn [client room-id reason bridge-timeout on-success _]
           (swap! calls conj [client room-id reason bridge-timeout])
           (on-success nil)
           (->StubCloseable (atom 0)))}
        (fn []
          (is (nil?
               (realize-task
                ((var-get leave-room-var)
                 :client-handle
                 "!room:example.org"))))
          (is (nil?
               (realize-task
                ((var-get leave-room-var)
                 :client-handle
                 "!room:example.org"
                 {::schemas/reason  "bye"
                  ::schemas/timeout timeout})))))))
    (is (= [[:client-handle "!room:example.org" nil nil]
            [:client-handle "!room:example.org" "bye" timeout]]
           @calls))))

(deftest set-typing-task-surface-returns-a-missionary-task-test
  (let [set-typing-var        (resolve-var 'ol.trixnity.room 'set-typing)
        bridge-set-typing-var (resolve-var 'ol.trixnity.internal.bridge
                                           'set-typing)
        timeout               (Duration/ofSeconds 5)
        calls                 (atom [])]
    (is (some? set-typing-var)
        "ol.trixnity.room/set-typing is missing")
    (is (some? bridge-set-typing-var)
        "ol.trixnity.internal.bridge/set-typing is missing")
    (when (every? some? [set-typing-var bridge-set-typing-var])
      (with-redefs-fn
        {bridge-set-typing-var
         (fn [client room-id typing? bridge-timeout on-success _]
           (swap! calls conj [client room-id typing? bridge-timeout])
           (on-success nil)
           (->StubCloseable (atom 0)))}
        (fn []
          (is (nil?
               (realize-task
                ((var-get set-typing-var)
                 :client-handle
                 "!room:example.org"
                 true))))
          (is (nil?
               (realize-task
                ((var-get set-typing-var)
                 :client-handle
                 "!room:example.org"
                 false
                 {::schemas/timeout timeout})))))))
    (is (= [[:client-handle "!room:example.org" true nil]
            [:client-handle "!room:example.org" false timeout]]
           @calls))))

(deftest room-task-surface-additions-return-missionary-tasks-test
  (let [leave-room-var            (resolve-var 'ol.trixnity.room 'leave-room)
        bridge-leave-room-var     (resolve-var 'ol.trixnity.internal.bridge
                                               'leave-room)
        forget-room-var           (resolve-var 'ol.trixnity.room 'forget-room)
        bridge-forget-room-var    (resolve-var 'ol.trixnity.internal.bridge
                                               'forget-room)
        redact-event-var          (resolve-var 'ol.trixnity.room 'redact-event)
        bridge-redact-event-var   (resolve-var 'ol.trixnity.internal.bridge
                                               'redact-event)
        cancel-send-message-var   (resolve-var 'ol.trixnity.room
                                               'cancel-send-message)
        bridge-cancel-message-var (resolve-var 'ol.trixnity.internal.bridge
                                               'cancel-send-message)
        retry-send-message-var    (resolve-var 'ol.trixnity.room
                                               'retry-send-message)
        bridge-retry-message-var  (resolve-var 'ol.trixnity.internal.bridge
                                               'retry-send-message)
        fill-timeline-gaps-var    (resolve-var 'ol.trixnity.room
                                               'fill-timeline-gaps)
        bridge-fill-timeline-var  (resolve-var 'ol.trixnity.internal.bridge
                                               'fill-timeline-gaps)
        calls                     (atom [])]
    (is (some? leave-room-var)
        "ol.trixnity.room/leave-room is missing")
    (is (some? bridge-leave-room-var)
        "ol.trixnity.internal.bridge/leave-room is missing")
    (is (some? forget-room-var)
        "ol.trixnity.room/forget-room is missing")
    (is (some? bridge-forget-room-var)
        "ol.trixnity.internal.bridge/forget-room is missing")
    (is (some? redact-event-var)
        "ol.trixnity.room/redact-event is missing")
    (is (some? bridge-redact-event-var)
        "ol.trixnity.internal.bridge/redact-event is missing")
    (is (some? cancel-send-message-var)
        "ol.trixnity.room/cancel-send-message is missing")
    (is (some? bridge-cancel-message-var)
        "ol.trixnity.internal.bridge/cancel-send-message is missing")
    (is (some? retry-send-message-var)
        "ol.trixnity.room/retry-send-message is missing")
    (is (some? bridge-retry-message-var)
        "ol.trixnity.internal.bridge/retry-send-message is missing")
    (is (some? fill-timeline-gaps-var)
        "ol.trixnity.room/fill-timeline-gaps is missing")
    (is (some? bridge-fill-timeline-var)
        "ol.trixnity.internal.bridge/fill-timeline-gaps is missing")
    (when (every? some? [leave-room-var
                         bridge-leave-room-var
                         forget-room-var
                         bridge-forget-room-var
                         redact-event-var
                         bridge-redact-event-var
                         cancel-send-message-var
                         bridge-cancel-message-var
                         retry-send-message-var
                         bridge-retry-message-var
                         fill-timeline-gaps-var
                         bridge-fill-timeline-var])
      (with-redefs-fn
        {bridge-leave-room-var
         (fn [client room-id reason timeout on-success _]
           (swap! calls conj [:leave client room-id reason timeout])
           (on-success nil)
           (->StubCloseable (atom 0)))

         bridge-forget-room-var
         (fn [client room-id force on-success _]
           (swap! calls conj [:forget client room-id force])
           (on-success nil)
           (->StubCloseable (atom 0)))

         bridge-redact-event-var
         (fn [client room-id event-id reason bridge-timeout on-success _]
           (swap! calls conj [:redact client room-id event-id reason bridge-timeout])
           (on-success "$redaction")
           (->StubCloseable (atom 0)))

         bridge-cancel-message-var
         (fn [client room-id transaction-id on-success _]
           (swap! calls conj [:cancel client room-id transaction-id])
           (on-success nil)
           (->StubCloseable (atom 0)))

         bridge-retry-message-var
         (fn [client room-id transaction-id on-success _]
           (swap! calls conj [:retry client room-id transaction-id])
           (on-success nil)
           (->StubCloseable (atom 0)))

         bridge-fill-timeline-var
         (fn [client room-id event-id limit on-success _]
           (swap! calls conj [:fill client room-id event-id limit])
           (on-success nil)
           (->StubCloseable (atom 0)))}
        (fn []
          (is (nil?
               (realize-task
                ((var-get leave-room-var)
                 :client-handle
                 "!room:example.org"))))
          (is (nil?
               (realize-task
                ((var-get leave-room-var)
                 :client-handle
                 "!room:example.org"
                 {::schemas/reason  "later"
                  ::schemas/timeout (Duration/ofSeconds 5)}))))
          (is (nil?
               (realize-task
                ((var-get forget-room-var)
                 :client-handle
                 "!room:example.org"))))
          (is (nil?
               (realize-task
                ((var-get forget-room-var)
                 :client-handle
                 "!room:example.org"
                 {::schemas/force true}))))
          (is (= "$redaction"
                 (realize-task
                  ((var-get redact-event-var)
                   :client-handle
                   "!room:example.org"
                   "$event"))))
          (is (= "$redaction"
                 (realize-task
                  ((var-get redact-event-var)
                   :client-handle
                   "!room:example.org"
                   "$event"
                   {::schemas/reason  "spam"
                    ::schemas/timeout (Duration/ofSeconds 5)}))))
          (is (nil?
               (realize-task
                ((var-get cancel-send-message-var)
                 :client-handle
                 "!room:example.org"
                 "txn-1"))))
          (is (nil?
               (realize-task
                ((var-get retry-send-message-var)
                 :client-handle
                 "!room:example.org"
                 "txn-2"))))
          (is (nil?
               (realize-task
                ((var-get fill-timeline-gaps-var)
                 :client-handle
                 "!room:example.org"
                 "$event"))))
          (is (nil?
               (realize-task
                ((var-get fill-timeline-gaps-var)
                 :client-handle
                 "!room:example.org"
                 "$event"
                 {::schemas/limit 7}))))))
      (is (= [[:leave :client-handle "!room:example.org" nil nil]
              [:leave :client-handle "!room:example.org" "later"
               (Duration/ofSeconds 5)]
              [:forget :client-handle "!room:example.org" false]
              [:forget :client-handle "!room:example.org" true]
              [:redact :client-handle "!room:example.org" "$event" nil nil]
              [:redact :client-handle "!room:example.org" "$event" "spam" (Duration/ofSeconds 5)]
              [:cancel :client-handle "!room:example.org" "txn-1"]
              [:retry :client-handle "!room:example.org" "txn-2"]
              [:fill :client-handle "!room:example.org" "$event" 20]
              [:fill :client-handle "!room:example.org" "$event" 7]]
             @calls)))))

(deftest room-task-surface-additions-validate-options-before-bridge-test
  (let [leave-room-var           (resolve-var 'ol.trixnity.room 'leave-room)
        bridge-leave-room-var    (resolve-var 'ol.trixnity.internal.bridge
                                              'leave-room)
        forget-room-var          (resolve-var 'ol.trixnity.room 'forget-room)
        bridge-forget-room-var   (resolve-var 'ol.trixnity.internal.bridge
                                              'forget-room)
        redact-event-var         (resolve-var 'ol.trixnity.room 'redact-event)
        bridge-redact-event-var  (resolve-var 'ol.trixnity.internal.bridge
                                              'redact-event)
        set-typing-var           (resolve-var 'ol.trixnity.room 'set-typing)
        bridge-set-typing-var    (resolve-var 'ol.trixnity.internal.bridge
                                              'set-typing)
        send-state-event-var     (resolve-var 'ol.trixnity.room 'send-state-event)
        bridge-send-state-var    (resolve-var 'ol.trixnity.internal.bridge
                                              'send-state-event)
        fill-timeline-gaps-var   (resolve-var 'ol.trixnity.room
                                              'fill-timeline-gaps)
        bridge-fill-timeline-var (resolve-var 'ol.trixnity.internal.bridge
                                              'fill-timeline-gaps)
        calls                    (atom [])]
    (is (some? leave-room-var)
        "ol.trixnity.room/leave-room is missing")
    (is (some? bridge-leave-room-var)
        "ol.trixnity.internal.bridge/leave-room is missing")
    (is (some? forget-room-var)
        "ol.trixnity.room/forget-room is missing")
    (is (some? bridge-forget-room-var)
        "ol.trixnity.internal.bridge/forget-room is missing")
    (is (some? redact-event-var)
        "ol.trixnity.room/redact-event is missing")
    (is (some? bridge-redact-event-var)
        "ol.trixnity.internal.bridge/redact-event is missing")
    (is (some? set-typing-var)
        "ol.trixnity.room/set-typing is missing")
    (is (some? bridge-set-typing-var)
        "ol.trixnity.internal.bridge/set-typing is missing")
    (is (some? send-state-event-var)
        "ol.trixnity.room/send-state-event is missing")
    (is (some? bridge-send-state-var)
        "ol.trixnity.internal.bridge/send-state-event is missing")
    (is (some? fill-timeline-gaps-var)
        "ol.trixnity.room/fill-timeline-gaps is missing")
    (is (some? bridge-fill-timeline-var)
        "ol.trixnity.internal.bridge/fill-timeline-gaps is missing")
    (when (every? some? [leave-room-var
                         bridge-leave-room-var
                         forget-room-var
                         bridge-forget-room-var
                         redact-event-var
                         bridge-redact-event-var
                         set-typing-var
                         bridge-set-typing-var
                         send-state-event-var
                         bridge-send-state-var
                         fill-timeline-gaps-var
                         bridge-fill-timeline-var])
      (with-redefs-fn
        {bridge-leave-room-var
         (fn [& _]
           (swap! calls conj :leave)
           (throw (ex-info "bridge should not be called" {})))

         bridge-forget-room-var
         (fn [& _]
           (swap! calls conj :forget)
           (throw (ex-info "bridge should not be called" {})))

         bridge-redact-event-var
         (fn [& _]
           (swap! calls conj :redact)
           (throw (ex-info "bridge should not be called" {})))

         bridge-set-typing-var
         (fn [& _]
           (swap! calls conj :typing)
           (throw (ex-info "bridge should not be called" {})))

         bridge-send-state-var
         (fn [& _]
           (swap! calls conj :send-state)
           (throw (ex-info "bridge should not be called" {})))

         bridge-fill-timeline-var
         (fn [& _]
           (swap! calls conj :fill)
           (throw (ex-info "bridge should not be called" {})))}
        (fn []
          (is (try
                ((var-get leave-room-var)
                 :client-handle
                 "!room:example.org"
                 {::schemas/reason  :bye
                  ::schemas/timeout (Duration/ofSeconds 5)})
                false
                (catch clojure.lang.ExceptionInfo _ true)))
          (is (try
                ((var-get leave-room-var)
                 :client-handle
                 "!room:example.org"
                 {::schemas/reason  "bye"
                  ::schemas/timeout :soon})
                false
                (catch clojure.lang.ExceptionInfo _ true)))
          (is (try
                ((var-get forget-room-var)
                 :client-handle
                 "!room:example.org"
                 {::schemas/force :yes})
                false
                (catch clojure.lang.ExceptionInfo _ true)))
          (is (try
                ((var-get redact-event-var)
                 :client-handle
                 "!room:example.org"
                 "$event"
                 {::schemas/reason [:not-a-string]})
                false
                (catch clojure.lang.ExceptionInfo _ true)))
          (is (try
                ((var-get set-typing-var)
                 :client-handle
                 "!room:example.org"
                 :yes
                 {::schemas/timeout (Duration/ofSeconds 5)})
                false
                (catch clojure.lang.ExceptionInfo _ true)))
          (is (try
                ((var-get set-typing-var)
                 :client-handle
                 "!room:example.org"
                 true
                 {::schemas/timeout :soon})
                false
                (catch clojure.lang.ExceptionInfo _ true)))
          (is (try
                ((var-get send-state-event-var)
                 :client-handle
                 "!room:example.org"
                 {::schemas/type "m.room.avatar"})
                false
                (catch clojure.lang.ExceptionInfo _ true)))
          (is (try
                ((var-get send-state-event-var)
                 :client-handle
                 "!room:example.org"
                 {::schemas/type "m.room.unknown"
                  ::schemas/body "wat"})
                false
                (catch clojure.lang.ExceptionInfo _ true)))
          (is (try
                ((var-get fill-timeline-gaps-var)
                 :client-handle
                 "!room:example.org"
                 "$event"
                 {::schemas/limit 0})
                false
                (catch clojure.lang.ExceptionInfo _ true)))))
      (is (empty? @calls)))))

(deftest room-and-state-surfaces-stay-thin-test
  (let [calls        (atom {})
        room-value   {::schemas/room-id "!room:example.org"}
        typing-value {"!room:example.org" {::schemas/users #{"@alice:example.org"}}}
        state-flow   (m/observe (fn [emit] (future (emit nil) (emit room-value)) (constantly nil)))]
    (with-redefs [bridge/room-by-id
                  (fn [client room-id]
                    (swap! calls assoc :room-by-id [client room-id])
                    ::room-by-id-flow)

                  bridge/rooms
                  (fn [client]
                    (swap! calls assoc :rooms [client])
                    ::rooms-flow)

                  bridge/rooms-flat
                  (fn [client]
                    (swap! calls assoc :rooms-flat [client])
                    ::rooms-flat-flow)

                  bridge/current-users-typing
                  (fn [client]
                    (swap! calls assoc :current-users-typing [client])
                    typing-value)

                  bridge/users-typing-flow
                  (fn [client]
                    (swap! calls assoc :users-typing-flow [client])
                    ::users-typing-flow)

                  bridge/account-data
                  (fn [client room-id event-content-class key]
                    (swap! calls assoc :account-data [client room-id event-content-class key])
                    ::account-data-flow)

                  bridge/state
                  (fn [client room-id event-content-class state-key]
                    (swap! calls assoc :state [client room-id event-content-class state-key])
                    ::state-flow)

                  bridge/all-state
                  (fn [client room-id event-content-class]
                    (swap! calls assoc :all-state [client room-id event-content-class])
                    ::all-state-flow)

                  m/relieve
                  (fn [_ flow] flow)

                  internal/observe-flow
                  (fn [_ kotlin-flow]
                    (case kotlin-flow
                      ::room-by-id-flow state-flow
                      ::rooms-flat-flow (m/observe (fn [emit] (future (emit []) (emit [room-value])) (constantly nil)))
                      ::users-typing-flow (m/observe (fn [emit] (future (emit {}) (emit typing-value)) (constantly nil)))
                      ::account-data-flow (m/observe (fn [emit] (future (emit nil) (emit {::schemas/raw :content})) (constantly nil)))
                      ::state-flow (m/observe (fn [emit] (future (emit nil) (emit {::schemas/state-key ""})) (constantly nil)))))

                  internal/observe-keyed-flow-map
                  (fn [_ kotlin-flow]
                    (case kotlin-flow
                      ::rooms-flow (m/observe (fn [emit] (future (emit {}) (emit {"!room:example.org" :room-flow})) (constantly nil)))
                      ::all-state-flow (m/observe (fn [emit] (future (emit {}) (emit {"" :state-entry-flow})) (constantly nil)))))]
      (is (= [nil room-value]
             (collect-values (sut/get-by-id :client-handle "!room:example.org") 2)))
      (is (= [{} {"!room:example.org" :room-flow}]
             (collect-values (sut/get-all :client-handle) 2)))
      (is (= [[] [room-value]]
             (collect-values (sut/get-all-flat :client-handle) 2)))
      (is (= typing-value
             (sut/current-users-typing :client-handle)))
      (is (= [{} typing-value]
             (collect-values (sut/users-typing :client-handle) 2)))
      (is (= [nil {::schemas/raw :content}]
             (collect-values (sut/get-account-data :client-handle "!room:example.org" EmptyEventContent) 2)))
      (is (= [nil {::schemas/state-key ""}]
             (collect-values (sut/get-state :client-handle "!room:example.org" EmptyEventContent) 2)))
      (is (= [{} {"" :state-entry-flow}]
             (collect-values (sut/get-all-state :client-handle "!room:example.org" EmptyEventContent) 2)))
      (is (= [:client-handle "!room:example.org"] (:room-by-id @calls)))
      (is (= [:client-handle "!room:example.org" EmptyEventContent ""]
             (:account-data @calls)))
      (is (= [:client-handle "!room:example.org" EmptyEventContent ""]
             (:state @calls)))
      (is (= [:client-handle "!room:example.org" EmptyEventContent]
             (:all-state @calls))))))

(deftest room-event-content-class-surfaces-reject-non-trixnity-classes-test
  (is (try
        (sut/get-account-data :client-handle "!room:example.org" String)
        false
        (catch clojure.lang.ExceptionInfo _ true)))
  (is (try
        (sut/get-state :client-handle "!room:example.org" String)
        false
        (catch clojure.lang.ExceptionInfo _ true)))
  (is (try
        (sut/get-all-state :client-handle "!room:example.org" String)
        false
        (catch clojure.lang.ExceptionInfo _ true))))

(deftest outbox-surfaces-cover-all-public-arities-test
  (let [calls        (atom {})
        flat-message {::schemas/transaction-id "txn"}]
    (with-redefs [bridge/outbox
                  (fn [client]
                    (swap! calls assoc :outbox [client])
                    ::outbox-flow)

                  bridge/outbox-flat
                  (fn [client]
                    (swap! calls assoc :outbox-flat [client])
                    ::outbox-flat-flow)

                  bridge/outbox-by-room
                  (fn [client room-id]
                    (swap! calls assoc :outbox-by-room [client room-id])
                    ::outbox-by-room-flow)

                  bridge/outbox-by-room-flat
                  (fn [client room-id]
                    (swap! calls assoc :outbox-by-room-flat [client room-id])
                    ::outbox-by-room-flat-flow)

                  bridge/outbox-message
                  (fn [client room-id transaction-id]
                    (swap! calls assoc :outbox-message [client room-id transaction-id])
                    ::outbox-message-flow)

                  internal/observe-flow
                  (fn [_ kotlin-flow]
                    (case kotlin-flow
                      ::outbox-flat-flow (m/observe (fn [emit] (future (emit []) (emit [flat-message])) (constantly nil)))
                      ::outbox-by-room-flat-flow (m/observe (fn [emit] (future (emit []) (emit [flat-message])) (constantly nil)))
                      ::outbox-message-flow (m/observe (fn [emit] (future (emit nil) (emit flat-message)) (constantly nil)))))

                  internal/observe-flow-list
                  (fn [_ kotlin-flow]
                    (case kotlin-flow
                      ::outbox-flow (m/observe (fn [emit] (future (emit []) (emit [:message-flow])) (constantly nil)))
                      ::outbox-by-room-flow (m/observe (fn [emit] (future (emit []) (emit [:room-message-flow])) (constantly nil)))))]
      (is (= [[] [:message-flow]]
             (collect-values (sut/get-outbox :client-handle) 2)))
      (is (= [[] [flat-message]]
             (collect-values (sut/get-outbox-flat :client-handle) 2)))
      (is (= [[] [:room-message-flow]]
             (collect-values (sut/get-outbox :client-handle "!room:example.org") 2)))
      (is (= [[] [flat-message]]
             (collect-values (sut/get-outbox-flat :client-handle "!room:example.org") 2)))
      (is (= [nil flat-message]
             (collect-values (sut/get-outbox :client-handle "!room:example.org" "txn") 2)))
      (is (= [:client-handle "!room:example.org"] (:outbox-by-room @calls)))
      (is (= [:client-handle "!room:example.org"] (:outbox-by-room-flat @calls)))
      (is (= [:client-handle "!room:example.org" "txn"] (:outbox-message @calls))))))

(deftest timeline-surfaces-cover-selective-nested-and-helper-forms-test
  (let [calls          (atom {})
        timeline-event {::schemas/room-id  "!room:example.org"
                        ::schemas/event-id "$event"
                        ::schemas/raw      :timeline-raw}
        response       (Sync$Response. "next" nil nil nil nil nil nil nil)]
    (with-redefs [bridge/timeline-event
                  (fn [client room-id event-id d f size allow]
                    (swap! calls assoc :timeline-event [client room-id event-id d f size allow])
                    ::timeline-event-flow)

                  bridge/previous-timeline-event
                  (fn [client raw d f size allow]
                    (swap! calls assoc :previous [client raw d f size allow])
                    ::previous-flow)

                  bridge/next-timeline-event
                  (fn [client raw d f size allow]
                    (swap! calls assoc :next [client raw d f size allow])
                    ::next-flow)

                  bridge/last-timeline-event
                  (fn [client room-id d f size allow]
                    (swap! calls assoc :last [client room-id d f size allow])
                    ::last-flow)

                  bridge/response-timeline-events
                  (fn [client response d]
                    (swap! calls assoc :response [client response d])
                    ::response-flow)

                  bridge/timeline-event-chain
                  (fn [client room-id start direction d f size allow min-size max-size]
                    (swap! calls assoc :chain [client room-id start direction d f size allow min-size max-size])
                    ::chain-flow)

                  bridge/last-timeline-events
                  (fn [client room-id d f size allow min-size max-size]
                    (swap! calls assoc :last-chain [client room-id d f size allow min-size max-size])
                    ::last-chain-flow)

                  bridge/timeline-events-list
                  (fn [& args]
                    (swap! calls assoc :list args)
                    ::list-flow)

                  bridge/last-timeline-events-list
                  (fn [& args]
                    (swap! calls assoc :last-list args)
                    ::last-list-flow)

                  bridge/timeline-events-around
                  (fn [& args]
                    (swap! calls assoc :around args)
                    ::around-flow)

                  bridge/timeline-events-from-now-on
                  (fn [client d buffer-size]
                    (swap! calls assoc :from-now-on [client d buffer-size])
                    ::from-now-on-flow)

                  bridge/timeline-event-relations
                  (fn [client room-id event-id relation-type]
                    (swap! calls assoc :relations [client room-id event-id relation-type])
                    ::relations-flow)

                  internal/observe-flow
                  (fn [_ kotlin-flow]
                    (case kotlin-flow
                      ::timeline-event-flow (m/observe (fn [emit] (future (emit nil) (emit timeline-event)) (constantly nil)))
                      ::previous-flow (m/observe (fn [emit] (future (emit nil) (emit timeline-event)) (constantly nil)))
                      ::next-flow (m/observe (fn [emit] (future (emit nil) (emit timeline-event)) (constantly nil)))
                      ::last-flow (m/observe (fn [emit] (future (emit :inner-event-flow)) (constantly nil)))
                      ::response-flow (m/observe (fn [emit] (future (emit timeline-event)) (constantly nil)))
                      ::chain-flow (m/observe (fn [emit] (future (emit :event-flow)) (constantly nil)))
                      ::last-chain-flow (m/observe (fn [emit] (future (emit :chain-flow)) (constantly nil)))
                      ::from-now-on-flow (m/observe (fn [emit] (future (emit timeline-event)) (constantly nil)))
                      ::list-flow (m/observe (fn [emit] (future (emit [:list-event-flow])) (constantly nil)))
                      ::last-list-flow (m/observe (fn [emit] (future (emit [:last-list-event-flow])) (constantly nil)))
                      ::around-flow (m/observe (fn [emit] (future (emit [:around-event-flow])) (constantly nil)))
                      :chain-flow (m/observe (fn [emit] (future (emit :event-flow)) (constantly nil)))
                      :event-flow (m/observe (fn [emit] (future (emit timeline-event)) (constantly nil)))
                      :inner-event-flow (m/observe (fn [emit] (future (emit timeline-event)) (constantly nil)))))

                  internal/observe-flow-list
                  (fn [_ kotlin-flow]
                    (case kotlin-flow
                      ::list-flow (m/observe (fn [emit] (future (emit [:list-event-flow])) (constantly nil)))
                      ::last-list-flow (m/observe (fn [emit] (future (emit [:last-list-event-flow])) (constantly nil)))
                      ::around-flow (m/observe (fn [emit] (future (emit [:around-event-flow])) (constantly nil)))))

                  internal/observe-keyed-flow-map
                  (fn [_ kotlin-flow]
                    (is (= ::relations-flow kotlin-flow))
                    (m/observe (fn [emit] (future (emit nil) (emit {"$related" :relation-flow})) (constantly nil))))]
      (is (= [nil timeline-event]
             (collect-values (sut/get-timeline-event :client-handle "!room:example.org" "$event") 2)))
      (is (= [nil timeline-event]
             (collect-values (sut/get-previous-timeline-event :client-handle timeline-event) 2)))
      (is (= [nil timeline-event]
             (collect-values (sut/get-next-timeline-event :client-handle timeline-event) 2)))
      (is (= [timeline-event]
             (collect-values (first (collect-values (sut/get-last-timeline-event :client-handle "!room:example.org") 1)) 1)))
      (is (= [timeline-event]
             (collect-values (sut/get-timeline-events :client-handle response) 1)))
      (is (= [timeline-event]
             (collect-values (first (collect-values (sut/get-timeline-events :client-handle "!room:example.org" "$event" :backwards) 1)) 1)))
      (let [chain-flow (first (collect-values (sut/get-last-timeline-events :client-handle "!room:example.org") 1))
            event-flow (first (collect-values chain-flow 1))]
        (is (= [timeline-event]
               (collect-values event-flow 1))))
      (is (= [[:list-event-flow]]
             (collect-values (sut/get-timeline-events-list :client-handle "!room:example.org" "$event" :forwards 10 1) 1)))
      (is (= [[:last-list-event-flow]]
             (collect-values (sut/get-last-timeline-events-list :client-handle "!room:example.org" 10 1) 1)))
      (is (= [[:around-event-flow]]
             (collect-values (sut/get-timeline-events-around :client-handle "!room:example.org" "$event" 10 10) 1)))
      (is (= [timeline-event]
             (collect-values (sut/get-timeline-events-from-now-on :client-handle) 1)))
      (is (= [nil {"$related" :relation-flow}]
             (collect-values (sut/get-timeline-event-relations :client-handle "!room:example.org" "$event" "m.annotation") 2)))
      (is (= [:client-handle response nil] (:response @calls)))
      (is (= [:client-handle nil nil] (:from-now-on @calls))))))

(deftest previous-and-next-timeline-event-require-bridgeable-raw-events-test
  (let [previous-called? (atom false)
        next-called?     (atom false)
        timeline-event   {::schemas/room-id  "!room:example.org"
                          ::schemas/event-id "$event"}]
    (with-redefs [bridge/previous-timeline-event
                  (fn [& _]
                    (reset! previous-called? true)
                    ::previous-flow)

                  bridge/next-timeline-event
                  (fn [& _]
                    (reset! next-called? true)
                    ::next-flow)]
      (is (try
            (sut/get-previous-timeline-event :client-handle timeline-event)
            false
            (catch clojure.lang.ExceptionInfo _ true)))
      (is (try
            (sut/get-next-timeline-event :client-handle timeline-event)
            false
            (catch clojure.lang.ExceptionInfo _ true)))
      (is (false? @previous-called?))
      (is (false? @next-called?)))))

(deftest timeline-attachment-accessors-cover-normalized-download-fields-test
  (let [msgtype-var                                                   (resolve-var 'ol.trixnity.event 'msgtype)
        url-var                                                       (resolve-var 'ol.trixnity.event 'url)
        encrypted-file-var                                            (resolve-var 'ol.trixnity.event
                                                                                   'encrypted-file)
        file-name-var                                                 (resolve-var 'ol.trixnity.event 'file-name)
        mime-type-var                                                 (resolve-var 'ol.trixnity.event 'mime-type)
        size-bytes-var                                                (resolve-var 'ol.trixnity.event 'size-bytes)
        duration-var                                                  (resolve-var 'ol.trixnity.event 'duration)
        height-var                                                    (resolve-var 'ol.trixnity.event 'height)
        width-var                                                     (resolve-var 'ol.trixnity.event 'width)
        thumbnail-url-var                                             (resolve-var 'ol.trixnity.event
                                                                                   'thumbnail-url)
        thumbnail-encrypted-file-var                                  (resolve-var 'ol.trixnity.event
                                                                                   'thumbnail-encrypted-file)
        encrypted-file
        {::schemas/url                   "mxc://example.org/encrypted"
         ::schemas/jwk                   {::schemas/jwk-key        "secret"
                                          ::schemas/key-type       "oct"
                                          ::schemas/key-operations #{"encrypt"
                                                                     "decrypt"}
                                          ::schemas/algorithm      "A256CTR"
                                          ::schemas/extractable    true}
         ::schemas/initialization-vector "iv"
         ::schemas/hashes                {"sha256" "hash"}
         ::schemas/version               "v2"}
        timeline-event
        {::schemas/type                     "m.room.message"
         ::schemas/msgtype                  "m.video"
         ::schemas/url                      "mxc://example.org/video"
         ::schemas/encrypted-file           encrypted-file
         ::schemas/file-name                "clip.mp4"
         ::schemas/mime-type                "video/mp4"
         ::schemas/size-bytes               2048
         ::schemas/duration                 (Duration/ofSeconds 9)
         ::schemas/height                   720
         ::schemas/width                    1280
         ::schemas/thumbnail-url            "mxc://example.org/thumb"
         ::schemas/thumbnail-encrypted-file encrypted-file}]
    (is (some? msgtype-var)
        "ol.trixnity.event/msgtype is missing")
    (is (some? url-var)
        "ol.trixnity.event/url is missing")
    (is (some? encrypted-file-var)
        "ol.trixnity.event/encrypted-file is missing")
    (is (some? file-name-var)
        "ol.trixnity.event/file-name is missing")
    (is (some? mime-type-var)
        "ol.trixnity.event/mime-type is missing")
    (is (some? size-bytes-var)
        "ol.trixnity.event/size-bytes is missing")
    (is (some? duration-var)
        "ol.trixnity.event/duration is missing")
    (is (some? height-var)
        "ol.trixnity.event/height is missing")
    (is (some? width-var)
        "ol.trixnity.event/width is missing")
    (is (some? thumbnail-url-var)
        "ol.trixnity.event/thumbnail-url is missing")
    (is (some? thumbnail-encrypted-file-var)
        "ol.trixnity.event/thumbnail-encrypted-file is missing")
    (when (every? some? [msgtype-var
                         url-var
                         encrypted-file-var
                         file-name-var
                         mime-type-var
                         size-bytes-var
                         duration-var
                         height-var
                         width-var
                         thumbnail-url-var
                         thumbnail-encrypted-file-var])
      (with-redefs [bridge/timeline-events-from-now-on
                    (fn [_ _ _]
                      ::from-now-on-flow)

                    internal/observe-flow
                    (fn [_ kotlin-flow]
                      (is (= ::from-now-on-flow kotlin-flow))
                      (m/observe
                       (fn [emit]
                         (future
                           (emit timeline-event))
                         (constantly nil))))]
        (let [event (first (collect-values
                            (sut/get-timeline-events-from-now-on :client-handle)
                            1))]
          (is (= "m.video" ((var-get msgtype-var) event)))
          (is (= "mxc://example.org/video" ((var-get url-var) event)))
          (is (= encrypted-file ((var-get encrypted-file-var) event)))
          (is (= "clip.mp4" ((var-get file-name-var) event)))
          (is (= "video/mp4" ((var-get mime-type-var) event)))
          (is (= 2048 ((var-get size-bytes-var) event)))
          (is (= (Duration/ofSeconds 9) ((var-get duration-var) event)))
          (is (= 720 ((var-get height-var) event)))
          (is (= 1280 ((var-get width-var) event)))
          (is (= "mxc://example.org/thumb" ((var-get thumbnail-url-var) event)))
          (is (= encrypted-file
                 ((var-get thumbnail-encrypted-file-var) event))))))))
