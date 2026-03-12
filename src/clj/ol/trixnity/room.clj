(ns ol.trixnity.room
  "Room operations, state queries, and timeline traversal.

  ## Upstream Mapping

  This namespace maps primarily to Trixnity's `RoomService` on
  `de.connect2x.trixnity.client.MatrixClient`.

  The public wrappers here cover three upstream groupings:

  - room mutations such as room creation, invites, messages, and reactions
  - room observation and state operations such as `getById`, `getAll`,
    `getAccountData`, `getState`, and `getOutbox`
  - room-scoped timeline lookup and traversal helpers exposed through the
    room service and its timeline helpers

  Use [[ol.trixnity.user]] for `UserService` APIs and
  [[ol.trixnity.notification]], [[ol.trixnity.verification]], and
  [[ol.trixnity.key]] for the other non-room service mappings."
  (:require
   [missionary.core :as m]
   [ol.trixnity.event :as event]
   [ol.trixnity.internal :as internal]
   [ol.trixnity.internal.bridge :as bridge]
   [ol.trixnity.schemas :as mx]))

(set! *warn-on-reflection* true)

(defn- timeline-event-args [opts]
  (let [opts (mx/validate! ::mx/TimelineEventOpts opts)]
    {:decryption-timeout-ms (internal/duration->millis (::mx/decryption-timeout opts))
     :fetch-timeout-ms      (internal/duration->millis (::mx/fetch-timeout opts))
     :fetch-size            (::mx/fetch-size opts)
     :allow-replace-content (::mx/allow-replace-content opts)}))

(defn- timeline-events-args [opts]
  (merge (timeline-event-args opts)
         (let [opts (mx/validate! ::mx/TimelineEventsOpts opts)]
           {:min-size (::mx/min-size opts)
            :max-size (::mx/max-size opts)})))

(defn- observe-flow-of-flow [client kotlin-outer-flow]
  (m/eduction
   (map (fn [kotlin-inner-flow]
          (when kotlin-inner-flow
            (internal/observe-flow client kotlin-inner-flow))))
   (internal/observe-flow client kotlin-outer-flow)))

(defn- observe-flow-of-flow-of-flow [client kotlin-outer-flow]
  (m/eduction
   (map (fn [kotlin-middle-flow]
          (when kotlin-middle-flow
            (observe-flow-of-flow client kotlin-middle-flow))))
   (internal/observe-flow client kotlin-outer-flow)))

(defn- timeline-event-raw [timeline-event]
  (::mx/raw (mx/validate!
             ::mx/BridgeableTimelineEvent
             timeline-event)))

(defn create-room
  [client opts]
  (let [opts (mx/validate! ::mx/CreateRoomOpts opts)]
    (internal/suspend-task bridge/create-room
                           client
                           (get opts ::mx/room-name))))

(defn invite-user
  ([client room-id user-id]
   (invite-user client room-id user-id {}))
  ([client room-id user-id opts]
   (mx/validate! ::mx/room-id room-id)
   (mx/validate! ::mx/user-id user-id)
   (let [opts (mx/validate! ::mx/InviteOpts opts)]
     (internal/suspend-task bridge/invite-user
                            client
                            room-id
                            user-id
                            (get opts ::mx/timeout)))))

(defn join-room
  ([client room-id]
   (join-room client room-id {}))
  ([client room-id opts]
   (mx/validate! ::mx/room-id room-id)
   (let [opts (mx/validate! ::mx/JoinOpts opts)]
     (internal/suspend-task bridge/join-room
                            client
                            room-id
                            (get opts ::mx/timeout)))))

(defn send-message
  ([client room-id message]
   (send-message client room-id message {}))
  ([client room-id message opts]
   (mx/validate! ::mx/room-id room-id)
   (let [message (mx/validate! ::mx/MessageSpec message)
         opts    (mx/validate! ::mx/SendOpts opts)]
     (internal/suspend-task bridge/send-message
                            client
                            room-id
                            message
                            (get opts ::mx/timeout)))))

(defn send-reaction
  ([client room-id ev key]
   (send-reaction client room-id ev key {}))
  ([client room-id ev key opts]
   (mx/validate! ::mx/room-id room-id)
   (mx/validate! ::mx/Event ev)
   (mx/validate! ::mx/key key)
   (let [opts (mx/validate! ::mx/SendOpts opts)]
     (internal/suspend-task bridge/send-reaction
                            client
                            room-id
                            (event/event-id ev)
                            key
                            (get opts ::mx/timeout)))))

(defn get-by-id
  [client room-id]
  (mx/validate! ::mx/room-id room-id)
  (internal/observe-flow client (bridge/room-by-id client room-id)))

(defn get-all
  [client]
  (internal/observe-keyed-flow-map client (bridge/rooms client)))

(defn get-all-flat
  [client]
  (internal/observe-flow client (bridge/rooms-flat client)))

(defn current-users-typing
  [client]
  (bridge/current-users-typing client))

(defn users-typing
  [client]
  (->> (internal/observe-flow client (bridge/users-typing-flow client))
       (m/relieve {})))

(defn get-account-data
  ([client room-id event-content-class]
   (get-account-data client room-id event-content-class ""))
  ([client room-id event-content-class key]
   (mx/validate! ::mx/room-id room-id)
   (mx/validate!
    ::mx/room-account-data-event-content-class
    event-content-class)
   (mx/validate! ::mx/key key)
   (internal/observe-flow
    client
    (bridge/account-data client room-id event-content-class key))))

(defn get-state
  ([client room-id event-content-class]
   (get-state client room-id event-content-class ""))
  ([client room-id event-content-class state-key]
   (mx/validate! ::mx/room-id room-id)
   (mx/validate!
    ::mx/state-event-content-class
    event-content-class)
   (mx/validate! ::mx/state-key state-key)
   (internal/observe-flow
    client
    (bridge/state client room-id event-content-class state-key))))

(defn get-all-state
  [client room-id event-content-class]
  (mx/validate! ::mx/room-id room-id)
  (mx/validate!
   ::mx/state-event-content-class
   event-content-class)
  (internal/observe-keyed-flow-map
   client
   (bridge/all-state client room-id event-content-class)))

(defn get-outbox
  ([client]
   (internal/observe-flow-list client (bridge/outbox client)))
  ([client room-id]
   (mx/validate! ::mx/room-id room-id)
   (internal/observe-flow-list client (bridge/outbox-by-room client room-id)))
  ([client room-id transaction-id]
   (mx/validate! ::mx/room-id room-id)
   (mx/validate! ::mx/transaction-id transaction-id)
   (internal/observe-flow client (bridge/outbox-message client room-id transaction-id))))

(defn get-outbox-flat
  ([client]
   (internal/observe-flow client (bridge/outbox-flat client)))
  ([client room-id]
   (mx/validate! ::mx/room-id room-id)
   (internal/observe-flow client (bridge/outbox-by-room-flat client room-id))))

(defn get-timeline-event
  ([client room-id event-id]
   (get-timeline-event client room-id event-id {}))
  ([client room-id event-id opts]
   (mx/validate! ::mx/room-id room-id)
   (mx/validate! ::mx/event-id event-id)
   (let [{:keys [decryption-timeout-ms fetch-timeout-ms fetch-size allow-replace-content]}
         (timeline-event-args opts)]
     (internal/observe-flow
      client
      (bridge/timeline-event
       client
       room-id
       event-id
       decryption-timeout-ms
       fetch-timeout-ms
       fetch-size
       allow-replace-content)))))

(defn get-previous-timeline-event
  ([client timeline-event]
   (get-previous-timeline-event client timeline-event {}))
  ([client timeline-event opts]
   (let [{:keys [decryption-timeout-ms fetch-timeout-ms fetch-size allow-replace-content]}
         (timeline-event-args opts)]
     (some->> (bridge/previous-timeline-event
               client
               (timeline-event-raw timeline-event)
               decryption-timeout-ms
               fetch-timeout-ms
               fetch-size
               allow-replace-content)
              (internal/observe-flow client)))))

(defn get-next-timeline-event
  ([client timeline-event]
   (get-next-timeline-event client timeline-event {}))
  ([client timeline-event opts]
   (let [{:keys [decryption-timeout-ms fetch-timeout-ms fetch-size allow-replace-content]}
         (timeline-event-args opts)]
     (some->> (bridge/next-timeline-event
               client
               (timeline-event-raw timeline-event)
               decryption-timeout-ms
               fetch-timeout-ms
               fetch-size
               allow-replace-content)
              (internal/observe-flow client)))))

(defn get-last-timeline-event
  ([client room-id]
   (get-last-timeline-event client room-id {}))
  ([client room-id opts]
   (mx/validate! ::mx/room-id room-id)
   (let [{:keys [decryption-timeout-ms fetch-timeout-ms fetch-size allow-replace-content]}
         (timeline-event-args opts)]
     (observe-flow-of-flow
      client
      (bridge/last-timeline-event
       client
       room-id
       decryption-timeout-ms
       fetch-timeout-ms
       fetch-size
       allow-replace-content)))))

(defn get-timeline-events
  ([client response]
   (get-timeline-events client response {}))
  ([client response opts]
   (mx/validate! ::mx/response response)
   (let [opts                                                       (mx/validate! ::mx/TimelineSubscribeOpts opts)
         decryption-timeout-ms
         (internal/duration->millis (::mx/decryption-timeout opts))]
     (internal/observe-flow
      client
      (bridge/response-timeline-events client response decryption-timeout-ms))))
  ([client room-id start-from direction]
   (get-timeline-events client room-id start-from direction {}))
  ([client room-id start-from direction opts]
   (let [{:keys [decryption-timeout-ms fetch-timeout-ms fetch-size allow-replace-content min-size max-size]}
         (timeline-events-args opts)]
     (mx/validate! ::mx/room-id room-id)
     (mx/validate! ::mx/event-id start-from)
     (mx/validate! ::mx/direction direction)
     (observe-flow-of-flow
      client
      (bridge/timeline-event-chain
       client
       room-id
       start-from
       (name direction)
       decryption-timeout-ms
       fetch-timeout-ms
       fetch-size
       allow-replace-content
       min-size
       max-size)))))

(defn get-last-timeline-events
  ([client room-id]
   (get-last-timeline-events client room-id {}))
  ([client room-id opts]
   (mx/validate! ::mx/room-id room-id)
   (let [{:keys [decryption-timeout-ms fetch-timeout-ms fetch-size allow-replace-content min-size max-size]}
         (timeline-events-args opts)]
     (observe-flow-of-flow-of-flow
      client
      (bridge/last-timeline-events
       client
       room-id
       decryption-timeout-ms
       fetch-timeout-ms
       fetch-size
       allow-replace-content
       min-size
       max-size)))))

(defn get-timeline-events-list
  ([client room-id start-from direction max-size min-size]
   (get-timeline-events-list client room-id start-from direction max-size min-size {}))
  ([client room-id start-from direction max-size min-size opts]
   (mx/validate! ::mx/room-id room-id)
   (mx/validate! ::mx/event-id start-from)
   (mx/validate! ::mx/direction direction)
   (mx/validate! ::mx/max-size max-size)
   (mx/validate! ::mx/min-size min-size)
   (let [{:keys [decryption-timeout-ms fetch-timeout-ms fetch-size allow-replace-content]}
         (timeline-event-args opts)]
     (internal/observe-flow-list
      client
      (bridge/timeline-events-list
       client
       room-id
       start-from
       (name direction)
       max-size
       min-size
       decryption-timeout-ms
       fetch-timeout-ms
       fetch-size
       allow-replace-content)))))

(defn get-last-timeline-events-list
  ([client room-id max-size min-size]
   (get-last-timeline-events-list client room-id max-size min-size {}))
  ([client room-id max-size min-size opts]
   (mx/validate! ::mx/room-id room-id)
   (mx/validate! ::mx/max-size max-size)
   (mx/validate! ::mx/min-size min-size)
   (let [{:keys [decryption-timeout-ms fetch-timeout-ms fetch-size allow-replace-content]}
         (timeline-event-args opts)]
     (internal/observe-flow-list
      client
      (bridge/last-timeline-events-list
       client
       room-id
       max-size
       min-size
       decryption-timeout-ms
       fetch-timeout-ms
       fetch-size
       allow-replace-content)))))

(defn get-timeline-events-around
  ([client room-id start-from max-size-before max-size-after]
   (get-timeline-events-around client room-id start-from max-size-before max-size-after {}))
  ([client room-id start-from max-size-before max-size-after opts]
   (mx/validate! ::mx/room-id room-id)
   (mx/validate! ::mx/event-id start-from)
   (mx/validate! ::mx/max-size max-size-before)
   (mx/validate! ::mx/max-size max-size-after)
   (let [{:keys [decryption-timeout-ms fetch-timeout-ms fetch-size allow-replace-content]}
         (timeline-event-args opts)]
     (internal/observe-flow-list
      client
      (bridge/timeline-events-around
       client
       room-id
       start-from
       max-size-before
       max-size-after
       decryption-timeout-ms
       fetch-timeout-ms
       fetch-size
       allow-replace-content)))))

(defn get-timeline-events-from-now-on
  ([client]
   (get-timeline-events-from-now-on client {}))
  ([client opts]
   (let [opts (mx/validate! ::mx/TimelineSubscribeOpts opts)]
     (internal/observe-flow
      client
      (bridge/timeline-events-from-now-on
       client
       (internal/duration->millis (::mx/decryption-timeout opts))
       (::mx/sync-response-buffer-size opts))))))

(defn get-timeline-event-relations
  [client room-id event-id relation-type]
  (mx/validate! ::mx/room-id room-id)
  (mx/validate! ::mx/event-id event-id)
  (mx/validate! ::mx/relation-type relation-type)
  (internal/observe-keyed-flow-map
   client
   (bridge/timeline-event-relations client room-id event-id relation-type)))
