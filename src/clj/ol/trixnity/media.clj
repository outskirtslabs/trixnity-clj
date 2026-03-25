(ns ol.trixnity.media
  "Generic media upload helpers built on top of Trixnity's staged upload flow.

  Uploads happen in two steps:

  - [[prepare-upload]] stages local bytes in Trixnity's media store and returns
    a prepared upload map containing an `upload://...` cache URI
  - [[upload]] uploads either a prepared upload map or a local path-like source
    and returns a handle exposing upload progress plus the final uploaded media

  The public shapes here stay normalized as namespaced keyword maps so callers
  do not need to work with Kotlin media APIs directly."
  (:require
   [clojure.java.io :as io]
   [missionary.core :as m]
   [ol.trixnity.internal :as internal]
   [ol.trixnity.internal.bridge :as bridge]
   [ol.trixnity.schemas :as mx])
  (:import
   [java.util.concurrent LinkedBlockingQueue]))

(set! *warn-on-reflection* true)

(defn- normalize-source-path [source]
  (cond
    (string? source) source
    (instance? java.io.File source) (.getPath ^java.io.File source)
    (instance? java.nio.file.Path source) (.toString ^java.nio.file.Path source)
    :else source))

(defn- path-metadata [source opts]
  (let [source-path (mx/validate! ::mx/source-path
                                  (normalize-source-path source))
        file        (io/file source-path)
        source-path (.getPath (.getAbsoluteFile file))
        basename    (.getName file)]
    (mx/validate!
     ::mx/PreparedUpload
     (cond-> {::mx/cache-uri   "upload://pending"
              ::mx/source-path source-path
              ::mx/file-name   (or (::mx/file-name opts) basename)
              ::mx/size-bytes  (.length file)}
       (::mx/mime-type opts) (assoc ::mx/mime-type (::mx/mime-type opts))))))

(defn- strip-placeholder-cache-uri [prepared-upload]
  (dissoc prepared-upload ::mx/cache-uri))

(def ^:private flow-complete (Object.))

(defn- replayable-flow-publisher []
  (let [state* (atom {:finished?   false
                      :history     []
                      :next-id     0
                      :subscribers {}})]
    {:publish!
     (fn [value]
       (let [[_ next-state]
             (swap-vals! state*
                         (fn [{:keys [finished?] :as state}]
                           (if finished?
                             state
                             (update state :history conj value))))]
         (when-not (:finished? next-state)
           (doseq [^LinkedBlockingQueue queue (vals (:subscribers next-state))]
             (.offer queue value)))))

     :finish!
     (fn []
       (let [[prev-state _]
             (swap-vals! state*
                         (fn [state]
                           (-> state
                               (assoc :finished? true)
                               (assoc :subscribers {}))))]
         (doseq [^LinkedBlockingQueue queue (vals (:subscribers prev-state))]
           (.offer queue flow-complete))))

     :flow
     (m/observe
      (fn [emit]
        (let [queue         (LinkedBlockingQueue.)
              [prev-state next-state]
              (swap-vals! state*
                          (fn [{:keys [finished? next-id] :as state}]
                            (if finished?
                              state
                              (-> state
                                  (assoc-in [:subscribers next-id] queue)
                                  (update :next-id inc)))))
              subscriber-id (when-not (:finished? prev-state)
                              (:next-id prev-state))
              worker        (future
                              (doseq [value (:history prev-state)]
                                (emit value))
                              (when-not (:finished? prev-state)
                                (loop []
                                  (let [value (.take queue)]
                                    (when-not (identical? flow-complete value)
                                      (emit value)
                                      (recur))))))]
          (fn []
            (when subscriber-id
              (when-let [^LinkedBlockingQueue current-queue
                         (get-in next-state [:subscribers subscriber-id])]
                (swap! state* update :subscribers dissoc subscriber-id)
                (.offer current-queue flow-complete)))
            (future-cancel worker)))))}))

(defn- promise-task [result*]
  (fn [success failure]
    (let [cancelled? (atom false)
          worker     (future
                       (let [{:keys [error value]} @result*]
                         (when-not @cancelled?
                           (if error
                             (failure error)
                             (success value)))))]
      #(do
         (reset! cancelled? true)
         (future-cancel worker)))))

(defn prepare-upload
  "Stages a local media source in Trixnity's media store and returns a
  Missionary task of a prepared upload map.

  `source` may be a string path, `java.io.File`, or `java.nio.file.Path`.

  Supported opts:

  | key | description
  |-----|-------------
  | `::mx/file-name` | Optional logical file name stored in the returned metadata |
  | `::mx/mime-type` | Optional MIME type forwarded to upstream media upload preparation |"
  ([client source]
   (prepare-upload client source {}))
  ([client source opts]
   (let [opts            (mx/validate! ::mx/PrepareUploadOpts opts)
         prepared-upload (strip-placeholder-cache-uri (path-metadata source opts))]
     (m/sp
      (mx/validate!
       ::mx/PreparedUpload
       (assoc prepared-upload
              ::mx/cache-uri
              (m/? (internal/suspend-task bridge/prepare-upload-media
                                          client
                                          (::mx/source-path prepared-upload)
                                          (::mx/mime-type prepared-upload)))))))))

(defn upload
  "Uploads a prepared upload map or local media source and returns a handle map.

  When `source` is path-like, this first stages the file through
  [[prepare-upload]] and then uploads the resulting cache URI.

  Return value:

  - `::mx/result` is a Missionary task that resolves to the uploaded media map
  - `::mx/progress` is a Missionary flow of normalized progress snapshots with
    `::mx/transferred` and optional `::mx/total`

  This intentionally replaces the previous task-only return contract so one
  `upload` call can drive both progress observation and the final upload result
  from the same underlying operation.

  Supported opts:

  | key | description
  |-----|-------------
  | `::mx/file-name` | Optional logical file name when `source` is path-like |
  | `::mx/mime-type` | Optional MIME type when `source` is path-like |
  | `::mx/keep-in-cache` | Keep the staged media in the local cache after upload, defaults to `true` |"
  ([client source]
   (upload client source {}))
  ([client source opts]
   (let [opts            (mx/validate! ::mx/UploadMediaOpts opts)
         prepared-upload (when (map? source)
                           (mx/validate! ::mx/PreparedUpload source))
         progress        (replayable-flow-publisher)
         result*         (promise)]
     (future
       (try
         (let [prepared-upload (or prepared-upload
                                   (m/? (prepare-upload client source opts)))
               upload-result*  (promise)]
           (bridge/upload-media
            client
            (::mx/cache-uri prepared-upload)
            (get opts ::mx/keep-in-cache true)
            (fn [snapshot]
              ((:publish! progress)
               (mx/validate! ::mx/UploadProgress snapshot)))
            (fn [mxc-uri]
              ((:finish! progress))
              (deliver upload-result*
                       {:value
                        (mx/validate!
                         ::mx/UploadedMedia
                         (assoc prepared-upload ::mx/mxc-uri mxc-uri))}))
            (fn [error]
              ((:finish! progress))
              (deliver upload-result* {:error error})))
           (deliver result* @upload-result*))
         (catch Throwable error
           ((:finish! progress))
           (deliver result* {:error error}))))
     {::mx/result   (promise-task result*)
      ::mx/progress (:flow progress)})))
