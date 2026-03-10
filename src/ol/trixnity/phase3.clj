(ns ol.trixnity.phase3
  (:require
   [clojure.edn :as edn]
   [clojure.java.io :as io]
   [clojure.set :as set]
   [clojure.string :as str]))

(def ^:private default-paths
  {:deps-file                "deps.edn"
   :kotlin-build-file        "kotlin/build.gradle.kts"
   :kotlin-bridge-build-file "kotlin-bridge/build.gradle.kts"
   :bridge-spec-file         "bridge-spec.edn"
   :bridge-generated-kotlin  "kotlin-bridge/src/generated/kotlin"
   :interop-file             "src/ol/trixnity/interop.clj"})

(def ^:private trixnity-version-source-coord
  'net.folivo/trixnity-client-jvm)

(def ^:private managed-version-targets
  [{:target     :deps-client
    :file-key   :deps-file
    :file-label "deps.edn"
    :coord      "net.folivo/trixnity-client-jvm"
    :regex      #"(?m)(net\.folivo/trixnity-client-jvm\s+\{:mvn/version\s+\")([^\"]+)(\"\})"}
   {:target     :deps-client-repository
    :file-key   :deps-file
    :file-label "deps.edn"
    :coord      "net.folivo/trixnity-client-repository-exposed-jvm"
    :regex      #"(?m)(net\.folivo/trixnity-client-repository-exposed-jvm\s+\{:mvn/version\s+\")([^\"]+)(\"\})"}
   {:target     :deps-client-media
    :file-key   :deps-file
    :file-label "deps.edn"
    :coord      "net.folivo/trixnity-client-media-okio-jvm"
    :regex      #"(?m)(net\.folivo/trixnity-client-media-okio-jvm\s+\{:mvn/version\s+\")([^\"]+)(\"\})"}
   {:target     :kotlin-client
    :file-key   :kotlin-build-file
    :file-label "kotlin/build.gradle.kts"
    :coord      "net.folivo:trixnity-client"
    :regex      #"(?m)(\"net\.folivo:trixnity-client:)([^\"]+)(\"\))"}
   {:target     :kotlin-client-repository
    :file-key   :kotlin-build-file
    :file-label "kotlin/build.gradle.kts"
    :coord      "net.folivo:trixnity-client-repository-exposed"
    :regex      #"(?m)(\"net\.folivo:trixnity-client-repository-exposed:)([^\"]+)(\"\))"}
   {:target     :kotlin-client-media
    :file-key   :kotlin-build-file
    :file-label "kotlin/build.gradle.kts"
    :coord      "net.folivo:trixnity-client-media-okio"
    :regex      #"(?m)(\"net\.folivo:trixnity-client-media-okio:)([^\"]+)(\"\))"}
   {:target     :bridge-client
    :file-key   :kotlin-bridge-build-file
    :file-label "kotlin-bridge/build.gradle.kts"
    :coord      "net.folivo:trixnity-client"
    :regex      #"(?m)(\"net\.folivo:trixnity-client:)([^\"]+)(\"\))"}
   {:target     :bridge-client-repository
    :file-key   :kotlin-bridge-build-file
    :file-label "kotlin-bridge/build.gradle.kts"
    :coord      "net.folivo:trixnity-client-repository-exposed"
    :regex      #"(?m)(\"net\.folivo:trixnity-client-repository-exposed:)([^\"]+)(\"\))"}
   {:target     :bridge-client-media
    :file-key   :kotlin-bridge-build-file
    :file-label "kotlin-bridge/build.gradle.kts"
    :coord      "net.folivo:trixnity-client-media-okio"
    :regex      #"(?m)(\"net\.folivo:trixnity-client-media-okio:)([^\"]+)(\"\))"}])

(def ^:private required-bridge-operation-names
  #{"loginBlocking"
    "fromStoreBlocking"
    "startSyncBlocking"
    "createRoomBlocking"
    "inviteUserBlocking"
    "sendTextReplyBlocking"
    "sendReactionBlocking"
    "startTimelinePump"
    "stopTimelinePump"})

(defn- with-default-paths [opts]
  (merge default-paths opts))

(defn- normalize-file-path [path]
  (-> path io/file .getPath))

(defn- load-trixnity-version [deps-file]
  (or (-> deps-file slurp edn/read-string :deps trixnity-version-source-coord :mvn/version)
      (throw (ex-info "deps.edn is missing net.folivo/trixnity-client-jvm :mvn/version"
                      {:deps-file deps-file
                       :coord     trixnity-version-source-coord}))))

(defn- update-target-content [content expected-version target]
  (str/replace content
               (:regex target)
               (fn [[_ prefix _ suffix]]
                 (str prefix expected-version suffix))))

(defn- verify-target [content expected-version target]
  (let [versions (map #(nth % 2) (re-seq (:regex target) content))]
    (cond
      (empty? versions)
      [{:file     (:file-label target)
        :target   (:target target)
        :coord    (:coord target)
        :expected expected-version
        :actual   :missing}]

      :else
      (->> versions
           (remove #(= expected-version %))
           (mapv (fn [actual]
                   {:file     (:file-label target)
                    :target   (:target target)
                    :coord    (:coord target)
                    :expected expected-version
                    :actual   actual}))))))

(defn sync-versions!
  "Sync trixnity version across managed files using deps.edn as source of truth."
  [opts]
  (let [{:keys [deps-file kotlin-build-file kotlin-bridge-build-file]}
        (with-default-paths opts)
        expected-version                                               (load-trixnity-version deps-file)
        file-paths                                                     [[:deps-file deps-file]
                                                                        [:kotlin-build-file kotlin-build-file]
                                                                        [:kotlin-bridge-build-file kotlin-bridge-build-file]]
        grouped-targets                                                (group-by :file-key managed-version-targets)
        updated-files                                                  (reduce
                                                                        (fn [acc [file-key path]]
                                                                          (let [path'        (normalize-file-path path)
                                                                                current      (slurp path')
                                                                                next-content (reduce
                                                                                              (fn [content target]
                                                                                                (update-target-content content expected-version target))
                                                                                              current
                                                                                              (get grouped-targets file-key))]
                                                                            (if (= current next-content)
                                                                              acc
                                                                              (do
                                                                                (spit path' next-content)
                                                                                (conj acc (-> (first (get grouped-targets file-key))
                                                                                              :file-label))))))
                                                                        []
                                                                        file-paths)]
    {:ok?              true
     :trixnity-version expected-version
     :updated-files    updated-files}))

(defn verify-versions
  "Verify managed files match trixnity version in deps.edn."
  [opts]
  (let [{:keys [deps-file kotlin-build-file kotlin-bridge-build-file]}
        (with-default-paths opts)
        expected-version                                               (load-trixnity-version deps-file)
        file-paths                                                     {:deps-file                (normalize-file-path deps-file)
                                                                        :kotlin-build-file        (normalize-file-path kotlin-build-file)
                                                                        :kotlin-bridge-build-file (normalize-file-path kotlin-bridge-build-file)}
        mismatches                                                     (->> managed-version-targets
                                                                            (mapcat
                                                                             (fn [target]
                                                                               (verify-target (slurp (get file-paths (:file-key target)))
                                                                                              expected-version
                                                                                              target)))
                                                                            vec)]
    {:ok?              (empty? mismatches)
     :trixnity-version expected-version
     :mismatches       mismatches}))

(defn versions-sync
  "bb/clojure -X entrypoint for syncing versions from deps.edn."
  [opts]
  (let [result (sync-versions! opts)]
    (println "Trixnity version from deps.edn:" (:trixnity-version result))
    (if (seq (:updated-files result))
      (doseq [file (:updated-files result)]
        (println "updated" file))
      (println "already in sync"))
    result))

(defn versions-verify
  "bb/clojure -X entrypoint for verifying version consistency from deps.edn."
  [opts]
  (let [result (verify-versions opts)]
    (if (:ok? result)
      (do
        (println "Version files are in sync for trixnity" (:trixnity-version result))
        result)
      (do
        (doseq [{:keys [file coord expected actual]} (:mismatches result)]
          (println "version drift:" file coord "expected" expected "actual" actual))
        (throw (ex-info "version drift detected" result))))))

(defn- load-bridge-spec [spec-file]
  (edn/read-string (slurp spec-file)))

(defn- bridge-operation-names [spec]
  (->> (:bridges spec)
       (mapcat :operations)
       (map :name)
       set))

(defn- validate-bridge-spec! [spec]
  (let [actual-ops (bridge-operation-names spec)
        missing    (-> required-bridge-operation-names
                       (set/difference actual-ops)
                       sort
                       vec)]
    (when (seq missing)
      (throw (ex-info "missing required bridge operations"
                      {:missing missing})))))

(defn- write-file-if-changed! [path content]
  (let [f (io/file path)]
    (io/make-parents f)
    (let [existing (when (.exists f) (slurp f))]
      (when-not (= existing content)
        (spit f content)
        true))))

(defn- bridge-method-source [{:keys [name request return]}]
  (str "    @JvmStatic\n"
       "    fun " name "(request: " request "): " return " = blocking(\"" name "\")\n\n"))

(defn- bridge-file-source [class-name operations]
  (str "package ol.trixnity.bridge\n\n"
       "import kotlinx.coroutines.runBlocking\n\n"
       "object " class-name " {\n"
       (apply str (map bridge-method-source operations))
       "    private fun <T> blocking(method: String): T =\n"
       "        runBlocking { unsupported(method) }\n\n"
       "    private fun <T> unsupported(method: String): T {\n"
       "        throw UnsupportedOperationException(\"Bridge method is not implemented yet: $method\")\n"
       "    }\n"
       "}\n"))

(defn- dto-file-source [dto-types]
  (str "package ol.trixnity.bridge\n\n"
       "data class TimelinePumpHandle(val id: String)\n\n"
       (apply str
              (map (fn [dto]
                     (str "data class " dto "(val payload: Map<String, Any?>)\n\n"))
                   dto-types))))

(defn generate-bridge!
  "Generate Kotlin bridge source files from bridge-spec.edn."
  [opts]
  (let [{:keys [bridge-spec-file bridge-generated-kotlin out-dir spec-file]}
        (with-default-paths opts)
        spec-path                                                            (or spec-file bridge-spec-file)
        out-root                                                             (normalize-file-path (or out-dir bridge-generated-kotlin))
        spec                                                                 (load-bridge-spec spec-path)
        _                                                                    (validate-bridge-spec! spec)
        package-root                                                         (str (io/file out-root "ol/trixnity/bridge"))
        bridge-files                                                         (reduce
                                                                              (fn [acc {:keys [class operations]}]
                                                                                (let [path    (str (io/file package-root (str class ".kt")))
                                                                                      content (bridge-file-source class operations)]
                                                                                  (write-file-if-changed! path content)
                                                                                  (conj acc path)))
                                                                              []
                                                                              (:bridges spec))
        dto-path                                                             (str (io/file package-root "BridgeDtos.kt"))
        _                                                                    (write-file-if-changed! dto-path (dto-file-source (:dto-types spec)))]
    {:ok?             true
     :spec-file       spec-path
     :generated-files (conj bridge-files dto-path)}))

(defn bridge-gen
  "bb/clojure -X entrypoint for bridge generation."
  [opts]
  (let [result (generate-bridge! opts)]
    (doseq [file (:generated-files result)]
      (println "generated" file))
    result))

(defn- camel->kebab [s]
  (-> s
      (str/replace #"([a-z0-9])([A-Z])" "$1-$2")
      str/lower-case))

(defn- interop-return-hint [return]
  (case return
    "Any" "Object"
    "Unit" nil
    "String" nil
    return))

(defn- bridge-import-types [spec]
  (let [bridge-classes (map :class (:bridges spec))
        op-requests    (mapcat (fn [{:keys [operations]}]
                                 (map :request operations))
                               (:bridges spec))
        op-returns     (->> (:bridges spec)
                            (mapcat :operations)
                            (map :return)
                            (remove #{"Any" "Unit" "String"}))]
    (->> (concat bridge-classes
                 (:dto-types spec)
                 op-requests
                 op-returns)
         distinct
         sort)))

(defn- interop-def-source [bridge-class {:keys [name request return]}]
  (let [fn-name     (camel->kebab name)
        return-hint (interop-return-hint return)]
    (str "(defn " fn-name "\n"
         (when return-hint
           (str "  ^" return-hint "\n"))
         "  [^" request " request]\n"
         "  (" bridge-class "/" name " request))\n\n")))

(defn- interop-file-source [spec]
  (let [import-types (bridge-import-types spec)
        import-block (str/join "\n"
                               (map (fn [type-name]
                                      (str "    " type-name))
                                    import-types))
        defs         (apply str
                            (mapcat (fn [{:keys [class operations]}]
                                      (map (fn [op]
                                             (interop-def-source class op))
                                           operations))
                                    (:bridges spec)))]
    (str ";; Generated by bb interop:gen. Do not edit by hand.\n"
         "(ns ol.trixnity.interop\n"
         "  (:import\n"
         "   (ol.trixnity.bridge\n"
         import-block
         ")))\n\n"
         defs)))

(defn generate-interop!
  "Generate ol.trixnity.interop from bridge-spec.edn."
  [opts]
  (let [{:keys [bridge-spec-file spec-file interop-file out-file]}
        (with-default-paths opts)
        spec-path                                                  (or spec-file bridge-spec-file)
        target-file                                                (normalize-file-path (or out-file interop-file))
        spec                                                       (load-bridge-spec spec-path)
        _                                                          (validate-bridge-spec! spec)
        content                                                    (interop-file-source spec)
        changed?                                                   (boolean (write-file-if-changed! target-file content))]
    {:ok?            true
     :spec-file      spec-path
     :generated-file target-file
     :changed?       changed?}))

(defn verify-interop
  "Verify generated ol.trixnity.interop is up-to-date."
  [opts]
  (let [{:keys [bridge-spec-file spec-file interop-file out-file]}
        (with-default-paths opts)
        spec-path                                                  (or spec-file bridge-spec-file)
        target-file                                                (normalize-file-path (or out-file interop-file))
        spec                                                       (load-bridge-spec spec-path)
        _                                                          (validate-bridge-spec! spec)
        expected                                                   (interop-file-source spec)
        actual                                                     (when (.exists (io/file target-file))
                                                                     (slurp target-file))
        mismatches                                                 (cond
                                                                     (nil? actual)
                                                                     [{:file   target-file
                                                                       :status :missing}]

                                                                     (not= expected actual)
                                                                     [{:file   target-file
                                                                       :status :stale}]

                                                                     :else
                                                                     [])]
    {:ok?            (empty? mismatches)
     :spec-file      spec-path
     :generated-file target-file
     :mismatches     mismatches}))

(defn interop-gen
  "bb/clojure -X entrypoint for interop generation."
  [opts]
  (let [result (generate-interop! opts)]
    (println "generated" (:generated-file result))
    result))

(defn interop-verify
  "bb/clojure -X entrypoint for interop staleness checks."
  [opts]
  (let [result (verify-interop opts)]
    (if (:ok? result)
      (do
        (println "Interop file is up-to-date:" (:generated-file result))
        result)
      (do
        (doseq [{:keys [file status]} (:mismatches result)]
          (println "interop mismatch:" file status))
        (throw (ex-info "interop file is stale" result))))))
