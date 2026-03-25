(ns ol.trixnity.room.message
  "Helpers for constructing normalized room message payloads.

  This namespace provides small builders for the message-spec maps accepted by
  [[ol.trixnity.room/send-message]].

  The helpers here cover the common message-spec cases:

  - [[text]] builds a normalized text message payload
  - [[emote]] builds a normalized emote message payload
  - [[audio]], [[image]], and [[file]] build normalized attachment payloads
  - [[reply-to]] attaches reply metadata from a normalized event map

  Use [[ol.trixnity.event]] to inspect the events you are replying to."
  (:require
   [clojure.java.io :as io]
   [ol.trixnity.event :as event]
   [ol.trixnity.schemas :as mx]))

(set! *warn-on-reflection* true)

(defn- validate-message-spec [message]
  (mx/validate! ::mx/MessageSpec message))

(defn- normalize-source-path [source-path]
  (cond
    (string? source-path) source-path
    (instance? java.io.File source-path) (.getPath ^java.io.File source-path)
    (instance? java.nio.file.Path source-path) (.toString ^java.nio.file.Path source-path)
    :else source-path))

(defn- basename [path]
  (.getName (io/file path)))

(defn- attachment-message
  [kind source-path opts]
  (let [source-path (mx/validate! ::mx/source-path
                                  (normalize-source-path source-path))
        basename    (basename source-path)]
    (validate-message-spec
     (cond-> {::mx/kind        kind
              ::mx/source-path source-path
              ::mx/body        (or (::mx/body opts) basename)
              ::mx/file-name   (or (::mx/file-name opts) basename)}
       (::mx/mime-type opts) (assoc ::mx/mime-type (::mx/mime-type opts))
       (::mx/size-bytes opts) (assoc ::mx/size-bytes (::mx/size-bytes opts))
       (::mx/duration opts) (assoc ::mx/duration (::mx/duration opts))
       (::mx/height opts) (assoc ::mx/height (::mx/height opts))
       (::mx/width opts) (assoc ::mx/width (::mx/width opts))
       (::mx/format opts) (assoc ::mx/format (::mx/format opts))
       (::mx/formatted-body opts)
       (assoc ::mx/formatted-body (::mx/formatted-body opts))))))

(defn text
  "Builds a text message-spec map understood by [[ol.trixnity.room/send-message]]."
  ([body]
   (text body {}))
  ([body opts]
   (validate-message-spec
    (cond-> {::mx/kind :text
             ::mx/body body}
      (::mx/format opts) (assoc ::mx/format (::mx/format opts))
      (::mx/formatted-body opts)
      (assoc ::mx/formatted-body (::mx/formatted-body opts))))))

(defn emote
  "Builds an emote message-spec map understood by [[ol.trixnity.room/send-message]]."
  ([body]
   (emote body {}))
  ([body opts]
   (validate-message-spec
    (cond-> {::mx/kind :emote
             ::mx/body body}
      (::mx/format opts) (assoc ::mx/format (::mx/format opts))
      (::mx/formatted-body opts)
      (assoc ::mx/formatted-body (::mx/formatted-body opts))))))

(defn audio
  "Builds an audio message-spec map understood by [[ol.trixnity.room/send-message]]."
  ([source-path]
   (audio source-path {}))
  ([source-path opts]
   (attachment-message :audio source-path opts)))

(defn image
  "Builds an image message-spec map understood by [[ol.trixnity.room/send-message]]."
  ([source-path]
   (image source-path {}))
  ([source-path opts]
   (attachment-message :image source-path opts)))

(defn file
  "Builds a file message-spec map understood by [[ol.trixnity.room/send-message]]."
  ([source-path]
   (file source-path {}))
  ([source-path opts]
   (attachment-message :file source-path opts)))

(defn reply-to
  "Associates reply metadata from normalized event `ev` onto `message`.

  When `ev` already carries relation metadata, that relation is copied into the
  reply target so upstream threading semantics are preserved."
  [message ev]
  (validate-message-spec
   (assoc message
          ::mx/reply-to
          (cond-> {::mx/event-id (event/event-id ev)}
            (event/relates-to ev)
            (assoc ::mx/relates-to
                   (event/relates-to ev))))))
