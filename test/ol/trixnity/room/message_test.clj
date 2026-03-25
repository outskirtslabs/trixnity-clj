(ns ol.trixnity.room.message-test
  (:require
   [clojure.test :refer [deftest is testing]]
   [ol.trixnity.room.message :as sut]
   [ol.trixnity.schemas :as mx])
  (:import
   [java.io File]
   [java.nio.file Path Paths]
   [java.time Duration]))

(deftest emote-builder-produces-a-normalized-message-spec-test
  (is
   (= {::mx/kind           :emote
       ::mx/body           "/me waves"
       ::mx/format         "org.matrix.custom.html"
       ::mx/formatted-body "<em>waves</em>"}
      (sut/emote "/me waves"
                 {::mx/format         "org.matrix.custom.html"
                  ::mx/formatted-body "<em>waves</em>"}))))

(deftest attachment-builders-preserve-explicit-metadata-test
  (let [duration (Duration/ofSeconds 42)]
    (is
     (= {::mx/kind           :audio
         ::mx/source-path    "/tmp/audio/intro.ogg"
         ::mx/body           "Intro clip"
         ::mx/file-name      "custom.ogg"
         ::mx/mime-type      "audio/ogg"
         ::mx/size-bytes     1024
         ::mx/duration       duration
         ::mx/format         "org.matrix.custom.html"
         ::mx/formatted-body "<strong>Intro clip</strong>"}
        (sut/audio "/tmp/audio/intro.ogg"
                   {::mx/body           "Intro clip"
                    ::mx/file-name      "custom.ogg"
                    ::mx/mime-type      "audio/ogg"
                    ::mx/size-bytes     1024
                    ::mx/duration       duration
                    ::mx/format         "org.matrix.custom.html"
                    ::mx/formatted-body "<strong>Intro clip</strong>"})))
    (is
     (= {::mx/kind        :image
         ::mx/source-path "/tmp/images/cat.png"
         ::mx/body        "Cat photo"
         ::mx/file-name   "cat-final.png"
         ::mx/mime-type   "image/png"
         ::mx/size-bytes  2048
         ::mx/height      800
         ::mx/width       600}
        (sut/image "/tmp/images/cat.png"
                   {::mx/body       "Cat photo"
                    ::mx/file-name  "cat-final.png"
                    ::mx/mime-type  "image/png"
                    ::mx/size-bytes 2048
                    ::mx/height     800
                    ::mx/width      600})))
    (is
     (= {::mx/kind        :file
         ::mx/source-path "/tmp/docs/report.pdf"
         ::mx/body        "Quarterly report"
         ::mx/file-name   "report-v2.pdf"
         ::mx/mime-type   "application/pdf"
         ::mx/size-bytes  4096}
        (sut/file "/tmp/docs/report.pdf"
                  {::mx/body       "Quarterly report"
                   ::mx/file-name  "report-v2.pdf"
                   ::mx/mime-type  "application/pdf"
                   ::mx/size-bytes 4096})))))

(deftest attachment-builders-default-body-and-file-name-from-the-source-basename-test
  (let [path (Paths/get "/tmp/media/voice-note.opus" (make-array String 0))
        file (File. "/tmp/docs/spec-sheet.pdf")]
    (is
     (= {::mx/kind        :audio
         ::mx/source-path (.toString ^Path path)
         ::mx/body        "voice-note.opus"
         ::mx/file-name   "voice-note.opus"}
        (sut/audio path)))
    (is
     (= {::mx/kind        :image
         ::mx/source-path "/tmp/media/poster.png"
         ::mx/body        "poster.png"
         ::mx/file-name   "poster.png"}
        (sut/image "/tmp/media/poster.png")))
    (is
     (= {::mx/kind        :file
         ::mx/source-path (.getPath file)
         ::mx/body        "spec-sheet.pdf"
         ::mx/file-name   "spec-sheet.pdf"}
        (sut/file file)))))

(deftest attachment-builders-support-reply-metadata-test
  (let [message (sut/reply-to
                 (sut/image "/tmp/media/poster.png")
                 {::mx/event-id                      "$reply"
                  ::mx/relates-to
                  {::mx/relation-type     "m.thread"
                   ::mx/relation-event-id "$thread"
                   ::mx/reply-to-event-id "$root"
                   ::mx/is-falling-back   false}})]
    (is
     (= {::mx/kind                           :image
         ::mx/source-path                    "/tmp/media/poster.png"
         ::mx/body                           "poster.png"
         ::mx/file-name                      "poster.png"
         ::mx/reply-to
         {::mx/event-id                      "$reply"
          ::mx/relates-to
          {::mx/relation-type     "m.thread"
           ::mx/relation-event-id "$thread"
           ::mx/reply-to-event-id "$root"
           ::mx/is-falling-back   false}}}
        message))))

(deftest attachment-builders-reject-nil-or-blank-source-paths-test
  (doseq [f [sut/audio sut/image sut/file]]
    (testing (str f)
      (is (thrown? clojure.lang.ExceptionInfo
                   (f nil)))
      (is (thrown? clojure.lang.ExceptionInfo
                   (f "")))
      (is (thrown? clojure.lang.ExceptionInfo
                   (f "   "))))))
