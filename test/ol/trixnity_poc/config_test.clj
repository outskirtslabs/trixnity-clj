(ns ol.trixnity-poc.config-test
  (:require
   [clojure.string :as str]
   [clojure.test :refer [deftest is testing]]
   [ol.trixnity-poc.config :as sut]))

(deftest load-config-test
  (testing "loads shared-secret registration settings"
    (let [config (sut/load-config
                  {"MATRIX_HOMESERVER_URL"             "https://matrix.example.org"
                   "MATRIX_BOT_USER_ID"                "@bot:example.org"
                   "MATRIX_BOT_PASSWORD"               "pw"
                   "MATRIX_REGISTRATION_SHARED_SECRET" "secret-123"
                   "MATRIX_BOT_ADMIN"                  "true"
                   "MATRIX_ROOM_NAME"                  "my-room"
                   "MATRIX_ROOM_ID_FILE"               "./tmp/room-id.txt"
                   "MATRIX_MEDIA_PATH"                 "./tmp/media"
                   "MATRIX_DB_PATH"                    "./tmp/db"
                   "MATRIX_INVITE_USER"                "@invitee:example.org"
                   "MATRIX_TRY_REGISTER"               "false"})]
      (is (= "https://matrix.example.org"
             (sut/url->string (:homeserver-url config))))
      (is (= "@bot:example.org" (:user-id config)))
      (is (= "pw" (:password config)))
      (is (= "secret-123" (:registration-shared-secret config)))
      (is (true? (:bot-admin config)))
      (is (= "my-room" (:room-name config)))
      (is (= "./tmp/room-id.txt" (str (:room-id-file config))))
      (is (= "./tmp/media" (str (:media-path config))))
      (is (= "./tmp/db" (str (:database-path config))))
      (is (= "@invitee:example.org" (str (:invite-user config))))
      (is (false? (:try-register config)))))

  (testing "uses defaults for optional settings"
    (let [config (sut/load-config {})]
      (is (= "http://localhost:8008"
             (sut/url->string (:homeserver-url config))))
      (is (= "@trixnitycljbot:localhost" (:user-id config)))
      (is (= "password!123" (:password config)))
      (is (nil? (:registration-shared-secret config)))
      (is (false? (:bot-admin config)))
      (is (str/starts-with? (:room-name config)
                            "trixnity-clj-bot-room-"))
      (is (= "./dev-data/room-id.txt"
             (str (:room-id-file config))))
      (is (= "./dev-data/media" (str (:media-path config))))
      (is (= "./dev-data/trixnity-poc.sqlite"
             (str (:database-path config))))
      (is (nil? (:invite-user config)))
      (is (true? (:try-register config))))))
