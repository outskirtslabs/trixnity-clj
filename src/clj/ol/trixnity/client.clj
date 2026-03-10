(ns ol.trixnity.client
  (:require
   [ol.trixnity.interop :as interop]
   [ol.trixnity.schemas :as schemas])
  (:import
   (java.util HashMap)
   (java.util.concurrent BlockingQueue LinkedBlockingQueue)
   (ol.trixnity.bridge
    CreateRoomRequest
    InviteUserRequest
    SendReactionRequest
    SendTextReplyRequest
    StartTimelinePumpRequest
    StopTimelinePumpRequest)))

(declare lookup send-reaction! send-text!)

(defn- ->jmap [payload]
  (HashMap. payload))

(defn- ->start-sync-request [payload]
  {::schemas/client (:client payload)})

(defn- ->start-timeline-pump-request [payload]
  (StartTimelinePumpRequest. (->jmap payload)))

(defn- ->stop-timeline-pump-request [payload]
  (StopTimelinePumpRequest. (->jmap payload)))

(defn- ->create-room-request [payload]
  (CreateRoomRequest. (->jmap payload)))

(defn- ->invite-user-request [payload]
  (InviteUserRequest. (->jmap payload)))

(defn- ->send-text-reply-request [payload]
  (SendTextReplyRequest. (->jmap payload)))

(defn- ->send-reaction-request [payload]
  (SendReactionRequest. (->jmap payload)))

(defn- event-kind [raw-event]
  (let [kind (or (lookup raw-event :kind) (lookup raw-event :type))]
    (case kind
      "m.room.message" :text
      "m.reaction" :reaction
      kind)))

(defn- lookup [m k]
  (or (get m k)
      (let [name' (if (keyword? k) (name k) (str k))]
        (or (get m name')
            (get m (str ":" name'))))))

(defn- with-client [runtime payload]
  (assoc payload :client (:client runtime)))

(defn ensure-room! [runtime payload]
  (interop/create-room-blocking
   (->create-room-request (with-client runtime payload))))

(defn invite-user! [runtime payload]
  (interop/invite-user-blocking
   (->invite-user-request (with-client runtime payload))))

(defn send-text! [runtime payload]
  (interop/send-text-reply-blocking
   (->send-text-reply-request (with-client runtime payload))))

(defn send-reaction! [runtime payload]
  (interop/send-reaction-blocking
   (->send-reaction-request (with-client runtime payload))))

(defn- normalize-event [runtime raw-event]
  (let [kind     (event-kind raw-event)
        room-id  (or (lookup raw-event :room-id) (lookup raw-event :room))
        event-id (or (lookup raw-event :event-id) (lookup raw-event :id))
        base     {:kind     kind
                  :room-id  room-id
                  :sender   (lookup raw-event :sender)
                  :event-id event-id
                  :raw      raw-event}]
    (case kind
      :text
      (assoc base
             :body (or (lookup raw-event :body) (lookup raw-event :text))
             :reply! (fn [body]
                       (send-text! runtime
                                   {:room-id  room-id
                                    :event-id event-id
                                    :body     body})))

      :reaction
      (assoc base
             :key (or (lookup raw-event :key) (lookup raw-event :reaction))
             :react! (fn [key]
                       (send-reaction! runtime
                                       {:room-id  room-id
                                        :event-id event-id
                                        :key      key})))

      base)))

(defn- enqueue-event! [^BlockingQueue events event]
  (.put events event)
  event)

(defn- dispatch-callback! [handlers event]
  (try
    (case (:kind event)
      :text
      (when-let [on-text (:on-text handlers)]
        (on-text event))

      :reaction
      (when-let [on-reaction (:on-reaction handlers)]
        (on-reaction event))

      nil)
    (catch Throwable ex
      (when-let [on-error (:on-error handlers)]
        (on-error {:stage :event-callback
                   :event event
                   :ex    ex})))))

(defn start!
  ([config]
   (start! config {}))
  ([config handlers]
   (let [queue-size   (int (or (:event-queue-size config) 256))
         events       (LinkedBlockingQueue. queue-size)
         runtime*     (atom nil)
         on-event     (fn [raw-event]
                        (let [runtime    @runtime*
                              normalized (normalize-event runtime raw-event)]
                          (enqueue-event! events normalized)
                          (dispatch-callback! handlers normalized)
                          normalized))
         client       (or (when (and (::schemas/store-path config) (::schemas/media-path config))
                            (interop/from-store-blocking config))
                          (interop/login-blocking config))
         _            (interop/start-sync-blocking
                       (->start-sync-request {:client client}))
         base-runtime {:client client
                       :events events}
         _            (reset! runtime* base-runtime)
         timeline     (interop/start-timeline-pump
                       (->start-timeline-pump-request
                        {:client   client
                         :on-event on-event}))
         stop-fn      (fn []
                        (interop/stop-timeline-pump
                         (->stop-timeline-pump-request
                          {:client        client
                           :timeline-pump timeline}))
                        nil)
         runtime      (assoc base-runtime
                             :timeline-pump timeline
                             :stop! stop-fn)]
     (reset! runtime* runtime)
     runtime)))

(defn stop! [runtime]
  ((:stop! runtime)))
