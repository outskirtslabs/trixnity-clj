(ns ol.trixnity.phase3-test
  (:require
   [clojure.string :as str]
   [clojure.test :refer [deftest is testing]]
   [ol.trixnity.phase3 :as sut])
  (:import
   (java.nio.file Files Path)
   (java.nio.file.attribute FileAttribute)))

(defn- make-temp-dir []
  (Files/createTempDirectory "phase3-test-"
                             (make-array FileAttribute 0)))

(defn- write-file!
  [^Path root relative-path content]
  (let [path (.resolve root relative-path)]
    (Files/createDirectories (.getParent path)
                             (make-array FileAttribute 0))
    (spit (str path) content)
    (str path)))

(defn- read-file [path]
  (slurp path))

(deftest sync-versions-updates-all-target-files-test
  (let [tmp                (make-temp-dir)
        deps-file          (write-file! tmp
                                        "deps.edn"
                                        (str "{:deps {net.folivo/trixnity-client-jvm {:mvn/version \"9.9.9\"}\n"
                                             "        net.folivo/trixnity-client-repository-exposed-jvm {:mvn/version \"4.22.7\"}\n"
                                             "        net.folivo/trixnity-client-media-okio-jvm {:mvn/version \"4.22.7\"}\n"
                                             "        io.ktor/ktor-client-java-jvm {:mvn/version \"3.4.1\"}}}\n"))
        kotlin-file        (write-file! tmp
                                        "kotlin/build.gradle.kts"
                                        (str "dependencies {\n"
                                             "    implementation(\"net.folivo:trixnity-client:4.22.7\")\n"
                                             "    implementation(\"net.folivo:trixnity-client-repository-exposed:4.22.7\")\n"
                                             "    implementation(\"net.folivo:trixnity-client-media-okio:4.22.7\")\n"
                                             "    implementation(\"io.ktor:ktor-client-java:3.4.1\")\n"
                                             "}\n"))
        bridge-file        (write-file! tmp
                                        "kotlin-bridge/build.gradle.kts"
                                        (str "dependencies {\n"
                                             "    implementation(\"net.folivo:trixnity-client:4.22.7\")\n"
                                             "    implementation(\"net.folivo:trixnity-client-repository-exposed:4.22.7\")\n"
                                             "    implementation(\"net.folivo:trixnity-client-media-okio:4.22.7\")\n"
                                             "}\n"))
        opts               {:deps-file                deps-file
                            :kotlin-build-file        kotlin-file
                            :kotlin-bridge-build-file bridge-file}
        first-sync-result  (sut/sync-versions! opts)
        second-sync-result (sut/sync-versions! opts)]
    (testing "first sync updates all managed files"
      (is (= #{"deps.edn"
               "kotlin/build.gradle.kts"
               "kotlin-bridge/build.gradle.kts"}
             (set (:updated-files first-sync-result)))))
    (testing "second sync is deterministic and idempotent"
      (is (empty? (:updated-files second-sync-result))))
    (testing "managed trixnity versions are updated"
      (is (= 0 (count (re-seq #"4.22.7" (read-file deps-file)))))
      (is (str/includes? (read-file deps-file) "9.9.9"))
      (is (str/includes? (read-file kotlin-file) "9.9.9"))
      (is (str/includes? (read-file bridge-file) "9.9.9")))
    (testing "non-managed versions stay unchanged"
      (is (str/includes? (read-file deps-file) "3.4.1"))
      (is (str/includes? (read-file kotlin-file) "3.4.1")))))

(deftest verify-versions-detects-drift-test
  (let [tmp         (make-temp-dir)
        deps-file   (write-file! tmp
                                 "deps.edn"
                                 (str "{:deps {net.folivo/trixnity-client-jvm {:mvn/version \"4.22.7\"}\n"
                                      "        net.folivo/trixnity-client-repository-exposed-jvm {:mvn/version \"4.22.7\"}\n"
                                      "        net.folivo/trixnity-client-media-okio-jvm {:mvn/version \"4.22.7\"}}}\n"))
        kotlin-file (write-file! tmp
                                 "kotlin/build.gradle.kts"
                                 "implementation(\"net.folivo:trixnity-client:4.22.8\")\n")
        bridge-file (write-file! tmp
                                 "kotlin-bridge/build.gradle.kts"
                                 (str "implementation(\"net.folivo:trixnity-client:4.22.7\")\n"
                                      "implementation(\"net.folivo:trixnity-client-repository-exposed:4.22.7\")\n"
                                      "implementation(\"net.folivo:trixnity-client-media-okio:4.22.7\")\n"))
        result      (sut/verify-versions
                     {:deps-file                deps-file
                      :kotlin-build-file        kotlin-file
                      :kotlin-bridge-build-file bridge-file})]
    (is (false? (:ok? result)))
    (is (= #{"kotlin/build.gradle.kts"}
           (set (map :file (:mismatches result)))))))

(deftest verify-versions-pass-when-in-sync-test
  (let [tmp         (make-temp-dir)
        deps-file   (write-file! tmp
                                 "deps.edn"
                                 (str "{:deps {net.folivo/trixnity-client-jvm {:mvn/version \"4.22.7\"}\n"
                                      "        net.folivo/trixnity-client-repository-exposed-jvm {:mvn/version \"4.22.7\"}\n"
                                      "        net.folivo/trixnity-client-media-okio-jvm {:mvn/version \"4.22.7\"}}}\n"))
        kotlin-file (write-file! tmp
                                 "kotlin/build.gradle.kts"
                                 (str "implementation(\"net.folivo:trixnity-client:4.22.7\")\n"
                                      "implementation(\"net.folivo:trixnity-client-repository-exposed:4.22.7\")\n"
                                      "implementation(\"net.folivo:trixnity-client-media-okio:4.22.7\")\n"))
        bridge-file (write-file! tmp
                                 "kotlin-bridge/build.gradle.kts"
                                 (str "implementation(\"net.folivo:trixnity-client:4.22.7\")\n"
                                      "implementation(\"net.folivo:trixnity-client-repository-exposed:4.22.7\")\n"
                                      "implementation(\"net.folivo:trixnity-client-media-okio:4.22.7\")\n"))
        result      (sut/verify-versions
                     {:deps-file                deps-file
                      :kotlin-build-file        kotlin-file
                      :kotlin-bridge-build-file bridge-file})]
    (is (true? (:ok? result)))
    (is (empty? (:mismatches result)))))

(deftest generate-bridge-creates-stable-jvm-api-test
  (let [tmp         (make-temp-dir)
        spec-file   (write-file! tmp
                                 "bridge-spec.edn"
                                 (str "{:bridges [{:class \"ClientBridge\"\n"
                                      "            :operations [{:name \"loginBlocking\"\n"
                                      "                          :request \"LoginRequest\"\n"
                                      "                          :return \"Any\"}\n"
                                      "                         {:name \"fromStoreBlocking\"\n"
                                      "                          :request \"FromStoreRequest\"\n"
                                      "                          :return \"Any\"}\n"
                                      "                         {:name \"startSyncBlocking\"\n"
                                      "                          :request \"StartSyncRequest\"\n"
                                      "                          :return \"Unit\"}]}\n"
                                      "           {:class \"RoomBridge\"\n"
                                      "            :operations [{:name \"createRoomBlocking\"\n"
                                      "                          :request \"CreateRoomRequest\"\n"
                                      "                          :return \"String\"}\n"
                                      "                         {:name \"inviteUserBlocking\"\n"
                                      "                          :request \"InviteUserRequest\"\n"
                                      "                          :return \"Unit\"}\n"
                                      "                         {:name \"sendTextReplyBlocking\"\n"
                                      "                          :request \"SendTextReplyRequest\"\n"
                                      "                          :return \"Unit\"}\n"
                                      "                         {:name \"sendReactionBlocking\"\n"
                                      "                          :request \"SendReactionRequest\"\n"
                                      "                          :return \"Unit\"}]}\n"
                                      "           {:class \"EventBridge\"\n"
                                      "            :operations [{:name \"startTimelinePump\"\n"
                                      "                          :request \"StartTimelinePumpRequest\"\n"
                                      "                          :return \"TimelinePumpHandle\"}\n"
                                      "                         {:name \"stopTimelinePump\"\n"
                                      "                          :request \"StopTimelinePumpRequest\"\n"
                                      "                          :return \"Unit\"}]}]\n"
                                      " :dto-types [\"LoginRequest\"\n"
                                      "             \"FromStoreRequest\"\n"
                                      "             \"StartSyncRequest\"\n"
                                      "             \"CreateRoomRequest\"\n"
                                      "             \"InviteUserRequest\"\n"
                                      "             \"SendTextReplyRequest\"\n"
                                      "             \"SendReactionRequest\"\n"
                                      "             \"StartTimelinePumpRequest\"\n"
                                      "             \"StopTimelinePumpRequest\"]}\n"))
        out-dir     (str (.resolve tmp "generated"))
        _           (sut/generate-bridge! {:spec-file spec-file
                                           :out-dir   out-dir})
        client-file (str (.resolve tmp "generated/ol/trixnity/bridge/ClientBridge.kt"))
        room-file   (str (.resolve tmp "generated/ol/trixnity/bridge/RoomBridge.kt"))
        event-file  (str (.resolve tmp "generated/ol/trixnity/bridge/EventBridge.kt"))
        dto-file    (str (.resolve tmp "generated/ol/trixnity/bridge/BridgeDtos.kt"))]
    (is (.exists (java.io.File. client-file)))
    (is (.exists (java.io.File. room-file)))
    (is (.exists (java.io.File. event-file)))
    (is (.exists (java.io.File. dto-file)))
    (is (re-find #"@JvmStatic\s+fun loginBlocking\(" (read-file client-file)))
    (is (re-find #"@JvmStatic\s+fun createRoomBlocking\(" (read-file room-file)))
    (is (re-find #"@JvmStatic\s+fun startTimelinePump\(" (read-file event-file)))
    (is (re-find #"runBlocking" (read-file client-file)))
    (is (not (str/includes? (read-file client-file) "$default")))
    (is (not (re-find #"fun\s+\w+\([^)]*=\s*" (read-file client-file))))
    (is (not (re-find #"fun\s+\w+\([^)]*=\s*" (read-file room-file))))
    (is (not (re-find #"fun\s+\w+\([^)]*=\s*" (read-file event-file))))))

(deftest generate-bridge-fails-when-required-ops-are-missing-test
  (let [tmp       (make-temp-dir)
        spec-file (write-file! tmp
                               "bridge-spec.edn"
                               (str "{:bridges [{:class \"ClientBridge\"\n"
                                    "            :operations []}]}\n"))]
    (is (thrown-with-msg?
         clojure.lang.ExceptionInfo
         #"missing required bridge operations"
         (sut/generate-bridge! {:spec-file spec-file
                                :out-dir   (str (.resolve tmp "generated"))})))))
