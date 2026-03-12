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
  [client]
  (bridge/current-active-device-verification client))

(defn active-device-verification
  [client]
  (->> (internal/observe-flow client (bridge/active-device-verification-flow client))
       (m/relieve {})))

(defn current-active-user-verifications
  [client]
  (bridge/current-active-user-verifications client))

(defn active-user-verifications
  [client]
  (->> (internal/observe-flow client (bridge/active-user-verifications-flow client))
       (m/relieve {})))

(defn get-self-verification-methods
  [client]
  (internal/observe-flow client (bridge/self-verification-methods client)))
