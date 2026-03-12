(ns ol.trixnity.client
  "Matrix client lifecycle, sync control, and client-level state flows.

  ## Upstream Mapping

  This namespace maps to client-wide APIs on Trixnity's
  `de.connect2x.trixnity.client.MatrixClient`.

  The public wrappers here cover:

  - opening or resuming a client with [[open]]
  - sync lifecycle tasks such as [[start-sync]], [[await-running]],
    [[stop-sync]], and [[close]]
  - synchronous `current-*` accessors paired with relieved Missionary flows
    for profile, server data, sync state, login state, and startup state

  Use [[ol.trixnity.repo]] when you want the built-in sqlite4clj-backed
  repository setup, and [[ol.trixnity.room]], [[ol.trixnity.user]],
  [[ol.trixnity.notification]], [[ol.trixnity.verification]], and
  [[ol.trixnity.key]] for service-specific APIs."
  (:require
   [clojure.string :as str]
   [missionary.core :as m]
   [ol.trixnity.internal :as internal]
   [ol.trixnity.internal.bridge :as bridge]
   [ol.trixnity.schemas :as mx]))

(set! *warn-on-reflection* true)

(defn- existing-client [config]
  (get config ::mx/client))

(defn- normalized-keyword [value]
  (cond
    (nil? value) nil
    (keyword? value) value
    :else (-> value str str/lower-case (str/replace "_" "-") keyword)))

(defn open
  "Opens or resumes a `MatrixClient` using the built-in sqlite4clj-backed
  repository configuration.

  If `config` already contains a client under `::mx/client`, the returned task
  resolves to that client immediately."
  [config]
  (if-let [client (existing-client config)]
    (m/sp client)
    (->> config
         (mx/validate! ::mx/OpenClientRequest)
         (internal/suspend-task bridge/open-client))))

(defn start-sync
  "Starts sync for `client` and returns a Missionary task."
  [client]
  (internal/suspend-task bridge/start-sync client))

(defn await-running
  "Waits for `client` to reach `RUNNING`.

  Supported opts:

  `{::mx/timeout java.time.Duration}`"
  ([client]
   (await-running client {}))
  ([client opts]
   (let [opts (mx/validate! ::mx/OneShotOpts opts)]
     (internal/suspend-task bridge/await-running
                            client
                            (get opts ::mx/timeout)))))

(defn stop-sync
  "Stops sync for `client` and returns a Missionary task."
  [client]
  (internal/suspend-task bridge/stop-sync client))

(defn close
  "Closes `client` and tears down its bridge-owned coroutine scope."
  [client]
  (internal/suspend-task bridge/close-client client))

(defn current-user-id
  "Returns the Matrix user id of `client` as a string."
  [client]
  (bridge/current-user-id client))

(defn current-sync-state
  "Returns the current sync state as a lower-case keyword."
  [client]
  (normalized-keyword (bridge/current-sync-state client)))

(defn current-profile
  "Returns the current normalized client profile."
  [client]
  (bridge/current-profile client))

(defn profile
  "Returns a Missionary flow of the current normalized client profile."
  [client]
  (->> (internal/observe-flow client (bridge/profile-flow client))
       (m/relieve {})))

(defn current-server-data
  "Returns the current normalized server data snapshot."
  [client]
  (bridge/current-server-data client))

(defn server-data
  "Returns a Missionary flow of normalized server data snapshots."
  [client]
  (->> (internal/observe-flow client (bridge/server-data-flow client))
       (m/relieve {})))

(defn sync-state
  "Returns a Missionary flow of the current sync state."
  [client]
  (->> (internal/observe-flow client (bridge/sync-state-flow client))
       (m/eduction (map normalized-keyword))
       (m/relieve {})))

(defn current-initial-sync-done
  "Returns whether initial sync has completed."
  [client]
  (boolean (bridge/current-initial-sync-done client)))

(defn initial-sync-done
  "Returns a Missionary flow of initial-sync completion state."
  [client]
  (->> (internal/observe-flow client (bridge/initial-sync-done-flow client))
       (m/relieve {})))

(defn current-login-state
  "Returns the current login state as a lower-case keyword or nil."
  [client]
  (normalized-keyword (bridge/current-login-state client)))

(defn login-state
  "Returns a Missionary flow of lower-case login-state keywords or nil."
  [client]
  (->> (internal/observe-flow client (bridge/login-state-flow client))
       (m/eduction (map normalized-keyword))
       (m/relieve {})))

(defn current-started
  "Returns whether sync has been started for `client`."
  [client]
  (boolean (bridge/current-started client)))

(defn started
  "Returns a Missionary flow of `client` started state."
  [client]
  (->> (internal/observe-flow client (bridge/started-flow client))
       (m/relieve {})))
