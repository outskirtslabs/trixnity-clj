(ns ol.trixnity.notification
  "Notification snapshots and notification update flows.

  ## Upstream Mapping

  This namespace maps to Trixnity's `NotificationService`.

  The public wrappers here cover:

  - observing the full notification set in keyed or flat shapes
  - looking up individual notifications and unread counts
  - consuming live notification updates

  Use [[ol.trixnity.room]] for room timeline access and
  [[ol.trixnity.client]] for client lifecycle."
  (:require
   [ol.trixnity.internal :as internal]
   [ol.trixnity.internal.bridge :as bridge]
   [ol.trixnity.schemas :as mx]))

(set! *warn-on-reflection* true)

(defn get-all
  "Returns a Missionary flow of the current notifications as a list of inner flows."
  [client]
  (internal/observe-flow-list client (bridge/notification-all client)))

(defn get-all-flat
  "Returns a Missionary flow of flattened notification snapshots."
  [client]
  (internal/observe-flow client (bridge/notification-all-flat client)))

(defn get-by-id
  "Returns a Missionary flow of the notification with `id`, or nil when unavailable."
  [client id]
  (mx/validate! ::mx/id id)
  (internal/observe-flow client (bridge/notification-by-id client id)))

(defn get-count
  "Returns a Missionary flow of notification counts.

  With one argument, returns the total count across all rooms.
  With `room-id`, returns the count for that room."
  ([client]
   (internal/observe-flow client (bridge/notification-count client)))
  ([client room-id]
   (mx/validate! ::mx/room-id room-id)
   (internal/observe-flow client (bridge/notification-count client room-id))))

(defn is-unread
  "Returns a Missionary flow that is true when `room-id` is considered unread."
  [client room-id]
  (mx/validate! ::mx/room-id room-id)
  (internal/observe-flow client (bridge/notification-unread client room-id)))

(defn mark-read
  "Marks `room-id` as read through `event-id` and returns a Missionary task.

  This advances both the room's read markers and clears explicit unread state."
  [client room-id event-id]
  (mx/validate! ::mx/room-id room-id)
  (mx/validate! ::mx/event-id event-id)
  (internal/suspend-task bridge/notification-mark-read
                         client
                         room-id
                         event-id))

(defn mark-unread
  "Marks `room-id` as unread and returns a Missionary task."
  [client room-id]
  (mx/validate! ::mx/room-id room-id)
  (internal/suspend-task bridge/notification-mark-unread
                         client
                         room-id))

(defn dismiss
  "Marks the notification with `id` as dismissed and returns a Missionary task."
  [client id]
  (mx/validate! ::mx/id id)
  (internal/suspend-task bridge/notification-dismiss client id))

(defn dismiss-all
  "Dismisses all notifications and returns a Missionary task."
  [client]
  (internal/suspend-task bridge/notification-dismiss-all client))

(defn get-all-updates
  "Returns a Missionary flow of notification updates.

  Upstream notes that this stream should not be buffered because consumed
  updates are removed from the backing store as new values are requested."
  [client]
  (internal/observe-flow client (bridge/notification-all-updates client)))
