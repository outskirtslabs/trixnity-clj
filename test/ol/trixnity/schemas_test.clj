(ns ol.trixnity.schemas-test
  (:require
   [clojure.test :refer [deftest is]]
   [malli.core :as m]
   [ol.trixnity.schemas :as sut])
  (:import
   [de.connect2x.trixnity.core.model.events EmptyEventContent]
   [java.time Duration]))

(deftest timeout-schemas-reference-the-named-duration-schema-test
  (let [schema-entries (sut/schemas {})
        registry       (sut/registry {})
        duration       (Duration/ofSeconds 1)
        duration-keys  [::sut/timeout
                        ::sut/decryption-timeout
                        ::sut/fetch-timeout]]
    (is (contains? schema-entries ::sut/duration))
    (doseq [k duration-keys]
      (is (= ::sut/duration (get schema-entries k)))
      (is (m/validate (m/schema k {:registry registry}) duration))
      (is (not (m/validate (m/schema k {:registry registry}) "PT1S"))))))

(deftest event-content-class-schemas-require-trixnity-event-content-types-test
  (let [registry   (sut/registry {})
        valid-type EmptyEventContent
        class-ids  [::sut/room-event-content-class
                    ::sut/state-event-content-class
                    ::sut/global-account-data-event-content-class
                    ::sut/room-account-data-event-content-class]]
    (doseq [schema-id class-ids]
      (is (m/validate (m/schema schema-id {:registry registry}) valid-type))
      (is (not (m/validate (m/schema schema-id {:registry registry}) String))))))

(deftest room-event-content-schema-requires-room-event-content-values-test
  (let [registry (sut/registry {})]
    (is (m/validate (m/schema ::sut/room-event-content {:registry registry})
                    EmptyEventContent/INSTANCE))
    (is (not (m/validate (m/schema ::sut/room-event-content {:registry registry})
                         :content)))))

(deftest validate-uses-shared-schema-registry-by-default-test
  (let [duration (Duration/ofSeconds 1)]
    (is (= duration (sut/validate! ::sut/timeout duration)))
    (is (thrown-with-msg?
         clojure.lang.ExceptionInfo
         #"Schema validation failed"
         (sut/validate! ::sut/timeout "PT1S")))))

(deftest validate-allows-an-explicit-custom-registry-test
  (let [schema-registry-var (ns-resolve 'ol.trixnity.schemas 'schema-registry)]
    (is schema-registry-var)
    (when schema-registry-var
      (let [custom-registry (assoc (var-get schema-registry-var)
                                   ::even-int
                                   [:fn even?])]
        (is (= 2 (sut/validate! custom-registry ::even-int 2)))
        (is (thrown-with-msg?
             clojure.lang.ExceptionInfo
             #"Schema validation failed"
             (sut/validate! custom-registry ::even-int 3)))))))

(deftest message-spec-schema-dispatches-by-kind-test
  (let [schema   (m/schema ::sut/MessageSpec {:registry (sut/registry {})})
        duration (Duration/ofSeconds 5)]
    (is (m/validate schema {::sut/kind :text
                            ::sut/body "hello"}))
    (is (m/validate schema {::sut/kind           :emote
                            ::sut/body           "/me waves"
                            ::sut/formatted-body "<em>waves</em>"}))
    (is (m/validate schema {::sut/kind        :audio
                            ::sut/body        "Voice note"
                            ::sut/source-path "/tmp/voice-note.opus"
                            ::sut/file-name   "voice-note.opus"
                            ::sut/mime-type   "audio/ogg"
                            ::sut/size-bytes  1024
                            ::sut/duration    duration}))
    (is (m/validate schema {::sut/kind        :image
                            ::sut/body        "Poster"
                            ::sut/source-path "/tmp/poster.png"
                            ::sut/file-name   "poster.png"
                            ::sut/height      800
                            ::sut/width       600}))
    (is (m/validate schema {::sut/kind        :file
                            ::sut/body        "Spec sheet"
                            ::sut/source-path "/tmp/spec-sheet.pdf"
                            ::sut/file-name   "spec-sheet.pdf"
                            ::sut/mime-type   "application/pdf"}))
    (is (not (m/validate schema {::sut/kind :audio
                                 ::sut/body "Voice note"})))
    (is (not (m/validate schema {::sut/kind        :audio
                                 ::sut/body        "Voice note"
                                 ::sut/source-path ""})))
    (is (not (m/validate schema {::sut/kind        :image
                                 ::sut/body        "Poster"
                                 ::sut/source-path "/tmp/poster.png"
                                 ::sut/height      -1})))
    (is (not (m/validate schema {::sut/kind        :file
                                 ::sut/body        "Spec sheet"
                                 ::sut/source-path "/tmp/spec-sheet.pdf"
                                 ::sut/size-bytes  -1})))))

(deftest room-id-or-alias-schema-accepts-matrix-room-targets-test
  (let [schema (m/schema ::sut/room-id-or-alias {:registry (sut/registry {})})]
    (is (m/validate schema "!room:example.org"))
    (is (m/validate schema "#ops:example.org"))
    (is (not (m/validate schema "ops")))))

(deftest state-event-spec-schema-dispatches-by-type-test
  (let [schema (m/schema ::sut/StateEventSpec {:registry (sut/registry {})})]
    (is (m/validate schema {::sut/type "m.room.name"
                            ::sut/name "Ops Bot"}))
    (is (m/validate schema {::sut/type      "m.room.topic"
                            ::sut/state-key ""
                            ::sut/topic     "Incident chatter"}))
    (is (m/validate schema {::sut/type "m.room.avatar"
                            ::sut/url  "mxc://example.org/abc"}))
    (is (not (m/validate schema {::sut/type "m.room.avatar"})))
    (is (not (m/validate schema {::sut/type "m.room.unknown"
                                 ::sut/body "wat"})))))

(deftest media-upload-schemas-capture-prepared-and-uploaded-media-test
  (let [registry (sut/registry {})]
    (is (m/validate (m/schema ::sut/PreparedUpload {:registry registry})
                    {::sut/cache-uri   "upload://plain/1"
                     ::sut/source-path "/tmp/avatar.png"
                     ::sut/file-name   "avatar.png"
                     ::sut/size-bytes  42}))
    (is (m/validate (m/schema ::sut/UploadedMedia {:registry registry})
                    {::sut/cache-uri   "upload://plain/1"
                     ::sut/mxc-uri     "mxc://example.org/abc"
                     ::sut/source-path "/tmp/avatar.png"
                     ::sut/file-name   "avatar.png"
                     ::sut/size-bytes  42}))
    (is (not (m/validate (m/schema ::sut/PreparedUpload {:registry registry})
                         {::sut/cache-uri ""})))
    (is (not (m/validate (m/schema ::sut/UploadedMedia {:registry registry})
                         {::sut/cache-uri   "upload://plain/1"
                          ::sut/source-path "/tmp/avatar.png"
                          ::sut/file-name   "avatar.png"
                          ::sut/size-bytes  42})))))

(deftest upload-progress-schema-normalizes-transferred-bytes-and-optional-total-test
  (let [schema (m/schema ::sut/UploadProgress {:registry (sut/registry {})})]
    (is (m/validate schema {::sut/transferred 32768
                            ::sut/total       1048576}))
    (is (m/validate schema {::sut/transferred 32768}))
    (is (not (m/validate schema {::sut/total 1048576})))
    (is (not (m/validate schema {::sut/transferred -1})))))

(deftest room-outbox-message-schema-allows-attachment-upload-progress-test
  (let [schema       (m/schema ::sut/RoomOutboxMessage {:registry (sut/registry {})})
        schema-entry (get (sut/schemas {}) ::sut/RoomOutboxMessage)]
    (is (some #{[::sut/media-upload-progress {:optional true} ::sut/UploadProgress]}
              (rest schema-entry)))
    (is (m/validate schema {::sut/room-id               "!room:example.org"
                            ::sut/transaction-id        "txn-123"
                            ::sut/content               {::sut/body "uploading"}
                            ::sut/created-at            "2026-03-25T10:15:30Z"
                            ::sut/media-upload-progress {::sut/transferred 512
                                                         ::sut/total       1024}}))
    (is (m/validate schema {::sut/room-id        "!room:example.org"
                            ::sut/transaction-id "txn-123"
                            ::sut/content        {::sut/body "queued"}
                            ::sut/created-at     "2026-03-25T10:15:30Z"}))))
