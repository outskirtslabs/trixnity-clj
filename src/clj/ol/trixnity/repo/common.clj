(ns ol.trixnity.repo.common
  (:require
   [clojure.string :as str]
   [sqlite4clj.core :as sqlite])
  (:import
   [de.connect2x.trixnity.core.model EventId RoomId UserId]
   [java.util.concurrent BlockingQueue]
   [kotlinx.serialization DeserializationStrategy SerializationStrategy]
   [kotlinx.serialization.json Json]
   [ol.trixnity.bridge CurrentTx Sqlite4cljRepositoryHandle]))

(set! *warn-on-reflection* true)

(def ^:private schema-statements
  [["CREATE TABLE IF NOT EXISTS account (repo_key TEXT PRIMARY KEY, payload TEXT NOT NULL) WITHOUT ROWID"]
   ["CREATE TABLE IF NOT EXISTS authentication (repo_key TEXT PRIMARY KEY, payload TEXT NOT NULL) WITHOUT ROWID"]
   ["CREATE TABLE IF NOT EXISTS server_data (repo_key TEXT PRIMARY KEY, payload TEXT NOT NULL) WITHOUT ROWID"]
   ["CREATE TABLE IF NOT EXISTS outdated_keys (repo_key TEXT PRIMARY KEY, payload TEXT NOT NULL) WITHOUT ROWID"]
   ["CREATE TABLE IF NOT EXISTS device_keys (repo_key TEXT PRIMARY KEY, payload TEXT NOT NULL) WITHOUT ROWID"]
   ["CREATE TABLE IF NOT EXISTS cross_signing_keys (repo_key TEXT PRIMARY KEY, payload TEXT NOT NULL) WITHOUT ROWID"]
   ["CREATE TABLE IF NOT EXISTS key_verification_state (repo_key TEXT PRIMARY KEY, payload TEXT NOT NULL) WITHOUT ROWID"]
   ["CREATE TABLE IF NOT EXISTS secrets (repo_key TEXT PRIMARY KEY, payload TEXT NOT NULL) WITHOUT ROWID"]
   ["CREATE TABLE IF NOT EXISTS olm_account (repo_key TEXT PRIMARY KEY, payload TEXT NOT NULL) WITHOUT ROWID"]
   ["CREATE TABLE IF NOT EXISTS olm_forget_fallback_key_after (repo_key TEXT PRIMARY KEY, payload TEXT NOT NULL) WITHOUT ROWID"]
   ["CREATE TABLE IF NOT EXISTS olm_session (repo_key TEXT PRIMARY KEY, payload TEXT NOT NULL) WITHOUT ROWID"]
   ["CREATE TABLE IF NOT EXISTS inbound_megolm_message_index (repo_key TEXT PRIMARY KEY, payload TEXT NOT NULL) WITHOUT ROWID"]
   ["CREATE TABLE IF NOT EXISTS outbound_megolm_session (repo_key TEXT PRIMARY KEY, payload TEXT NOT NULL) WITHOUT ROWID"]
   ["CREATE TABLE IF NOT EXISTS room (repo_key TEXT PRIMARY KEY, payload TEXT NOT NULL) WITHOUT ROWID"]
   ["CREATE TABLE IF NOT EXISTS room_user (room_id TEXT NOT NULL, user_id TEXT NOT NULL, payload TEXT NOT NULL, PRIMARY KEY (room_id, user_id)) WITHOUT ROWID"]
   ["CREATE TABLE IF NOT EXISTS room_user_receipts (room_id TEXT NOT NULL, user_id TEXT NOT NULL, payload TEXT NOT NULL, PRIMARY KEY (room_id, user_id)) WITHOUT ROWID"]
   ["CREATE TABLE IF NOT EXISTS room_state (room_id TEXT NOT NULL, type TEXT NOT NULL, state_key TEXT NOT NULL, payload TEXT NOT NULL, PRIMARY KEY (room_id, type, state_key)) WITHOUT ROWID"]
   ["CREATE TABLE IF NOT EXISTS timeline_event (room_id TEXT NOT NULL, event_id TEXT NOT NULL, payload TEXT NOT NULL, PRIMARY KEY (room_id, event_id)) WITHOUT ROWID"]
   ["CREATE TABLE IF NOT EXISTS timeline_event_relation (room_id TEXT NOT NULL, related_event_id TEXT NOT NULL, relation_type TEXT NOT NULL, event_id TEXT NOT NULL, payload TEXT NOT NULL, PRIMARY KEY (room_id, related_event_id, relation_type, event_id)) WITHOUT ROWID"]
   ["CREATE TABLE IF NOT EXISTS media_cache_mapping (repo_key TEXT PRIMARY KEY, payload TEXT NOT NULL) WITHOUT ROWID"]
   ["CREATE TABLE IF NOT EXISTS global_account_data (type TEXT NOT NULL, item_key TEXT NOT NULL, payload TEXT NOT NULL, PRIMARY KEY (type, item_key)) WITHOUT ROWID"]
   ["CREATE TABLE IF NOT EXISTS user_presence (repo_key TEXT PRIMARY KEY, payload TEXT NOT NULL) WITHOUT ROWID"]
   ["CREATE TABLE IF NOT EXISTS room_account_data (room_id TEXT NOT NULL, type TEXT NOT NULL, item_key TEXT NOT NULL, payload TEXT NOT NULL, PRIMARY KEY (room_id, type, item_key)) WITHOUT ROWID"]
   ["CREATE TABLE IF NOT EXISTS secret_key_request (repo_key TEXT PRIMARY KEY, payload TEXT NOT NULL) WITHOUT ROWID"]
   ["CREATE TABLE IF NOT EXISTS room_key_request (repo_key TEXT PRIMARY KEY, payload TEXT NOT NULL) WITHOUT ROWID"]
   ["CREATE TABLE IF NOT EXISTS inbound_megolm_session (room_id TEXT NOT NULL, session_id TEXT NOT NULL, has_been_backed_up INTEGER NOT NULL, payload TEXT NOT NULL, PRIMARY KEY (room_id, session_id)) WITHOUT ROWID"]
   ["CREATE TABLE IF NOT EXISTS room_outbox_message (room_id TEXT NOT NULL, transaction_id TEXT NOT NULL, content_type TEXT NOT NULL, payload TEXT NOT NULL, PRIMARY KEY (room_id, transaction_id)) WITHOUT ROWID"]
   ["CREATE TABLE IF NOT EXISTS notification (repo_key TEXT PRIMARY KEY, room_id TEXT NOT NULL, payload TEXT NOT NULL) WITHOUT ROWID"]
   ["CREATE TABLE IF NOT EXISTS notification_update (repo_key TEXT PRIMARY KEY, room_id TEXT NOT NULL, payload TEXT NOT NULL) WITHOUT ROWID"]
   ["CREATE TABLE IF NOT EXISTS notification_state (repo_key TEXT PRIMARY KEY, payload TEXT NOT NULL) WITHOUT ROWID"]
   ["CREATE TABLE IF NOT EXISTS key_chain_link (signing_user_id TEXT NOT NULL, signing_key TEXT NOT NULL, signed_user_id TEXT NOT NULL, signed_key TEXT NOT NULL, payload TEXT NOT NULL, PRIMARY KEY (signing_user_id, signing_key, signed_user_id, signed_key)) WITHOUT ROWID"]
   ["CREATE TABLE IF NOT EXISTS migration (repo_key TEXT PRIMARY KEY, payload TEXT NOT NULL) WITHOUT ROWID"]])

(defn db
  [^Sqlite4cljRepositoryHandle handle]
  (.getDb handle))

(defn json
  [^Sqlite4cljRepositoryHandle handle]
  (.getJson handle))

(defn writer-pool
  [^Sqlite4cljRepositoryHandle handle]
  (:writer (db handle)))

(defn reader-pool
  [^Sqlite4cljRepositoryHandle handle]
  (:reader (db handle)))

(defn borrow-reader-conn!
  [^Sqlite4cljRepositoryHandle handle]
  (BlockingQueue/.take ^BlockingQueue (:conn-pool (reader-pool handle))))

(defn borrow-writer-conn!
  [^Sqlite4cljRepositoryHandle handle]
  (BlockingQueue/.take ^BlockingQueue (:conn-pool (writer-pool handle))))

(defn release-reader-conn!
  [^Sqlite4cljRepositoryHandle handle conn]
  (BlockingQueue/.offer ^BlockingQueue (:conn-pool (reader-pool handle)) conn)
  nil)

(defn release-writer-conn!
  [^Sqlite4cljRepositoryHandle handle conn]
  (BlockingQueue/.offer ^BlockingQueue (:conn-pool (writer-pool handle)) conn)
  nil)

(defn begin-deferred!
  [conn]
  (sqlite/q conn ["BEGIN DEFERRED"])
  nil)

(defn begin-immediate!
  [conn]
  (sqlite/q conn ["BEGIN IMMEDIATE"])
  nil)

(defn commit!
  [conn]
  (sqlite/q conn ["COMMIT"])
  nil)

(defn rollback!
  [conn]
  (sqlite/q conn ["ROLLBACK"])
  nil)

(defn current-read-conn
  []
  (CurrentTx/currentReadConn))

(defn current-write-conn
  []
  (CurrentTx/currentWriteConn))

(defn q-read
  [^Sqlite4cljRepositoryHandle handle query]
  (sqlite/q (or (current-write-conn)
                (current-read-conn)
                (reader-pool handle))
            query))

(defn q-write
  [^Sqlite4cljRepositoryHandle handle query]
  (sqlite/q (or (current-write-conn)
                (writer-pool handle))
            query))

(defn open!
  [path ^Json json]
  (Sqlite4cljRepositoryHandle. (sqlite/init-db! path) json))

(defn ensure-schema!
  [^Sqlite4cljRepositoryHandle handle]
  (doseq [statement schema-statements]
    (q-write handle statement))
  handle)

(defn encode-json
  [^Json json ^SerializationStrategy serializer value]
  (.encodeToString json serializer value))

(defn decode-json
  [^Json json ^DeserializationStrategy serializer ^String value]
  (.decodeFromString json serializer value))

(defn first-row
  [rows]
  (when (seq rows)
    (first rows)))

(defn maybe-rows
  [rows]
  (or rows []))

(defn in-placeholders
  [n]
  (str/join ", " (repeat n "?")))

(defn minimal-get
  [^Sqlite4cljRepositoryHandle handle table repo-key ^DeserializationStrategy serializer]
  (when-let [payload (first-row
                      (q-read handle
                              [(str "SELECT payload FROM " table " WHERE repo_key = ?")
                               repo-key]))]
    (decode-json (json handle) serializer payload)))

(defn minimal-save!
  [^Sqlite4cljRepositoryHandle handle table repo-key payload]
  (q-write handle
           [(str "INSERT INTO " table " (repo_key, payload) VALUES (?, ?) "
                 "ON CONFLICT(repo_key) DO UPDATE SET payload = excluded.payload")
            repo-key
            payload])
  nil)

(defn minimal-delete!
  [^Sqlite4cljRepositoryHandle handle table repo-key]
  (q-write handle
           [(str "DELETE FROM " table " WHERE repo_key = ?")
            repo-key])
  nil)

(defn delete-all!
  [^Sqlite4cljRepositoryHandle handle table]
  (q-write handle
           [(str "DELETE FROM " table)])
  nil)

(defn full-get-all
  [^Sqlite4cljRepositoryHandle handle table ^DeserializationStrategy serializer]
  (mapv (fn [payload]
          (decode-json (json handle) serializer payload))
        (maybe-rows
         (q-read handle [(str "SELECT payload FROM " table)]))))

(defn room-id-str
  [^RoomId room-id]
  (.getFull room-id))

(defn user-id-str
  [^UserId user-id]
  (.getFull user-id))

(defn event-id-str
  [^EventId event-id]
  (.getFull event-id))
