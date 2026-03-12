(ns ol.trixnity.notification
  "Notification snapshots and notification update flows.

  ## Upstream Mapping

  This namespace maps to Trixnity's `NotificationService`.

  The public wrappers here cover:

  - observing the full notification set in keyed or flat shapes
  - looking up individual notifications and unread counts
  - consuming live notification updates
  - a deprecated bridge for notification extraction from sync responses

  Use [[ol.trixnity.room]] for room timeline access and
  [[ol.trixnity.client]] for client lifecycle."
  (:require
   [ol.trixnity.internal :as internal]
   [ol.trixnity.internal.bridge :as bridge]
   [ol.trixnity.schemas :as mx])
  (:import
   [de.connect2x.trixnity.clientserverapi.model.sync Sync$Response]))

(set! *warn-on-reflection* true)

(defn ^:deprecated get-notifications
  "Returns deprecated notification extraction flows.

  Prefer [[get-all]], [[get-by-id]], and [[get-all-updates]] for the current
  notification model.

  Supported opts:

  | key                              | description                                                      |
  |----------------------------------|------------------------------------------------------------------|
  | `::mx/decryption-timeout`        | Decryption timeout for derived timeline events                   |
  | `::mx/sync-response-buffer-size` | Number of sync responses buffered while extracting notifications |"
  ([client]
   (get-notifications client {}))
  ([client response-or-opts]
   (if (instance? Sync$Response response-or-opts)
     (get-notifications client response-or-opts {})
     (let [opts (mx/validate! ::mx/NotificationOpts response-or-opts)]
       (internal/observe-flow
        client
        (bridge/notifications
         client
         (internal/duration->millis (::mx/decryption-timeout opts))
         (::mx/sync-response-buffer-size opts))))))
  ([client response opts]
   (mx/validate! ::mx/response response)
   (let [opts (mx/validate! ::mx/TimelineSubscribeOpts opts)]
     (internal/observe-flow
      client
      (bridge/notifications-from-response
       client
       response
       (internal/duration->millis (::mx/decryption-timeout opts)))))))

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
