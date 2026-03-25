(ns ol.trixnity.user
  "Room user state, receipts, presence, and permission checks.

  ## Upstream Mapping

  This namespace wraps the user-oriented room APIs exposed by Trixnity's
  `RoomService`.

  The public wrappers here cover:

  - room member lookup by id or as keyed flow maps
  - per-user receipt and power-level observation
  - permission checks for invites, bans, kicks, redactions, power changes, and
    sending events
  - user presence and global account-data queries

  Use [[ol.trixnity.room]] for room lifecycle and timeline access, and
  [[ol.trixnity.client]] for client-wide state."
  (:require
   [ol.trixnity.internal :as internal]
   [ol.trixnity.internal.bridge :as bridge]
   [ol.trixnity.schemas :as mx]))

(set! *warn-on-reflection* true)

(defn load-members
  "Loads room members for `room-id` and returns a Missionary task.

  Supported opts:

  | key | description
  |-----|-------------
  | `::mx/wait` | When true, wait for the member load to finish before resolving the task (default `true`) |"
  ([client room-id]
   (load-members client room-id {}))
  ([client room-id opts]
   (mx/validate! ::mx/room-id room-id)
   (let [opts (mx/validate! ::mx/LoadMembersOpts opts)]
     (internal/suspend-task bridge/load-members
                            client
                            room-id
                            (get opts ::mx/wait true)))))

(defn get-all
  "Returns a Missionary flow of room members keyed by user id."
  [client room-id]
  (mx/validate! ::mx/room-id room-id)
  (internal/observe-keyed-flow-map client (bridge/user-all client room-id)))

(defn get-by-id
  "Returns a Missionary flow of the room member for `user-id`, or nil when unavailable."
  [client room-id user-id]
  (mx/validate! ::mx/room-id room-id)
  (mx/validate! ::mx/user-id user-id)
  (internal/observe-flow client (bridge/user-by-id client room-id user-id)))

(defn get-all-receipts
  "Returns a Missionary flow of per-user receipt flows keyed by user id."
  [client room-id]
  (mx/validate! ::mx/room-id room-id)
  (internal/observe-keyed-flow-map client (bridge/user-all-receipts client room-id)))

(defn get-receipts-by-id
  "Returns a Missionary flow of receipts for `user-id` in `room-id`."
  [client room-id user-id]
  (mx/validate! ::mx/room-id room-id)
  (mx/validate! ::mx/user-id user-id)
  (internal/observe-flow client (bridge/user-receipts-by-id client room-id user-id)))

(defn get-power-level
  "Returns a Missionary flow of the current power-level view for `user-id`."
  [client room-id user-id]
  (mx/validate! ::mx/room-id room-id)
  (mx/validate! ::mx/user-id user-id)
  (internal/observe-flow client (bridge/user-power-level client room-id user-id)))

(defn can-kick-user
  "Returns a Missionary flow that reports whether the current client can kick `user-id`."
  [client room-id user-id]
  (mx/validate! ::mx/room-id room-id)
  (mx/validate! ::mx/user-id user-id)
  (internal/observe-flow client (bridge/can-kick-user client room-id user-id)))

(defn can-ban-user
  "Returns a Missionary flow that reports whether the current client can ban `user-id`."
  [client room-id user-id]
  (mx/validate! ::mx/room-id room-id)
  (mx/validate! ::mx/user-id user-id)
  (internal/observe-flow client (bridge/can-ban-user client room-id user-id)))

(defn can-unban-user
  "Returns a Missionary flow that reports whether the current client can unban `user-id`."
  [client room-id user-id]
  (mx/validate! ::mx/room-id room-id)
  (mx/validate! ::mx/user-id user-id)
  (internal/observe-flow client (bridge/can-unban-user client room-id user-id)))

(defn can-invite-user
  "Returns a Missionary flow that reports whether the current client can invite `user-id`."
  [client room-id user-id]
  (mx/validate! ::mx/room-id room-id)
  (mx/validate! ::mx/user-id user-id)
  (internal/observe-flow client (bridge/can-invite-user client room-id user-id)))

(defn can-invite
  "Returns a Missionary flow that reports whether the current client can invite users to `room-id`."
  [client room-id]
  (mx/validate! ::mx/room-id room-id)
  (internal/observe-flow client (bridge/can-invite client room-id)))

(defn can-redact-event
  "Returns a Missionary flow that reports whether the current client can redact `event-id`."
  [client room-id event-id]
  (mx/validate! ::mx/room-id room-id)
  (mx/validate! ::mx/event-id event-id)
  (internal/observe-flow client (bridge/can-redact-event client room-id event-id)))

(defn can-set-power-level-to-max
  "Returns a Missionary flow of the maximum power level the current client may assign to `user-id`."
  [client room-id user-id]
  (mx/validate! ::mx/room-id room-id)
  (mx/validate! ::mx/user-id user-id)
  (internal/observe-flow client (bridge/can-set-power-level-to-max client room-id user-id)))

(defn can-send-event
  "Returns a Missionary flow that reports whether the current client can send an event.

  `event-class-or-content` may be either a room-event-content class or a
  concrete room-event-content instance."
  [client room-id event-class-or-content]
  (mx/validate! ::mx/room-id room-id)
  (if (instance? Class event-class-or-content)
    (do
      (mx/validate!
       ::mx/room-event-content-class
       event-class-or-content)
      (internal/observe-flow client (bridge/can-send-event-by-class client room-id event-class-or-content)))
    (do
      (mx/validate!
       ::mx/room-event-content
       event-class-or-content)
      (internal/observe-flow client (bridge/can-send-event-by-content client room-id event-class-or-content)))))

(defn get-presence
  "Returns a Missionary flow of presence data for `user-id`."
  [client user-id]
  (mx/validate! ::mx/user-id user-id)
  (internal/observe-flow client (bridge/user-presence client user-id)))

(defn get-account-data
  "Returns a Missionary flow of global account-data content.

  When `key` is omitted, the empty-string state key is used."
  ([client event-content-class]
   (get-account-data client event-content-class ""))
  ([client event-content-class key]
   (mx/validate!
    ::mx/global-account-data-event-content-class
    event-content-class)
   (mx/validate! ::mx/key key)
   (internal/observe-flow client (bridge/user-account-data client event-content-class key))))

(defn get-direct-chats
  "Returns a Missionary flow of the current `m.direct` mapping.

  The emitted value is a map of Matrix user ids to sets of direct-room ids."
  [client]
  (internal/observe-flow client (bridge/user-direct-chats client)))

(defn set-direct-chats
  "Writes the `m.direct` mapping and returns a Missionary task.

  `mappings` must be a map of Matrix user ids to sets of room ids."
  [client mappings]
  (let [mappings (mx/validate! ::mx/direct-chat-mappings mappings)]
    (internal/suspend-task bridge/set-direct-chats
                           client
                           mappings)))
