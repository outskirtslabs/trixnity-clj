(ns ol.trixnity.room
  "Room operations, state queries, and timeline traversal.

  ## Upstream Mapping

  This namespace maps primarily to Trixnity's `RoomService` on
  `de.connect2x.trixnity.client.MatrixClient`.

  The public wrappers here cover three upstream groupings:

  - room mutations such as room creation, invites, messages, reactions, and
    supported room state events
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
  "Creates a room and returns a Missionary task that resolves to the new room id.

  Supported opts:

  | key               | description                                                                                  |
  |-------------------|----------------------------------------------------------------------------------------------|
  | `::mx/room-name`  | Optional room display name.                                                                  |
  | `::mx/topic`      | Optional room topic.                                                                         |
  | `::mx/invite`     | Optional vector of Matrix user ids to invite during creation.                                |
  | `::mx/preset`     | Optional preset keyword, one of `:private-chat`, `:public-chat`, or `:trusted-private-chat`. |
  | `::mx/is-direct`  | Optional direct-message flag for invite membership events.                                   |
  | `::mx/visibility` | Optional room-directory visibility, either `:private` or `:public`.                          |"
  [client opts]
  (let [opts (mx/validate! ::mx/CreateRoomOpts opts)]
    (internal/suspend-task bridge/create-room
                           client
                           opts)))

(defn invite-user
  "Invites `user-id` to `room-id` and returns a Missionary task.

  Supported opts:

  | key | description
  |-----|-------------
  | `::mx/timeout` | Maximum time to wait for the invite request |"
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
  "Joins `room-id-or-alias` and returns a Missionary task.

  Supported opts:

  | key | description
  |-----|-------------
  | `::mx/timeout` | Maximum time to wait for the join request |"
  ([client room-id-or-alias]
   (join-room client room-id-or-alias {}))
  ([client room-id-or-alias opts]
   (mx/validate! ::mx/room-id-or-alias room-id-or-alias)
   (let [opts (mx/validate! ::mx/JoinOpts opts)]
     (internal/suspend-task bridge/join-room
                            client
                            room-id-or-alias
                            (get opts ::mx/timeout)))))

(defn forget-room
  "Forgets `room-id` locally and returns a Missionary task.

  Upstream notes that this is intended for rooms in `LEAVE` membership.

  Supported opts:

  | key | description
  |-----|-------------
  | `::mx/force` | Force forgetting even when the usual upstream preconditions are not met |"
  ([client room-id]
   (forget-room client room-id {}))
  ([client room-id opts]
   (mx/validate! ::mx/room-id room-id)
   (let [opts (mx/validate! ::mx/ForgetRoomOpts opts)]
     (internal/suspend-task bridge/forget-room
                            client
                            room-id
                            (get opts ::mx/force false)))))

(defn send-message
  "Queues `message` for `room-id` and returns a Missionary task of the transaction id.

  Supported opts:

  | key | description
  |-----|-------------
  | `::mx/timeout` | Maximum time to wait for the send operation |"
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
  "Sends a reaction to event `ev` in `room-id` and returns a Missionary task."
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

(defn send-state-event
  "Sends a supported room state event map to `room-id`.

  Supported state-event payloads:

  - `{::mx/type \"m.room.name\", ::mx/name ...}`
  - `{::mx/type \"m.room.topic\", ::mx/topic ...}`
  - `{::mx/type \"m.room.avatar\", ::mx/url ...}`

  `::mx/state-key` is optional on the payload and defaults to the empty string.

  Supported opts:

  | key | description
  |-----|-------------
  | `::mx/timeout` | Maximum time to wait for the send operation |"
  ([client room-id state-event]
   (send-state-event client room-id state-event {}))
  ([client room-id state-event opts]
   (mx/validate! ::mx/room-id room-id)
   (let [state-event (mx/validate! ::mx/StateEventSpec state-event)
         opts        (mx/validate! ::mx/SendOpts opts)]
     (internal/suspend-task bridge/send-state-event
                            client
                            room-id
                            state-event
                            (get opts ::mx/timeout)))))

(defn cancel-send-message
  "Cancels an outbox message identified by `transaction-id` and returns a Missionary task."
  [client room-id transaction-id]
  (mx/validate! ::mx/room-id room-id)
  (mx/validate! ::mx/transaction-id transaction-id)
  (internal/suspend-task bridge/cancel-send-message
                         client
                         room-id
                         transaction-id))

(defn retry-send-message
  "Retries an outbox message identified by `transaction-id` and returns a Missionary task."
  [client room-id transaction-id]
  (mx/validate! ::mx/room-id room-id)
  (mx/validate! ::mx/transaction-id transaction-id)
  (internal/suspend-task bridge/retry-send-message
                         client
                         room-id
                         transaction-id))

(defn get-by-id
  "Returns a Missionary flow of the room for `room-id`, or nil when unavailable."
  [client room-id]
  (mx/validate! ::mx/room-id room-id)
  (internal/observe-flow client (bridge/room-by-id client room-id)))

(defn get-all
  "Returns a Missionary flow of room flows keyed by room id."
  [client]
  (internal/observe-keyed-flow-map client (bridge/rooms client)))

(defn get-all-flat
  "Returns a Missionary flow of flattened room snapshots."
  [client]
  (internal/observe-flow client (bridge/rooms-flat client)))

(defn current-users-typing
  "Returns the current typing-state snapshot keyed by room id."
  [client]
  (bridge/current-users-typing client))

(defn users-typing
  "Returns a relieved Missionary flow of typing-state snapshots keyed by room id."
  [client]
  (->> (internal/observe-flow client (bridge/users-typing-flow client))
       (m/relieve {})))

(defn set-typing
  "Sets the typing status for `room-id` and returns a Missionary task.

  Supported opts:

  | key | description |
  |-----|-------------|
  | `::mx/timeout` | How long the typing notification should remain active |"
  ([client room-id typing?]
   (set-typing client room-id typing? {}))
  ([client room-id typing? opts]
   (mx/validate! ::mx/room-id room-id)
   (mx/validate! ::mx/typing typing?)
   (let [opts (mx/validate! ::mx/SetTypingOpts opts)]
     (internal/suspend-task bridge/set-typing
                            client
                            room-id
                            typing?
                            (get opts ::mx/timeout)))))

(defn get-account-data
  "Returns a Missionary flow of room account-data content.

  When `key` is omitted, the empty-string key is used."
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
  "Returns a Missionary flow of room state for `event-content-class`.

  When `state-key` is omitted, the empty-string state key is used."
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
  "Returns a Missionary flow of state-event flows keyed by state key."
  [client room-id event-content-class]
  (mx/validate! ::mx/room-id room-id)
  (mx/validate!
   ::mx/state-event-content-class
   event-content-class)
  (internal/observe-keyed-flow-map
   client
   (bridge/all-state client room-id event-content-class)))

(defn get-outbox
  "Returns Missionary flows over room outbox state.

  Arities:

  - `(get-outbox client)` returns all outbox entries as a list of inner flows
  - `(get-outbox client room-id)` scopes that list to one room
  - `(get-outbox client room-id transaction-id)` returns the single outbox
    entry flow for that transaction id"
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
  "Returns flattened Missionary flows over room outbox state.

  With `room-id`, scopes the flattened outbox view to a single room."
  ([client]
   (internal/observe-flow client (bridge/outbox-flat client)))
  ([client room-id]
   (mx/validate! ::mx/room-id room-id)
   (internal/observe-flow client (bridge/outbox-by-room-flat client room-id))))

(defn fill-timeline-gaps
  "Fills timeline gaps around `event-id` in `room-id` and returns a Missionary task.

  Supported opts:

  | key | description
  |-----|-------------
  | `::mx/limit` | Maximum number of events to request while filling gaps (default `20`) |"
  ([client room-id event-id]
   (fill-timeline-gaps client room-id event-id {}))
  ([client room-id event-id opts]
   (mx/validate! ::mx/room-id room-id)
   (mx/validate! ::mx/event-id event-id)
   (let [opts (mx/validate! ::mx/FillTimelineGapsOpts opts)]
     (internal/suspend-task bridge/fill-timeline-gaps
                            client
                            room-id
                            event-id
                            (long (get opts ::mx/limit 20))))))

(defn get-timeline-event
  "Returns a Missionary flow of the timeline event for `event-id`.

  Upstream notes that this lookup may traverse locally stored events and fill
  remote gaps when the event is not available locally.

  Supported opts:

  | key | description
  |-----|-------------
  | `::mx/decryption-timeout` | Timeout used while decrypting timeline events
  | `::mx/fetch-timeout` | Timeout for remote fetches when the event is missing locally
  | `::mx/fetch-size` | Maximum number of events fetched from the server at once
  | `::mx/allow-replace-content` | Replace event content when an `m.replace` relation is present |"
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
  "Returns a Missionary flow of the previous timeline event relative to `timeline-event`.

  Returns nil when upstream cannot traverse backward from the supplied event."
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
  "Returns a Missionary flow of the next timeline event relative to `timeline-event`.

  Returns nil when upstream cannot traverse forward from the supplied event."
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
  "Returns a Missionary outer flow whose values are flows of the latest timeline event."
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
  "Returns Missionary flows over timeline events.

  Arities:

  - `(get-timeline-events client response opts)` extracts timeline events from
    a sync `response`
  - `(get-timeline-events client room-id start-from direction opts)` traverses
    a room timeline from `start-from` in `:backwards` or `:forwards`

  The room traversal arity follows upstream behavior: it emits flows of events,
  may fetch missing events from the server, and can be bounded with
  `::mx/min-size` and `::mx/max-size`."
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
  "Returns a Missionary flow whose values are flows of flows for the latest timeline events.

  This mirrors upstream's nested-flow shape for continuously updated room-end
  traversal."
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
  "Returns a Missionary flow of timeline-event lists starting from `start-from`.

  `max-size` and `min-size` bound the list-shaped traversal directly."
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
  "Returns a Missionary flow of the latest timeline events as lists."
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
  "Returns a Missionary flow of timeline-event lists centered around `start-from`."
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
  "Returns a Missionary flow of timeline events received after subscription starts.

  Upstream notes that timeline gaps are not filled automatically for this live
  stream.

  Supported opts:

  | key | description
  |-----|-------------
  | `::mx/decryption-timeout` | Timeout used while decrypting live events
  | `::mx/sync-response-buffer-size` | Number of sync responses buffered while events are consumed |"
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
  "Returns a Missionary flow of related timeline-event flows keyed by related event id."
  [client room-id event-id relation-type]
  (mx/validate! ::mx/room-id room-id)
  (mx/validate! ::mx/event-id event-id)
  (mx/validate! ::mx/relation-type relation-type)
  (internal/observe-keyed-flow-map
   client
   (bridge/timeline-event-relations client room-id event-id relation-type)))
