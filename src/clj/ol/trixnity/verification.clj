(ns ol.trixnity.verification
  "Verification state snapshots and active verification flows.

  ## Upstream Mapping

  This namespace maps to Trixnity's `VerificationService`.

  The public wrappers here cover:

  - creating device and user verification requests
  - accepting requests, starting and accepting SAS, confirming or rejecting SAS,
    and cancelling active verifications
  - the current active device verification and its relieved flow
  - the current active user verifications and their relieved flow
  - self-verification method discovery

  Use [[ol.trixnity.key]] for trust and key-management APIs that often
  accompany verification workflows."
  (:require
   [missionary.core :as m]
   [ol.trixnity.internal :as internal]
   [ol.trixnity.internal.bridge :as bridge]
   [ol.trixnity.schemas :as mx]))

(set! *warn-on-reflection* true)

(defn- validate-verification-id [verification-id]
  (mx/validate! ::mx/verification-id verification-id))

(defn current-active-device-verification
  "Returns the current active device verification, or nil when none is active."
  [client]
  (bridge/current-active-device-verification client))

(defn active-device-verification
  "Returns a relieved Missionary flow of the current active device verification."
  [client]
  (->> (internal/observe-flow client (bridge/active-device-verification-flow client))
       (m/relieve {})))

(defn current-active-user-verifications
  "Returns the current active user verifications as a vector."
  [client]
  (bridge/current-active-user-verifications client))

(defn active-verification-snapshots
  "Returns active device and user verification snapshots as a vector.

  Device verification, when present, appears first, followed by active user
  verifications in Trixnity order. Each snapshot contains a stable
  `::mx/verification-id` suitable for [[ready!]], [[start-sas!]],
  [[accept-sas!]], [[confirm!]], [[no-match!]], and [[cancel!]]."
  [client]
  (let [device (current-active-device-verification client)]
    (cond-> []
      device (conj device)
      true (into (current-active-user-verifications client)))))

(defn status
  "Returns active verification snapshots.

  Alias for [[active-verification-snapshots]]."
  [client]
  (active-verification-snapshots client))

(defn active-user-verifications
  "Returns a relieved Missionary flow of the current active user verifications."
  [client]
  (->> (internal/observe-flow client (bridge/active-user-verifications-flow client))
       (m/relieve {})))

(defn start-device-verification!
  "Creates a device verification request and returns a Missionary task.

  `user-id` is the target Matrix user id. `device-id` is the target Matrix
  device id. The task resolves to a normalized active-verification snapshot.

  Maps to Trixnity `VerificationService.createDeviceVerificationRequest`."
  [client user-id device-id]
  (mx/validate! ::mx/user-id user-id)
  (mx/validate! ::mx/device-id device-id)
  (internal/suspend-task bridge/start-device-verification
                         client
                         user-id
                         device-id))

(defn start-user-verification!
  "Creates a user verification request and returns a Missionary task.

  The task resolves to a normalized active-verification snapshot. Maps to
  Trixnity `VerificationService.createUserVerificationRequest`."
  [client user-id]
  (mx/validate! ::mx/user-id user-id)
  (internal/suspend-task bridge/start-user-verification client user-id))

(defn get-active-user-verification!
  "Gets or creates an active user verification for a room request event.

  `room-id` and `event-id` identify a Matrix verification request event. The
  task resolves to a normalized active-verification snapshot or nil. Maps to
  Trixnity `VerificationService.getActiveUserVerification`."
  [client room-id event-id]
  (mx/validate! ::mx/room-id room-id)
  (mx/validate! ::mx/event-id event-id)
  (internal/suspend-task bridge/get-active-user-verification
                         client
                         room-id
                         event-id))

(defn ready!
  "Sends `m.key.verification.ready` for an incoming verification request.

  `verification-id` must come from an active-verification snapshot. The task
  resolves to the updated snapshot. Maps to Trixnity
  `ActiveVerificationState.TheirRequest.ready`."
  [client verification-id]
  (validate-verification-id verification-id)
  (internal/suspend-task bridge/ready-verification client verification-id))

(defn start-sas!
  "Starts SAS verification for a ready verification request.

  `verification-id` must come from an active-verification snapshot. The task
  resolves to the updated snapshot. Maps to Trixnity
  `ActiveVerificationState.Ready.start(VerificationMethod.Sas)`."
  [client verification-id]
  (validate-verification-id verification-id)
  (internal/suspend-task bridge/start-sas-verification client verification-id))

(defn accept-sas!
  "Accepts an incoming SAS start event.

  `verification-id` must come from an active-verification snapshot. The task
  resolves to the updated snapshot. Maps to Trixnity
  `ActiveSasVerificationState.TheirSasStart.accept`."
  [client verification-id]
  (validate-verification-id verification-id)
  (internal/suspend-task bridge/accept-sas-verification client verification-id))

(defn accept!
  "Accepts the current incoming verification step.

  `verification-id` must come from an active-verification snapshot. This maps
  to Trixnity's available incoming action for the current state: request
  `ready` or SAS `accept`. Prefer [[ready!]] or [[accept-sas!]] when the
  caller already knows the state."
  [client verification-id]
  (validate-verification-id verification-id)
  (internal/suspend-task bridge/accept-verification client verification-id))

(defn confirm!
  "Confirms that the SAS comparison matches.

  `verification-id` must come from an active-verification snapshot. The task
  resolves to the updated snapshot. Maps to Trixnity
  `ActiveSasVerificationState.ComparisonByUser.match`."
  [client verification-id]
  (validate-verification-id verification-id)
  (internal/suspend-task bridge/confirm-verification client verification-id))

(defn no-match!
  "Rejects the SAS comparison as a mismatch.

  `verification-id` must come from an active-verification snapshot. The task
  resolves to the updated snapshot. Maps to Trixnity
  `ActiveSasVerificationState.ComparisonByUser.noMatch`."
  [client verification-id]
  (validate-verification-id verification-id)
  (internal/suspend-task bridge/no-match-verification client verification-id))

(defn cancel!
  "Cancels an active verification and returns a Missionary task.

  `verification-id` must come from an active-verification snapshot. With
  `reason`, passes that Matrix cancellation reason through to Trixnity."
  ([client verification-id]
   (cancel! client verification-id nil))
  ([client verification-id reason]
   (validate-verification-id verification-id)
   (when reason
     (mx/validate! ::mx/reason reason))
   (internal/suspend-task bridge/cancel-verification
                          client
                          verification-id
                          reason)))

(defn get-self-verification-methods
  "Returns a Missionary flow of available self-verification methods.

  Upstream models this as a state machine that distinguishes unmet
  preconditions, no-cross-signing-yet, already-cross-signed, and available
  self-verification methods."
  [client]
  (internal/observe-flow client (bridge/self-verification-methods client)))
