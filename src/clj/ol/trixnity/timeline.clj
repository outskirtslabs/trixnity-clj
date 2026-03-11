(ns ol.trixnity.timeline
  (:require
   [ol.trixnity.interop :as interop]
   [ol.trixnity.schemas :as mx]))

(set! *warn-on-reflection* true)

(def ^:private schema-registry
  (mx/registry {}))

(defn subscribe!
  "Subscribes to timeline events and returns a closeable subscription handle.

  Supported opts:

  `{::mx/decryption-timeout java.time.Duration}`"
  [client opts handler]
  (let [opts (mx/validate! schema-registry ::mx/TimelineSubscribeOpts opts)]
    (interop/subscribe-timeline
     (cond-> {::mx/client   client
              ::mx/on-event handler}
       (::mx/decryption-timeout opts)
       (assoc ::mx/decryption-timeout (::mx/decryption-timeout opts))))))

(defn close!
  "Closes a timeline subscription handle."
  [subscription]
  (.close ^java.io.Closeable subscription)
  nil)
