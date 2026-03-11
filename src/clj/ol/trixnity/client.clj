(ns ol.trixnity.client
  (:require
   [clojure.string :as str]
   [ol.trixnity.interop :as interop]
   [ol.trixnity.schemas :as mx])
  (:import
   [java.util.concurrent CompletableFuture]))

(set! *warn-on-reflection* true)

(def ^:private schema-registry
  (mx/registry {}))

(defn- existing-client [config]
  (::mx/client config))

(defn- timeout-request [client opts]
  (cond-> {::mx/client client}
    (::mx/timeout opts) (assoc ::mx/timeout (::mx/timeout opts))))

(defn open!
  "Opens or resumes a `MatrixClient` using the built-in sqlite-backed happy path.

  If `config` already contains a client under `::mx/client`, that client is
  wrapped in a completed future."
  [config]
  (if-let [client (existing-client config)]
    (CompletableFuture/completedFuture client)
    (->> config
         (mx/validate! schema-registry ::mx/OpenClientRequest)
         interop/open-client)))

(defn start-sync!
  "Starts sync for `client` and returns a cancelable `CompletableFuture`."
  [client]
  (interop/start-sync {::mx/client client}))

(defn await-running!
  "Waits for `client` to reach `RUNNING`.

  Supported opts:

  `{::mx/timeout java.time.Duration}`"
  ([client]
   (await-running! client {}))
  ([client opts]
   (interop/await-running (timeout-request client opts))))

(defn stop-sync!
  "Stops sync for `client` and returns a cancelable `CompletableFuture`."
  [client]
  (interop/stop-sync {::mx/client client}))

(defn close!
  "Closes `client` and tears down its bridge-owned coroutine scope."
  [client]
  (interop/close-client {::mx/client client}))

(defn current-user-id
  "Returns the Matrix user id of `client` as a string."
  [client]
  (interop/current-user-id {::mx/client client}))

(defn sync-state
  "Returns the current sync state as a lower-case keyword."
  [client]
  (let [state (interop/sync-state {::mx/client client})]
    (if (keyword? state)
      state
      (-> state str str/lower-case keyword))))
