(ns ol.trixnity-poc.config
  (:require
   [clojure.string :as str])
  (:import
   (io.ktor.http URLBuilder URLParserKt Url)
   (java.nio.file Path)))

(defn- parse-url [s]
  (-> (URLBuilder.)
      (URLParserKt/takeFrom s)
      (.build)))

(defn- parse-bool [s]
  (= "true" (some-> s str/lower-case)))

(defn- maybe-non-blank [s]
  (when-not (str/blank? (or s ""))
    s))

(defn- to-path [s]
  (Path/of s (make-array String 0)))

(defn url->string ^String [^Url url]
  (str url))

(defn load-config
  ([]
   (load-config (System/getenv)))
  ([env]
   (let [timestamp                  (System/currentTimeMillis)
         homeserver                 (or (get env "MATRIX_HOMESERVER_URL")
                                        "http://localhost:8008")
         username                   (or (get env "MATRIX_BOT_USERNAME")
                                        "trixnitycljbot")
         password                   (or (get env "MATRIX_BOT_PASSWORD")
                                        "password!123")
         registration-shared-secret (-> (get env "MATRIX_REGISTRATION_SHARED_SECRET")
                                        maybe-non-blank)
         bot-admin                  (parse-bool (get env "MATRIX_BOT_ADMIN"))
         room-name                  (or (get env "MATRIX_ROOM_NAME")
                                        (str "trixnity-clj-bot-room-" timestamp))
         room-id-file               (to-path
                                     (or (get env "MATRIX_ROOM_ID_FILE")
                                         "./dev-data/room-id.txt"))
         media-path                 (to-path
                                     (or (get env "MATRIX_MEDIA_PATH")
                                         "./dev-data/media"))
         database-path              (to-path
                                     (or (get env "MATRIX_DB_PATH")
                                         "./dev-data/trixnity-poc.sqlite"))
         invite-user                (some-> (get env "MATRIX_INVITE_USER")
                                            maybe-non-blank)
         try-register               (not= "false"
                                          (some-> (get env "MATRIX_TRY_REGISTER")
                                                  str/lower-case))]
     {:homeserver-url             (parse-url homeserver)
      :username                   username
      :password                   password
      :registration-shared-secret registration-shared-secret
      :bot-admin                  bot-admin
      :room-name                  room-name
      :room-id-file               room-id-file
      :media-path                 media-path
      :database-path              database-path
      :invite-user                invite-user
      :try-register               try-register})))
