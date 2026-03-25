(ns ol.trixnity.media-test
  (:require
   [clojure.test :refer [deftest is]]
   [missionary.core :as m]
   [ol.trixnity.internal.bridge :as bridge]
   [ol.trixnity.media :as sut]
   [ol.trixnity.schemas :as schemas])
  (:import
   [java.io Closeable]
   [java.nio.file Files Paths]))

(defn- realize-task [task]
  (m/? task))

(deftype StubCloseable [closed-count]
  Closeable
  (close [_]
    (swap! closed-count inc)))

(defn- temp-file [suffix contents]
  (let [path (Files/createTempFile "trixnity-media-" suffix
                                   (make-array java.nio.file.attribute.FileAttribute 0))]
    (spit (.toFile path) contents)
    path))

(deftest prepare-upload-normalizes-path-like-values-and-returns-metadata-test
  (let [calls  (atom [])
        source (temp-file ".png" "image-data")]
    (with-redefs [bridge/prepare-upload-media
                  (fn [client source-path mime-type on-success _]
                    (swap! calls conj [client source-path mime-type])
                    (on-success "upload://plain/1")
                    (->StubCloseable (atom 0)))]
      (is (= {::schemas/cache-uri   "upload://plain/1"
              ::schemas/source-path (.toString (.toAbsolutePath source))
              ::schemas/file-name   (.getName (.toFile source))
              ::schemas/mime-type   "image/png"
              ::schemas/size-bytes  10}
             (realize-task
              (sut/prepare-upload :client-handle
                                  source
                                  {::schemas/mime-type "image/png"})))))
    (is (= [[:client-handle
             (.toString (.toAbsolutePath source))
             "image/png"]]
           @calls))))

(deftest upload-accepts-a-prepared-upload-map-and-preserves-metadata-test
  (let [calls (atom [])]
    (with-redefs [bridge/upload-media
                  (fn [client cache-uri keep-in-cache on-success _]
                    (swap! calls conj [client cache-uri keep-in-cache])
                    (on-success "mxc://example.org/avatar")
                    (->StubCloseable (atom 0)))]
      (is (= {::schemas/cache-uri   "upload://plain/1"
              ::schemas/mxc-uri     "mxc://example.org/avatar"
              ::schemas/source-path "/tmp/avatar.png"
              ::schemas/file-name   "avatar.png"
              ::schemas/mime-type   "image/png"
              ::schemas/size-bytes  1234}
             (realize-task
              (sut/upload :client-handle
                          {::schemas/cache-uri   "upload://plain/1"
                           ::schemas/source-path "/tmp/avatar.png"
                           ::schemas/file-name   "avatar.png"
                           ::schemas/mime-type   "image/png"
                           ::schemas/size-bytes  1234}
                          {::schemas/keep-in-cache false})))))
    (is (= [[:client-handle "upload://plain/1" false]]
           @calls))))

(deftest upload-path-source-composes-prepare-and-upload-test
  (let [calls  (atom [])
        source (temp-file ".txt" "hello")]
    (with-redefs [bridge/prepare-upload-media
                  (fn [client source-path mime-type on-success _]
                    (swap! calls conj [:prepare client source-path mime-type])
                    (on-success "upload://plain/2")
                    (->StubCloseable (atom 0)))

                  bridge/upload-media
                  (fn [client cache-uri keep-in-cache on-success _]
                    (swap! calls conj [:upload client cache-uri keep-in-cache])
                    (on-success "mxc://example.org/file")
                    (->StubCloseable (atom 0)))]
      (is (= {::schemas/cache-uri   "upload://plain/2"
              ::schemas/mxc-uri     "mxc://example.org/file"
              ::schemas/source-path (.toString (.toAbsolutePath source))
              ::schemas/file-name   (.getName (.toFile source))
              ::schemas/size-bytes  5}
             (realize-task
              (sut/upload :client-handle (Paths/get (.toString source) (make-array String 0)))))))
    (is (= [[:prepare :client-handle (.toString (.toAbsolutePath source)) nil]
            [:upload :client-handle "upload://plain/2" true]]
           @calls))))

(deftest media-surfaces-validate-before-bridge-test
  (let [calls (atom [])]
    (with-redefs [bridge/prepare-upload-media
                  (fn [& _]
                    (swap! calls conj :prepare)
                    (throw (ex-info "bridge should not be called" {})))

                  bridge/upload-media
                  (fn [& _]
                    (swap! calls conj :upload)
                    (throw (ex-info "bridge should not be called" {})))]
      (is (try
            (sut/prepare-upload :client-handle "")
            false
            (catch clojure.lang.ExceptionInfo _ true)))
      (is (try
            (sut/upload :client-handle {::schemas/cache-uri   ""
                                        ::schemas/source-path "/tmp/x"})
            false
            (catch clojure.lang.ExceptionInfo _ true))))
    (is (empty? @calls))))
