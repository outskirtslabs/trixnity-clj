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
  [client]
  (internal/observe-flow-list client (bridge/notification-all client)))

(defn get-all-flat
  [client]
  (internal/observe-flow client (bridge/notification-all-flat client)))

(defn get-by-id
  [client id]
  (mx/validate! ::mx/id id)
  (internal/observe-flow client (bridge/notification-by-id client id)))

(defn get-count
  ([client]
   (internal/observe-flow client (bridge/notification-count client)))
  ([client room-id]
   (mx/validate! ::mx/room-id room-id)
   (internal/observe-flow client (bridge/notification-count client room-id))))

(defn is-unread
  [client room-id]
  (mx/validate! ::mx/room-id room-id)
  (internal/observe-flow client (bridge/notification-unread client room-id)))

(defn get-all-updates
  [client]
  (internal/observe-flow client (bridge/notification-all-updates client)))
