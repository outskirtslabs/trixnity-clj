(ns ol.trixnity.verification
  "Verification state snapshots and active verification flows.

  ## Upstream Mapping

  This namespace maps to Trixnity's `VerificationService`.

  The public wrappers here cover:

  - the current active device verification and its relieved flow
  - the current active user verifications and their relieved flow
  - self-verification method discovery

  Use [[ol.trixnity.key]] for trust and key-management APIs that often
  accompany verification workflows."
  (:require
   [missionary.core :as m]
   [ol.trixnity.internal :as internal]
   [ol.trixnity.internal.bridge :as bridge]))

(set! *warn-on-reflection* true)

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

(defn active-user-verifications
  "Returns a relieved Missionary flow of the current active user verifications."
  [client]
  (->> (internal/observe-flow client (bridge/active-user-verifications-flow client))
       (m/relieve {})))

(defn get-self-verification-methods
  "Returns a Missionary flow of available self-verification methods.

  Upstream models this as a state machine that distinguishes unmet
  preconditions, no-cross-signing-yet, already-cross-signed, and available
  self-verification methods."
  [client]
  (internal/observe-flow client (bridge/self-verification-methods client)))
