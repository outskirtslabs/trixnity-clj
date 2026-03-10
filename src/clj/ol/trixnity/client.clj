(ns ol.trixnity.client
  (:require
   [ol.trixnity.interop :as interop]
   [ol.trixnity.schemas :as schemas])
  (:import
   (java.util.concurrent BlockingQueue LinkedBlockingQueue)))

(set! *warn-on-reflection* true)

(declare lookup send-reaction! send-text!)

(defn- ->start-sync-request [payload]
  {::schemas/client (:client payload)})

(defn- ->start-timeline-pump-request [payload]
  {::schemas/client   (:client payload)
   ::schemas/on-event (:on-event payload)})

(defn- ->stop-timeline-pump-request [payload]
  {::schemas/client        (:client payload)
   ::schemas/timeline-pump (:timeline-pump payload)})

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
  (assoc payload ::schemas/client (:client runtime)))

(defn ensure-room! [runtime payload]
  (interop/create-room-blocking
   (assoc (with-client runtime payload)
          ::schemas/room-name (:room-name payload))))

(defn invite-user! [runtime payload]
  (interop/invite-user-blocking
   (assoc (with-client runtime payload)
          ::schemas/room-id (:room-id payload)
          ::schemas/user-id (:user-id payload))))

(defn send-text! [runtime payload]
  (interop/send-text-reply-blocking
   (assoc (with-client runtime payload)
          ::schemas/room-id (:room-id payload)
          ::schemas/event-id (:event-id payload)
          ::schemas/body (:body payload))))

(defn send-reaction! [runtime payload]
  (interop/send-reaction-blocking
   (assoc (with-client runtime payload)
          ::schemas/room-id (:room-id payload)
          ::schemas/event-id (:event-id payload)
          ::schemas/key (:key payload))))

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
         client       (or (when (and (::schemas/database config) (::schemas/media-path config))
                            (interop/from-store-blocking config))
                          (interop/login-with-password-blocking config))
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
