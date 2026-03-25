(ns ol.trixnity.media
  "Generic media upload and download helpers built on top of Trixnity's media
  service.

  Uploads happen in two steps:

  - [[prepare-upload]] stages local bytes in Trixnity's media store and returns
    a prepared upload map containing an `upload://...` cache URI
  - [[upload]] uploads either a prepared upload map or a local path-like source
    and returns a handle exposing upload progress plus the final uploaded media
  - [[get-media]], [[get-encrypted-media]], and [[get-thumbnail]] download media
    as normalized handle maps carrying a JVM `InputStream`

  Download selection follows the normalized event data:

  - use [[get-media]] for plain `::mx/url` attachment references
  - use [[get-encrypted-media]] for `::mx/encrypted-file` or
    `::mx/thumbnail-encrypted-file`
  - use [[get-media]] or [[get-encrypted-media]] for event-provided thumbnail
    references already present on the event
  - use [[get-thumbnail]] only when asking the homeserver to generate a new
    thumbnail for an MXC URI at explicit dimensions
  - use [[temporary-file]] only when a filesystem path is required for an
    existing media handle

  The public shapes here stay normalized as namespaced keyword maps so callers
  do not need to work with Kotlin media APIs directly.

  Resolved download handles all share the same shape:

  ```clojure
  {::mx/input-stream <java.io.InputStream>
   ::mx/raw          <opaque upstream platform media>}
  ```

  `::mx/input-stream` is the supported happy path. `::mx/raw` exists only so
  [[temporary-file]] can compose on top of an already fetched handle."
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

(defrecord TemporaryMediaFile [path raw]
  java.io.Closeable
  (close [_]
    (bridge/delete-media-temporary-file raw)))

(defn- deferred-task [thunk]
  (let [result*  (promise)
        started? (atom false)]
    (fn [success failure]
      (when (compare-and-set! started? false true)
        (future
          (deliver result*
                   (try
                     {:value (thunk)}
                     (catch Throwable error
                       {:error error})))))
      ((promise-task result*) success failure))))

(defn prepare-upload
  "Stages a local media source in Trixnity's media store and returns a
  Missionary task of a prepared upload map.

  `source` may be a string path, `java.io.File`, or `java.nio.file.Path`.

  Supported opts:

  | key              | description                                                       |
  |------------------|-------------------------------------------------------------------|
  | `::mx/file-name` | Optional logical file name stored in the returned metadata        |
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

(defn get-media
  "Downloads plain media identified by `uri`.

  Returns a Missionary task of a normalized media handle:

  - `::mx/input-stream`, the primary public readable stream
  - `::mx/raw`, an opaque upstream media value used only for composition with
    [[temporary-file]]

  `uri` should be an MXC URI such as `mxc://example.org/abc`.

  Use this for event fields that already carry a plain media reference, such as
  `::mx/url` or `::mx/thumbnail-url`.

  Example:

  ```clojure
  (m/sp
    (let [handle (m/? (get-media client (::mx/url ev)))]
      (slurp (::mx/input-stream handle))))
  ```"
  [client uri]
  (let [uri (mx/validate! ::mx/url uri)]
    (deferred-task
     #(mx/validate!
       ::mx/MediaHandle
       (m/? (internal/suspend-task bridge/get-media
                                   client
                                   uri))))))

(defn get-encrypted-media
  "Downloads encrypted media from normalized `encrypted-file` metadata.

  `encrypted-file` must be the normalized encrypted-file map exposed on events
  under `::mx/encrypted-file` or `::mx/thumbnail-encrypted-file`.

  Returns the same normalized media-handle shape as [[get-media]], with
  `::mx/input-stream` as the public happy path and `::mx/raw` kept opaque for
  [[temporary-file]] composition.

  Example:

  ```clojure
  (m/sp
    (let [handle (m/? (get-encrypted-media client
                                           (::mx/encrypted-file ev)))]
      (slurp (::mx/input-stream handle))))
  ```"
  [client encrypted-file]
  (let [encrypted-file (mx/validate! ::mx/EncryptedFile encrypted-file)]
    (deferred-task
     #(mx/validate!
       ::mx/MediaHandle
       (m/? (internal/suspend-task bridge/get-encrypted-media
                                   client
                                   encrypted-file))))))

(defn get-thumbnail
  "Downloads a homeserver-generated thumbnail for `uri`.

  This mirrors upstream Trixnity `getThumbnail`: it asks the homeserver to
  generate a thumbnail for an MXC URI using explicit dimensions. It does not
  read event-provided thumbnail references already present on an event. For
  those, use [[get-media]] or [[get-encrypted-media]] with
  `::mx/thumbnail-url` or `::mx/thumbnail-encrypted-file`.

  Returns the same normalized media-handle shape as [[get-media]].

  Supported opts:

  | key | description
  |-----|-------------
  | `::mx/method` | Thumbnail resize method, either `:crop` or `:scale` |
  | `::mx/animated` | Request animated thumbnails when upstream supports them |

  Example:

  ```clojure
  (m/sp
    (let [handle (m/? (get-thumbnail client
                                      (::mx/url ev)
                                      320
                                      200
                                      {::mx/method :scale}))]
      (slurp (::mx/input-stream handle))))
  ```"
  ([client uri width height]
   (get-thumbnail client uri width height {}))
  ([client uri width height opts]
   (mx/validate! ::mx/url uri)
   (mx/validate! ::mx/width width)
   (mx/validate! ::mx/height height)
   (let [opts (mx/validate! ::mx/GetThumbnailOpts opts)]
     (deferred-task
      #(mx/validate!
        ::mx/MediaHandle
        (m/? (internal/suspend-task bridge/get-thumbnail
                                    client
                                    uri
                                    (long width)
                                    (long height)
                                    (some-> (::mx/method opts) name)
                                    (::mx/animated opts))))))))

(defn temporary-file
  "Creates a temporary file from `media`.

  `media` may be either a resolved media handle or a media-handle task returned
  by [[get-media]], [[get-encrypted-media]], or [[get-thumbnail]].

  Returns a Missionary task of a closeable [[TemporaryMediaFile]] record. The
  record exposes `:path` directly as a slurpable string path and should usually
  be used with `with-open`.

  Treat `::mx/raw` on media handles as opaque; it exists only so this helper
  can compose with already-fetched media.

  This is an opt-in JVM convenience for integrations that need a filesystem
  path. If the underlying media handle cannot produce a temporary file, the task
  fails with an exception from the bridge layer.

  Example:

  ```clojure
  (m/sp
    (with-open [tmp (m/? (temporary-file
                          (get-media client (::mx/url ev))))]
      (slurp (:path tmp))))
  ```"
  [media]
  (let [media (if (map? media)
                (mx/validate! ::mx/MediaHandle media)
                media)]
    (deferred-task
     #(let [handle          (if (map? media)
                              media
                              (mx/validate! ::mx/MediaHandle
                                            (m/? media)))
            temporary-file* (mx/validate!
                             ::mx/BridgeTemporaryMediaFile
                             (m/? (internal/suspend-task bridge/media-temporary-file
                                                         (::mx/raw handle))))]
        (->TemporaryMediaFile (str (::mx/path temporary-file*))
                              (::mx/raw temporary-file*))))))
