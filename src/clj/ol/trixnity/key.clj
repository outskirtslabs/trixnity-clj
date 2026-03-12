(ns ol.trixnity.key
  "Encryption-key state, backup state, and trust-level queries.

  ## Upstream Mapping

  This namespace maps to Trixnity's `KeyService` and `KeyBackupService`.

  The public wrappers here cover:

  - current and flow-based backup bootstrap state
  - current and flow-based backup-version observation
  - trust-level queries for users, devices, and timeline events
  - device-key and cross-signing-key lookup

  Use [[ol.trixnity.verification]] for active verification workflows and
  [[ol.trixnity.room]] when you need room timeline events to pair with
  trust-level checks."
  (:require
   [clojure.string :as str]
   [missionary.core :as m]
   [ol.trixnity.internal :as internal]
   [ol.trixnity.internal.bridge :as bridge]
   [ol.trixnity.schemas :as mx]))

(set! *warn-on-reflection* true)

(defn current-bootstrap-running
  "Returns whether cross-signing bootstrap is currently running."
  [client]
  (boolean (bridge/current-bootstrap-running client)))

(defn bootstrap-running
  "Returns a Missionary flow of cross-signing bootstrap state."
  [client]
  (->> (internal/observe-flow client (bridge/bootstrap-running-flow client))
       (m/relieve {})))

(defn current-backup-version
  "Returns the current active room-key-backup version, or nil when unavailable."
  [client]
  (bridge/current-backup-version client))

(defn backup-version
  "Returns a Missionary flow of the active room-key-backup version."
  [client]
  (->> (internal/observe-flow client (bridge/backup-version-flow client))
       (m/relieve {})))

(defn get-trust-level
  "Returns a Missionary flow of trust information.

  With two arguments, `user-id` resolves to the trust level of that user.

  With three arguments, the meaning depends on the first id:

  - a `@user:server` value resolves device trust for `device-id`
  - a `!room:server` value resolves the trust level for the device that sent
    the timeline `event-id`"
  ([client user-id]
   (mx/validate! ::mx/user-id user-id)
   (internal/observe-flow client (bridge/user-trust-level client user-id)))
  ([client user-id-or-room-id device-id-or-event-id]
   (if (str/starts-with? user-id-or-room-id "@")
     (do
       (mx/validate! ::mx/user-id user-id-or-room-id)
       (mx/validate! ::mx/device-id device-id-or-event-id)
       (internal/observe-flow client (bridge/device-trust-level client user-id-or-room-id device-id-or-event-id)))
     (do
       (mx/validate! ::mx/room-id user-id-or-room-id)
       (mx/validate! ::mx/event-id device-id-or-event-id)
       (internal/observe-flow client (bridge/timeline-trust-level client user-id-or-room-id device-id-or-event-id))))))

(defn get-device-keys
  "Returns a Missionary flow of device keys for `user-id`."
  [client user-id]
  (mx/validate! ::mx/user-id user-id)
  (internal/observe-flow client (bridge/device-keys-flow client user-id)))

(defn get-cross-signing-keys
  "Returns a Missionary flow of cross-signing keys for `user-id`."
  [client user-id]
  (mx/validate! ::mx/user-id user-id)
  (internal/observe-flow client (bridge/cross-signing-keys-flow client user-id)))
