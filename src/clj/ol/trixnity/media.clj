(ns ol.trixnity.media
  "Generic media upload helpers built on top of Trixnity's staged upload flow.

  Uploads happen in two steps:

  - [[prepare-upload]] stages local bytes in Trixnity's media store and returns
    a prepared upload map containing an `upload://...` cache URI
  - [[upload]] uploads either a prepared upload map or a local path-like source
    and returns the final `mxc://...` URI plus the prepared metadata

  The public shapes here stay normalized as namespaced keyword maps so callers
  do not need to work with Kotlin media APIs directly."
  (:require
   [clojure.java.io :as io]
   [missionary.core :as m]
   [ol.trixnity.internal :as internal]
   [ol.trixnity.internal.bridge :as bridge]
   [ol.trixnity.schemas :as mx]))

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
  "Uploads a prepared upload map or local media source and returns a Missionary
  task of an uploaded media map.

  When `source` is path-like, this first stages the file through
  [[prepare-upload]] and then uploads the resulting cache URI.

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
                           (mx/validate! ::mx/PreparedUpload source))]
     (m/sp
      (let [prepared-upload (or prepared-upload
                                (m/? (prepare-upload client source opts)))]
        (mx/validate!
         ::mx/UploadedMedia
         (assoc prepared-upload
                ::mx/mxc-uri
                (m/? (internal/suspend-task bridge/upload-media
                                            client
                                            (::mx/cache-uri prepared-upload)
                                            (get opts ::mx/keep-in-cache true))))))))))
