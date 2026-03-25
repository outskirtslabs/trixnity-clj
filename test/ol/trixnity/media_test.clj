(ns ol.trixnity.media-test
  (:require
   [clojure.test :refer [deftest is]]
   [missionary.core :as m]
   [ol.trixnity.internal.bridge :as bridge]
   [ol.trixnity.media :as sut]
   [ol.trixnity.schemas :as schemas])
  (:import
   [java.io ByteArrayInputStream Closeable InputStream]
   [java.nio.charset StandardCharsets]
   [java.nio.file Files Paths]))

(defn- realize-task [task]
  (m/? task))

(defn- collect-values [flow n]
  (m/? (->> flow
            (m/eduction (take n))
            (m/reduce conj []))))

(defn- resolve-var [ns-sym var-sym]
  (ns-resolve ns-sym var-sym))

(defn- slurp-stream [input-stream]
  (String. (.readAllBytes ^InputStream input-stream)
           StandardCharsets/UTF_8))

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

(deftest upload-returns-a-handle-with-a-result-task-and-progress-flow-test
  (let [calls                          (atom [])
        start-upload                   (promise)
        progress-snapshots
        [{::schemas/transferred 0
          ::schemas/total       1234}
         {::schemas/transferred 1234
          ::schemas/total       1234}]]
    (with-redefs [bridge/upload-media
                  (fn [& args]
                    (let [[client cache-uri keep-in-cache maybe-on-progress on-success _]
                          args
                          [on-progress on-success]
                          (if (= 6 (count args))
                            [maybe-on-progress on-success]
                            [nil maybe-on-progress])]
                      (swap! calls conj [client cache-uri keep-in-cache (some? on-progress)])
                      (future
                        @start-upload
                        (doseq [snapshot progress-snapshots]
                          (when on-progress
                            (on-progress snapshot)))
                        (on-success "mxc://example.org/avatar"))
                      (->StubCloseable (atom 0))))]
      (let [handle (sut/upload :client-handle
                               {::schemas/cache-uri   "upload://plain/1"
                                ::schemas/source-path "/tmp/avatar.png"
                                ::schemas/file-name   "avatar.png"
                                ::schemas/mime-type   "image/png"
                                ::schemas/size-bytes  1234}
                               {::schemas/keep-in-cache false})]
        (is (map? handle))
        (when (map? handle)
          (let [progress* (future (collect-values (::schemas/progress handle) 2))]
            (deliver start-upload :go)
            (is (= progress-snapshots @progress*))
            (is (= {::schemas/cache-uri   "upload://plain/1"
                    ::schemas/mxc-uri     "mxc://example.org/avatar"
                    ::schemas/source-path "/tmp/avatar.png"
                    ::schemas/file-name   "avatar.png"
                    ::schemas/mime-type   "image/png"
                    ::schemas/size-bytes  1234}
                   (realize-task (::schemas/result handle))))))))
    (is (= [[:client-handle "upload://plain/1" false true]]
           @calls))))

(deftest upload-path-source-composes-prepare-and-upload-through-one-handle-test
  (let [calls        (atom [])
        start-upload (promise)
        source       (temp-file ".txt" "hello")]
    (with-redefs [bridge/prepare-upload-media
                  (fn [client source-path mime-type on-success _]
                    (swap! calls conj [:prepare client source-path mime-type])
                    (on-success "upload://plain/2")
                    (->StubCloseable (atom 0)))

                  bridge/upload-media
                  (fn [& args]
                    (let [[client cache-uri keep-in-cache maybe-on-progress on-success _]
                          args
                          [_ on-success]
                          (if (= 6 (count args))
                            [maybe-on-progress on-success]
                            [nil maybe-on-progress])]
                      (swap! calls conj [:upload client cache-uri keep-in-cache])
                      (future
                        @start-upload
                        (on-success "mxc://example.org/file")))
                    (->StubCloseable (atom 0)))]
      (let [handle (sut/upload :client-handle
                               (Paths/get (.toString source) (make-array String 0)))]
        (is (map? handle))
        (when (map? handle)
          (deliver start-upload :go)
          (is (= {::schemas/cache-uri   "upload://plain/2"
                  ::schemas/mxc-uri     "mxc://example.org/file"
                  ::schemas/source-path (.toString (.toAbsolutePath source))
                  ::schemas/file-name   (.getName (.toFile source))
                  ::schemas/size-bytes  5}
                 (realize-task (::schemas/result handle)))))))
    (is (= [[:prepare :client-handle (.toString (.toAbsolutePath source)) nil]
            [:upload :client-handle "upload://plain/2" true]]
           @calls))))

(deftest upload-handle-shares-one-underlying-upload-between-progress-and-result-test
  (let [upload-count                (atom 0)
        start-upload                (promise)
        progress-snapshots
        [{::schemas/transferred 3
          ::schemas/total       5}
         {::schemas/transferred 5
          ::schemas/total       5}]]
    (with-redefs [bridge/upload-media
                  (fn [& args]
                    (let [[_ _ _ maybe-on-progress on-success _] args
                          [on-progress on-success]
                          (if (= 6 (count args))
                            [maybe-on-progress on-success]
                            [nil maybe-on-progress])]
                      (swap! upload-count inc)
                      (future
                        @start-upload
                        (doseq [snapshot progress-snapshots]
                          (when on-progress
                            (on-progress snapshot)))
                        (on-success "mxc://example.org/file"))
                      (->StubCloseable (atom 0))))]
      (let [handle (sut/upload :client-handle
                               {::schemas/cache-uri   "upload://plain/3"
                                ::schemas/source-path "/tmp/file.txt"
                                ::schemas/file-name   "file.txt"
                                ::schemas/size-bytes  5})]
        (is (map? handle))
        (when (map? handle)
          (let [progress* (future (collect-values (::schemas/progress handle) 2))]
            (deliver start-upload :go)
            (is (= progress-snapshots @progress*))
            (is (= "mxc://example.org/file"
                   (::schemas/mxc-uri
                    (realize-task (::schemas/result handle)))))))))
    (is (= 1 @upload-count))))

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

(deftest download-media-surfaces-return-stream-first-media-handle-tasks-test
  (let [get-media-var                                                           (resolve-var 'ol.trixnity.media 'get-media)
        get-encrypted-media-var                                                 (resolve-var 'ol.trixnity.media
                                                                                             'get-encrypted-media)
        get-thumbnail-var                                                       (resolve-var 'ol.trixnity.media
                                                                                             'get-thumbnail)
        bridge-get-media-var                                                    (resolve-var 'ol.trixnity.internal.bridge
                                                                                             'get-media)
        bridge-get-encrypted-media-var                                          (resolve-var 'ol.trixnity.internal.bridge
                                                                                             'get-encrypted-media)
        bridge-get-thumbnail-var                                                (resolve-var 'ol.trixnity.internal.bridge
                                                                                             'get-thumbnail)
        calls                                                                   (atom [])
        encrypted-file
        {::schemas/url                   "mxc://example.org/encrypted"
         ::schemas/jwk                   {::schemas/jwk-key        "secret"
                                          ::schemas/key-type       "oct"
                                          ::schemas/key-operations #{"encrypt"
                                                                     "decrypt"}
                                          ::schemas/algorithm      "A256CTR"
                                          ::schemas/extractable    true}
         ::schemas/initialization-vector "iv"
         ::schemas/hashes                {"sha256" "hash"}
         ::schemas/version               "v2"}]
    (is (some? get-media-var)
        "ol.trixnity.media/get-media is missing")
    (is (some? get-encrypted-media-var)
        "ol.trixnity.media/get-encrypted-media is missing")
    (is (some? get-thumbnail-var)
        "ol.trixnity.media/get-thumbnail is missing")
    (is (some? bridge-get-media-var)
        "ol.trixnity.internal.bridge/get-media is missing")
    (is (some? bridge-get-encrypted-media-var)
        "ol.trixnity.internal.bridge/get-encrypted-media is missing")
    (is (some? bridge-get-thumbnail-var)
        "ol.trixnity.internal.bridge/get-thumbnail is missing")
    (when (every? some? [get-media-var
                         get-encrypted-media-var
                         get-thumbnail-var
                         bridge-get-media-var
                         bridge-get-encrypted-media-var
                         bridge-get-thumbnail-var])
      (let [plain-raw     (Object.)
            encrypted-raw (Object.)
            thumb-raw     (Object.)]
        (with-redefs-fn
          {bridge-get-media-var
           (fn [client uri on-success _]
             (swap! calls conj [:get-media client uri])
             (on-success {::schemas/input-stream
                          (ByteArrayInputStream. (.getBytes "plain"
                                                            StandardCharsets/UTF_8))
                          ::schemas/raw          plain-raw})
             (->StubCloseable (atom 0)))

           bridge-get-encrypted-media-var
           (fn [client payload on-success _]
             (swap! calls conj [:get-encrypted-media client payload])
             (on-success {::schemas/input-stream
                          (ByteArrayInputStream. (.getBytes "secret"
                                                            StandardCharsets/UTF_8))
                          ::schemas/raw          encrypted-raw})
             (->StubCloseable (atom 0)))

           bridge-get-thumbnail-var
           (fn [client uri width height method animated on-success _]
             (swap! calls conj [:get-thumbnail client uri width height method animated])
             (on-success {::schemas/input-stream
                          (ByteArrayInputStream. (.getBytes "thumb"
                                                            StandardCharsets/UTF_8))
                          ::schemas/raw          thumb-raw})
             (->StubCloseable (atom 0)))}
          (fn []
            (let [plain-handle     ((var-get get-media-var)
                                    :client-handle
                                    "mxc://example.org/plain")
                  encrypted-handle ((var-get get-encrypted-media-var)
                                    :client-handle
                                    encrypted-file)
                  thumbnail-handle ((var-get get-thumbnail-var)
                                    :client-handle
                                    "mxc://example.org/plain"
                                    320
                                    200
                                    {::schemas/method   :scale
                                     ::schemas/animated true})]
              (is (= "plain"
                     (slurp-stream
                      (::schemas/input-stream (realize-task plain-handle)))))
              (is (identical? plain-raw (::schemas/raw (realize-task plain-handle))))
              (is (= "secret"
                     (slurp-stream
                      (::schemas/input-stream (realize-task encrypted-handle)))))
              (is (identical? encrypted-raw
                              (::schemas/raw (realize-task encrypted-handle))))
              (is (= "thumb"
                     (slurp-stream
                      (::schemas/input-stream (realize-task thumbnail-handle)))))
              (is (identical? thumb-raw
                              (::schemas/raw (realize-task thumbnail-handle)))))))))
    (is (= [[:get-media :client-handle "mxc://example.org/plain"]
            [:get-encrypted-media :client-handle encrypted-file]
            [:get-thumbnail :client-handle "mxc://example.org/plain" 320 200 "scale" true]]
           @calls))))

(deftest temporary-file-composes-with-media-handle-tasks-and-resolved-handles-test
  (let [temporary-file-var          (resolve-var 'ol.trixnity.media 'temporary-file)
        get-media-var               (resolve-var 'ol.trixnity.media 'get-media)
        bridge-get-media-var        (resolve-var 'ol.trixnity.internal.bridge
                                                 'get-media)
        bridge-temp-file-var        (resolve-var 'ol.trixnity.internal.bridge
                                                 'media-temporary-file)
        bridge-delete-temp-file-var (resolve-var 'ol.trixnity.internal.bridge
                                                 'delete-media-temporary-file)
        calls                       (atom [])
        deletes                     (atom [])
        handle-raw                  (Object.)
        temp-raw                    (Object.)
        path                        (temp-file ".txt" "downloaded")]
    (is (some? temporary-file-var)
        "ol.trixnity.media/temporary-file is missing")
    (is (some? get-media-var)
        "ol.trixnity.media/get-media is missing")
    (is (some? bridge-get-media-var)
        "ol.trixnity.internal.bridge/get-media is missing")
    (is (some? bridge-temp-file-var)
        "ol.trixnity.internal.bridge/media-temporary-file is missing")
    (is (some? bridge-delete-temp-file-var)
        "ol.trixnity.internal.bridge/delete-media-temporary-file is missing")
    (when (every? some? [temporary-file-var
                         get-media-var
                         bridge-get-media-var
                         bridge-temp-file-var
                         bridge-delete-temp-file-var])
      (with-redefs-fn
        {bridge-get-media-var
         (fn [client uri on-success _]
           (swap! calls conj [:get-media client uri])
           (on-success {::schemas/input-stream
                        (ByteArrayInputStream. (.getBytes "downloaded"
                                                          StandardCharsets/UTF_8))
                        ::schemas/raw          handle-raw})
           (->StubCloseable (atom 0)))

         bridge-temp-file-var
         (fn [raw on-success _]
           (swap! calls conj [:temporary-file raw])
           (on-success {::schemas/path path
                        ::schemas/raw  temp-raw})
           (->StubCloseable (atom 0)))

         bridge-delete-temp-file-var
         (fn [raw]
           (swap! deletes conj raw)
           nil)}
        (fn []
          (with-open [tmp (realize-task
                           ((var-get temporary-file-var)
                            ((var-get get-media-var)
                             :client-handle
                             "mxc://example.org/plain")))]
            (is (= "downloaded" (slurp (:path tmp)))))
          (with-open [tmp (realize-task
                           ((var-get temporary-file-var)
                            {::schemas/input-stream
                             (ByteArrayInputStream. (.getBytes "ignored"
                                                               StandardCharsets/UTF_8))
                             ::schemas/raw          handle-raw}))]
            (is (= "downloaded" (slurp (:path tmp))))))))
    (is (= [[:get-media :client-handle "mxc://example.org/plain"]
            [:temporary-file handle-raw]
            [:temporary-file handle-raw]]
           @calls))
    (is (= [temp-raw temp-raw] @deletes))))

(deftest temporary-file-fails-usefully-when-bridge-cannot-produce-one-test
  (let [temporary-file-var   (resolve-var 'ol.trixnity.media 'temporary-file)
        bridge-temp-file-var (resolve-var 'ol.trixnity.internal.bridge
                                          'media-temporary-file)]
    (is (some? temporary-file-var)
        "ol.trixnity.media/temporary-file is missing")
    (is (some? bridge-temp-file-var)
        "ol.trixnity.internal.bridge/media-temporary-file is missing")
    (when (every? some? [temporary-file-var bridge-temp-file-var])
      (with-redefs-fn
        {bridge-temp-file-var
         (fn [_ _ on-failure]
           (on-failure (ex-info "temporary file unavailable" {}))
           (->StubCloseable (atom 0)))}
        #(is (thrown-with-msg?
              clojure.lang.ExceptionInfo
              #"temporary file unavailable"
              (realize-task
               ((var-get temporary-file-var)
                {::schemas/input-stream
                 (ByteArrayInputStream. (.getBytes "ignored"
                                                   StandardCharsets/UTF_8))
                 ::schemas/raw          (Object.)}))))))))

(deftest media-download-surfaces-validate-before-bridge-test
  (let [get-media-var                  (resolve-var 'ol.trixnity.media 'get-media)
        get-encrypted-media-var        (resolve-var 'ol.trixnity.media
                                                    'get-encrypted-media)
        get-thumbnail-var              (resolve-var 'ol.trixnity.media
                                                    'get-thumbnail)
        temporary-file-var             (resolve-var 'ol.trixnity.media
                                                    'temporary-file)
        bridge-get-media-var           (resolve-var 'ol.trixnity.internal.bridge
                                                    'get-media)
        bridge-get-encrypted-media-var (resolve-var 'ol.trixnity.internal.bridge
                                                    'get-encrypted-media)
        bridge-get-thumbnail-var       (resolve-var 'ol.trixnity.internal.bridge
                                                    'get-thumbnail)
        bridge-temp-file-var           (resolve-var 'ol.trixnity.internal.bridge
                                                    'media-temporary-file)
        calls                          (atom [])]
    (when (every? some? [get-media-var
                         get-encrypted-media-var
                         get-thumbnail-var
                         temporary-file-var
                         bridge-get-media-var
                         bridge-get-encrypted-media-var
                         bridge-get-thumbnail-var
                         bridge-temp-file-var])
      (with-redefs-fn
        {bridge-get-media-var
         (fn [& _]
           (swap! calls conj :get-media)
           (throw (ex-info "bridge should not be called" {})))

         bridge-get-encrypted-media-var
         (fn [& _]
           (swap! calls conj :get-encrypted-media)
           (throw (ex-info "bridge should not be called" {})))

         bridge-get-thumbnail-var
         (fn [& _]
           (swap! calls conj :get-thumbnail)
           (throw (ex-info "bridge should not be called" {})))

         bridge-temp-file-var
         (fn [& _]
           (swap! calls conj :temporary-file)
           (throw (ex-info "bridge should not be called" {})))}
        (fn []
          (is (thrown-with-msg?
               clojure.lang.ExceptionInfo
               #"Schema validation failed"
               ((var-get get-media-var) :client-handle "")))
          (is (thrown-with-msg?
               clojure.lang.ExceptionInfo
               #"Schema validation failed"
               ((var-get get-encrypted-media-var)
                :client-handle
                {::schemas/url "mxc://example.org/encrypted"})))
          (is (thrown-with-msg?
               clojure.lang.ExceptionInfo
               #"Schema validation failed"
               ((var-get get-thumbnail-var)
                :client-handle
                "mxc://example.org/plain"
                0
                200)))
          (is (thrown-with-msg?
               clojure.lang.ExceptionInfo
               #"Schema validation failed"
               ((var-get temporary-file-var)
                {::schemas/input-stream (Object.)
                 ::schemas/raw          (Object.)}))))))
    (is (empty? @calls))))
