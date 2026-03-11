(ns ol.trixnity.repo
  "Built-in repository helpers for the sqlite4clj-backed happy path.

  Most callers do not need to interact with the raw repository handle or the
  Kotlin bridge types directly. Use [[sqlite4clj-config]] with
  [[ol.trixnity.client/open!]].

  Advanced callers can still build a `MatrixClient` with some other repository
  implementation and pass that client to [[ol.trixnity.client/open!]] via
  `::mx/client`."
  (:require
   [ol.trixnity.repo.common :as common]
   [ol.trixnity.schemas :as mx])
  (:import
   [de.connect2x.trixnity.client.store KeyChainLink StoredNotification
    StoredNotificationUpdate]
   [de.connect2x.trixnity.client.store.repository AccountRepository
    AuthenticationRepository CrossSigningKeysRepository DeviceKeysRepository
    GlobalAccountDataRepository InboundMegolmMessageIndexRepository
    InboundMegolmMessageIndexRepositoryKey InboundMegolmSessionRepository
    InboundMegolmSessionRepositoryKey KeyVerificationStateKey
    KeyVerificationStateRepository MediaCacheMappingRepository MigrationRepository
    NotificationStateRepository OlmAccountRepository OlmForgetFallbackKeyAfterRepository
    OlmSessionRepository OutboundMegolmSessionRepository OutdatedKeysRepository
    RoomAccountDataRepositoryKey RoomKeyRequestRepository RoomOutboxMessageRepositoryKey
    RoomRepository RoomStateRepositoryKey SecretKeyRequestRepository SecretsRepository
    ServerDataRepository TimelineEventKey TimelineEventRelationKey UserPresenceRepository]
   [de.connect2x.trixnity.core.model EventId RoomId UserId]
   [de.connect2x.trixnity.core.model.keys Key$Ed25519Key KeyValue$Curve25519KeyValue]
   [de.connect2x.trixnity.core.serialization.events EventContentSerializerMappings]
   [de.connect2x.trixnity.crypto.olm StoredInboundMegolmSession]
   [kotlinx.serialization KSerializer]
   [kotlinx.serialization.json Json]
   [ol.trixnity.bridge KeyChainLinkRepositoryOps NotificationRepositoryOps
    NotificationUpdateRepositoryOps RoomAccountDataRepositoryOps
    RoomOutboxMessageRepositoryOps RoomStateRepositoryOps RoomUserReceiptsRepositoryOps
    RoomUserRepositoryOps Sqlite4cljModelBridge Sqlite4cljRepositoryHandle
    Sqlite4cljSerializers TimelineEventRelationRepositoryOps TimelineEventRepositoryOps]))

(set! *warn-on-reflection* true)

(defn- option-value
  [options namespaced-key]
  (or (get options namespaced-key)
      (get options (keyword (name namespaced-key)))))

(defn sqlite4clj-config
  "Returns config entries for the built-in sqlite4clj repository and okio media store.

  Prefer the namespaced keys from [[ol.trixnity.schemas]]. Plain keywords are
  still accepted here as a convenience when normalizing app config.

  Options:

  | key | description
  |-----|-------------
  | `::mx/database-path` | SQLite file path used by `ol.trixnity.repo`
  | `::mx/media-path` | Directory used by the okio-backed media store

  Example:

  ```clojure
  (.get
   (client/open!
    (merge
      {::mx/homeserver-url \"https://matrix.example.org\"
       ::mx/username \"bot\"
       ::mx/password \"secret\"}
      (repo/sqlite4clj-config
        {::mx/database-path \"./var/trixnity.sqlite\"
         ::mx/media-path \"./var/media\"}))))
  ```"
  [options]
  {::mx/database-path (some-> (option-value options ::mx/database-path) str)
   ::mx/media-path    (some-> (option-value options ::mx/media-path) str)})

(defn open-handle!
  [path ^Json json]
  (common/open! path json))

(defn- minimal-get
  [^Sqlite4cljRepositoryHandle handle table repo-key ^KSerializer serializer]
  (when-let [payload (common/first-row
                      (common/q-read handle
                                     [(str "SELECT payload FROM " table " WHERE repo_key = ?")
                                      repo-key]))]
    (common/decode-json (common/json handle) serializer payload)))

(defn- minimal-save!
  [^Sqlite4cljRepositoryHandle handle table repo-key payload]
  (common/q-write handle
                  [(str "INSERT INTO " table " (repo_key, payload) VALUES (?, ?) "
                        "ON CONFLICT(repo_key) DO UPDATE SET payload = excluded.payload")
                   repo-key
                   payload])
  nil)

(defn- minimal-delete!
  [^Sqlite4cljRepositoryHandle handle table repo-key]
  (common/q-write handle
                  [(str "DELETE FROM " table " WHERE repo_key = ?")
                   repo-key])
  nil)

(defn- delete-all!
  [^Sqlite4cljRepositoryHandle handle table]
  (common/q-write handle
                  [(str "DELETE FROM " table)])
  nil)

(defn- full-get-all
  [^Sqlite4cljRepositoryHandle handle table ^KSerializer serializer]
  (mapv (fn [payload]
          (common/decode-json (common/json handle) serializer payload))
        (common/maybe-rows
         (common/q-read handle [(str "SELECT payload FROM " table)]))))

(defn- room-id-str
  [^RoomId room-id]
  (.getFull room-id))

(defn- user-id-str
  [^UserId user-id]
  (.getFull user-id))

(defn- event-id-str
  [^EventId event-id]
  (.getFull event-id))

(defn- create-account-repository
  [^Sqlite4cljRepositoryHandle handle]
  (let [serializer (Sqlite4cljSerializers/account)]
    (reify AccountRepository
      (^String serializeKey [_ key] (str key))
      (get [_ key _] (minimal-get handle "account" (str key) serializer))
      (save [_ key value _]
        (minimal-save! handle "account" (str key) (common/encode-json (common/json handle) serializer value)))
      (delete [_ key _] (minimal-delete! handle "account" (str key)))
      (deleteAll [_ _] (delete-all! handle "account")))))

(defn- create-authentication-repository
  [^Sqlite4cljRepositoryHandle handle]
  (let [serializer (Sqlite4cljSerializers/authentication)]
    (reify AuthenticationRepository
      (^String serializeKey [_ key] (str key))
      (get [_ key _] (minimal-get handle "authentication" (str key) serializer))
      (save [_ key value _]
        (minimal-save! handle "authentication" (str key) (common/encode-json (common/json handle) serializer value)))
      (delete [_ key _] (minimal-delete! handle "authentication" (str key)))
      (deleteAll [_ _] (delete-all! handle "authentication")))))

(defn- create-server-data-repository
  [^Sqlite4cljRepositoryHandle handle]
  (let [serializer (Sqlite4cljSerializers/serverData)]
    (reify ServerDataRepository
      (^String serializeKey [_ key] (str key))
      (get [_ key _] (minimal-get handle "server_data" (str key) serializer))
      (save [_ key value _]
        (minimal-save! handle "server_data" (str key) (common/encode-json (common/json handle) serializer value)))
      (delete [_ key _] (minimal-delete! handle "server_data" (str key)))
      (deleteAll [_ _] (delete-all! handle "server_data")))))

(defn- create-outdated-keys-repository
  [^Sqlite4cljRepositoryHandle handle]
  (let [serializer (Sqlite4cljSerializers/outdatedKeys)]
    (reify OutdatedKeysRepository
      (^String serializeKey [_ key] (str key))
      (get [_ key _] (minimal-get handle "outdated_keys" (str key) serializer))
      (save [_ key value _]
        (minimal-save! handle "outdated_keys" (str key) (common/encode-json (common/json handle) serializer value)))
      (delete [_ key _] (minimal-delete! handle "outdated_keys" (str key)))
      (deleteAll [_ _] (delete-all! handle "outdated_keys")))))

(defn- create-device-keys-repository
  [^Sqlite4cljRepositoryHandle handle]
  (let [serializer (Sqlite4cljSerializers/deviceKeys)]
    (reify DeviceKeysRepository
      (^String serializeKey [_ key] (.getFull ^UserId key))
      (get [_ key _] (minimal-get handle "device_keys" (.getFull ^UserId key) serializer))
      (save [_ key value _]
        (minimal-save! handle "device_keys" (.getFull ^UserId key) (common/encode-json (common/json handle) serializer value)))
      (delete [_ key _] (minimal-delete! handle "device_keys" (.getFull ^UserId key)))
      (deleteAll [_ _] (delete-all! handle "device_keys")))))

(defn- create-cross-signing-keys-repository
  [^Sqlite4cljRepositoryHandle handle]
  (let [serializer (Sqlite4cljSerializers/crossSigningKeys)]
    (reify CrossSigningKeysRepository
      (^String serializeKey [_ key] (.getFull ^UserId key))
      (get [_ key _] (minimal-get handle "cross_signing_keys" (.getFull ^UserId key) serializer))
      (save [_ key value _]
        (minimal-save! handle "cross_signing_keys" (.getFull ^UserId key) (common/encode-json (common/json handle) serializer value)))
      (delete [_ key _] (minimal-delete! handle "cross_signing_keys" (.getFull ^UserId key)))
      (deleteAll [_ _] (delete-all! handle "cross_signing_keys")))))

(defn- create-key-verification-state-repository
  [^Sqlite4cljRepositoryHandle handle]
  (let [serializer (Sqlite4cljSerializers/keyVerificationState)
        key->str   (fn [key] (str (.getKeyId ^KeyVerificationStateKey key) (.getKeyAlgorithm ^KeyVerificationStateKey key)))]
    (reify KeyVerificationStateRepository
      (^String serializeKey [_ key] (key->str key))
      (get [_ key _] (minimal-get handle "key_verification_state" (key->str key) serializer))
      (save [_ key value _]
        (minimal-save! handle "key_verification_state" (key->str key) (common/encode-json (common/json handle) serializer value)))
      (delete [_ key _] (minimal-delete! handle "key_verification_state" (key->str key)))
      (deleteAll [_ _] (delete-all! handle "key_verification_state")))))

(defn- create-secrets-repository
  [^Sqlite4cljRepositoryHandle handle]
  (let [serializer (Sqlite4cljSerializers/secrets)]
    (reify SecretsRepository
      (^String serializeKey [_ key] (str key))
      (get [_ key _] (minimal-get handle "secrets" (str key) serializer))
      (save [_ key value _]
        (minimal-save! handle "secrets" (str key) (common/encode-json (common/json handle) serializer value)))
      (delete [_ key _] (minimal-delete! handle "secrets" (str key)))
      (deleteAll [_ _] (delete-all! handle "secrets")))))

(defn- create-olm-account-repository
  [^Sqlite4cljRepositoryHandle handle]
  (reify OlmAccountRepository
    (^String serializeKey [_ key] (str key))
    (get [_ key _] (common/first-row (common/q-read handle ["SELECT payload FROM olm_account WHERE repo_key = ?" (str key)])))
    (save [_ key value _] (minimal-save! handle "olm_account" (str key) value))
    (delete [_ key _] (minimal-delete! handle "olm_account" (str key)))
    (deleteAll [_ _] (delete-all! handle "olm_account"))))

(defn- create-olm-forget-fallback-key-after-repository
  [^Sqlite4cljRepositoryHandle handle]
  (let [serializer (Sqlite4cljSerializers/forgetFallbackKeyAfter)]
    (reify OlmForgetFallbackKeyAfterRepository
      (^String serializeKey [_ key] (str key))
      (get [_ key _] (minimal-get handle "olm_forget_fallback_key_after" (str key) serializer))
      (save [_ key value _]
        (minimal-save! handle "olm_forget_fallback_key_after" (str key) (common/encode-json (common/json handle) serializer value)))
      (delete [_ key _] (minimal-delete! handle "olm_forget_fallback_key_after" (str key)))
      (deleteAll [_ _] (delete-all! handle "olm_forget_fallback_key_after")))))

(defn- create-olm-session-repository
  [^Sqlite4cljRepositoryHandle handle]
  (let [serializer (Sqlite4cljSerializers/olmSessions)
        key->str   (fn [key] (.getValue ^KeyValue$Curve25519KeyValue key))]
    (reify OlmSessionRepository
      (^String serializeKey [_ key] (key->str key))
      (get [_ key _] (minimal-get handle "olm_session" (key->str key) serializer))
      (getAll [_ _] (full-get-all handle "olm_session" serializer))
      (save [_ key value _]
        (minimal-save! handle "olm_session" (key->str key) (common/encode-json (common/json handle) serializer value)))
      (delete [_ key _] (minimal-delete! handle "olm_session" (key->str key)))
      (deleteAll [_ _] (delete-all! handle "olm_session")))))

(defn- create-inbound-megolm-message-index-repository
  [^Sqlite4cljRepositoryHandle handle]
  (let [serializer (Sqlite4cljSerializers/inboundMegolmMessageIndex)
        key->str   (fn [key]
                     (str (Sqlite4cljModelBridge/inboundMegolmMessageIndexRoomId ^InboundMegolmMessageIndexRepositoryKey key)
                          (.getSessionId ^InboundMegolmMessageIndexRepositoryKey key)
                          (.getMessageIndex ^InboundMegolmMessageIndexRepositoryKey key)))]
    (reify InboundMegolmMessageIndexRepository
      (^String serializeKey [_ key] (key->str key))
      (get [_ key _] (minimal-get handle "inbound_megolm_message_index" (key->str key) serializer))
      (save [_ key value _]
        (minimal-save! handle "inbound_megolm_message_index" (key->str key) (common/encode-json (common/json handle) serializer value)))
      (delete [_ key _] (minimal-delete! handle "inbound_megolm_message_index" (key->str key)))
      (deleteAll [_ _] (delete-all! handle "inbound_megolm_message_index")))))

(defn- create-inbound-megolm-session-repository
  [^Sqlite4cljRepositoryHandle handle]
  (let [serializer (Sqlite4cljSerializers/inboundMegolmSession)]
    (reify InboundMegolmSessionRepository
      (^String serializeKey [_ key] (str (Sqlite4cljModelBridge/inboundMegolmSessionRoomId ^InboundMegolmSessionRepositoryKey key)
                                         (.getSessionId ^InboundMegolmSessionRepositoryKey key)))
      (get [_ key _]
        (when-let [payload (common/first-row
                            (common/q-read handle
                                           ["SELECT payload FROM inbound_megolm_session WHERE room_id = ? AND session_id = ?"
                                            (Sqlite4cljModelBridge/inboundMegolmSessionRoomId ^InboundMegolmSessionRepositoryKey key)
                                            (.getSessionId ^InboundMegolmSessionRepositoryKey key)]))]
          (common/decode-json (common/json handle) serializer payload)))
      (getAll [_ _] (full-get-all handle "inbound_megolm_session" serializer))
      (getByNotBackedUp [_ _]
        (set (map (fn [payload]
                    (common/decode-json (common/json handle) serializer payload))
                  (common/maybe-rows
                   (common/q-read handle ["SELECT payload FROM inbound_megolm_session WHERE has_been_backed_up = 0"])))))
      (save [_ key value _]
        (common/q-write handle
                        [(str "INSERT INTO inbound_megolm_session (room_id, session_id, has_been_backed_up, payload) VALUES (?, ?, ?, ?) "
                              "ON CONFLICT(room_id, session_id) DO UPDATE SET has_been_backed_up = excluded.has_been_backed_up, payload = excluded.payload")
                         (Sqlite4cljModelBridge/inboundMegolmSessionRoomId ^InboundMegolmSessionRepositoryKey key)
                         (.getSessionId ^InboundMegolmSessionRepositoryKey key)
                         (if (.getHasBeenBackedUp ^StoredInboundMegolmSession value) 1 0)
                         (common/encode-json (common/json handle) serializer value)])
        nil)
      (delete [_ key _]
        (common/q-write handle
                        ["DELETE FROM inbound_megolm_session WHERE room_id = ? AND session_id = ?"
                         (Sqlite4cljModelBridge/inboundMegolmSessionRoomId ^InboundMegolmSessionRepositoryKey key)
                         (.getSessionId ^InboundMegolmSessionRepositoryKey key)])
        nil)
      (deleteAll [_ _] (delete-all! handle "inbound_megolm_session")))))

(defn- create-outbound-megolm-session-repository
  [^Sqlite4cljRepositoryHandle handle]
  (let [serializer (Sqlite4cljSerializers/outboundMegolmSession)]
    (reify OutboundMegolmSessionRepository
      (^String serializeKey [_ key] (.getFull ^RoomId key))
      (get [_ key _] (minimal-get handle "outbound_megolm_session" (.getFull ^RoomId key) serializer))
      (getAll [_ _] (full-get-all handle "outbound_megolm_session" serializer))
      (save [_ key value _]
        (minimal-save! handle "outbound_megolm_session" (.getFull ^RoomId key) (common/encode-json (common/json handle) serializer value)))
      (delete [_ key _] (minimal-delete! handle "outbound_megolm_session" (.getFull ^RoomId key)))
      (deleteAll [_ _] (delete-all! handle "outbound_megolm_session")))))

(defn- create-room-repository
  [^Sqlite4cljRepositoryHandle handle]
  (let [serializer (Sqlite4cljSerializers/room)]
    (reify RoomRepository
      (^String serializeKey [_ key] (.getFull ^RoomId key))
      (get [_ key _] (minimal-get handle "room" (.getFull ^RoomId key) serializer))
      (getAll [_ _] (full-get-all handle "room" serializer))
      (save [_ key value _]
        (minimal-save! handle "room" (.getFull ^RoomId key) (common/encode-json (common/json handle) serializer value)))
      (delete [_ key _] (minimal-delete! handle "room" (.getFull ^RoomId key)))
      (deleteAll [_ _] (delete-all! handle "room")))))

(defn- create-room-user-repository
  [^Sqlite4cljRepositoryHandle handle]
  (let [serializer (Sqlite4cljSerializers/roomUser)]
    (reify RoomUserRepositoryOps
      (^String serializeKey [_ first-key second-key] (str (room-id-str first-key) (user-id-str second-key)))
      (get [_ first-key _]
        (into {}
              (map (fn [[user-id payload]]
                     [(Sqlite4cljModelBridge/boxedUserId user-id)
                      (common/decode-json (common/json handle) serializer payload)]))
              (common/maybe-rows
               (common/q-read handle ["SELECT user_id, payload FROM room_user WHERE room_id = ?" (room-id-str first-key)]))))
      (get [_ first-key second-key _]
        (when-let [payload (common/first-row
                            (common/q-read handle ["SELECT payload FROM room_user WHERE room_id = ? AND user_id = ?"
                                                   (room-id-str first-key)
                                                   (user-id-str second-key)]))]
          (common/decode-json (common/json handle) serializer payload)))
      (save [_ first-key second-key value _]
        (common/q-write handle
                        [(str "INSERT INTO room_user (room_id, user_id, payload) VALUES (?, ?, ?) "
                              "ON CONFLICT(room_id, user_id) DO UPDATE SET payload = excluded.payload")
                         (room-id-str first-key)
                         (user-id-str second-key)
                         (common/encode-json (common/json handle) serializer value)])
        nil)
      (delete [_ first-key second-key _]
        (common/q-write handle ["DELETE FROM room_user WHERE room_id = ? AND user_id = ?"
                                (room-id-str first-key)
                                (user-id-str second-key)])
        nil)
      (deleteByRoomId [_ room-id _]
        (common/q-write handle ["DELETE FROM room_user WHERE room_id = ?" room-id])
        nil)
      (deleteAll [_ _] (delete-all! handle "room_user")))))

(defn- create-room-user-receipts-repository
  [^Sqlite4cljRepositoryHandle handle]
  (let [serializer (Sqlite4cljSerializers/roomUserReceipts)]
    (reify RoomUserReceiptsRepositoryOps
      (^String serializeKey [_ first-key second-key] (str (room-id-str first-key) (user-id-str second-key)))
      (get [_ first-key _]
        (into {}
              (map (fn [[user-id payload]]
                     [(Sqlite4cljModelBridge/boxedUserId user-id)
                      (common/decode-json (common/json handle) serializer payload)]))
              (common/maybe-rows
               (common/q-read handle ["SELECT user_id, payload FROM room_user_receipts WHERE room_id = ?" (room-id-str first-key)]))))
      (get [_ first-key second-key _]
        (when-let [payload (common/first-row
                            (common/q-read handle ["SELECT payload FROM room_user_receipts WHERE room_id = ? AND user_id = ?"
                                                   (room-id-str first-key)
                                                   (user-id-str second-key)]))]
          (common/decode-json (common/json handle) serializer payload)))
      (save [_ first-key second-key value _]
        (common/q-write handle
                        [(str "INSERT INTO room_user_receipts (room_id, user_id, payload) VALUES (?, ?, ?) "
                              "ON CONFLICT(room_id, user_id) DO UPDATE SET payload = excluded.payload")
                         (room-id-str first-key)
                         (user-id-str second-key)
                         (common/encode-json (common/json handle) serializer value)])
        nil)
      (delete [_ first-key second-key _]
        (common/q-write handle ["DELETE FROM room_user_receipts WHERE room_id = ? AND user_id = ?"
                                (room-id-str first-key)
                                (user-id-str second-key)])
        nil)
      (deleteByRoomId [_ room-id _]
        (common/q-write handle ["DELETE FROM room_user_receipts WHERE room_id = ?" room-id])
        nil)
      (deleteAll [_ _] (delete-all! handle "room_user_receipts")))))

(defn- create-room-state-repository
  [^Sqlite4cljRepositoryHandle handle]
  (let [serializer (Sqlite4cljSerializers/roomState (common/json handle))]
    (reify RoomStateRepositoryOps
      (^String serializeKey [_ first-key second-key]
        (str (Sqlite4cljModelBridge/roomStateRoomId ^RoomStateRepositoryKey first-key)
             (.getType ^RoomStateRepositoryKey first-key)
             second-key))
      (get [_ first-key _]
        (into {}
              (map (fn [[state-key payload]]
                     [state-key (common/decode-json (common/json handle) serializer payload)]))
              (common/maybe-rows
               (common/q-read handle ["SELECT state_key, payload FROM room_state WHERE room_id = ? AND type = ?"
                                      (Sqlite4cljModelBridge/roomStateRoomId ^RoomStateRepositoryKey first-key)
                                      (.getType ^RoomStateRepositoryKey first-key)]))))
      (get [_ first-key second-key _]
        (when-let [payload (common/first-row
                            (common/q-read handle ["SELECT payload FROM room_state WHERE room_id = ? AND type = ? AND state_key = ?"
                                                   (Sqlite4cljModelBridge/roomStateRoomId ^RoomStateRepositoryKey first-key)
                                                   (.getType ^RoomStateRepositoryKey first-key)
                                                   second-key]))]
          (common/decode-json (common/json handle) serializer payload)))
      (getByRooms [_ room-ids type state-key _]
        (let [room-ids (vec room-ids)]
          (if (empty? room-ids)
            []
            (let [sql   (str "SELECT payload FROM room_state WHERE room_id IN ("
                             (common/in-placeholders (count room-ids))
                             ") AND type = ? AND state_key = ?")
                  query (into [sql]
                              (concat (map #(.getFull ^RoomId %) room-ids)
                                      [type state-key]))]
              (mapv (fn [payload]
                      (common/decode-json (common/json handle) serializer payload))
                    (common/maybe-rows (common/q-read handle query)))))))
      (save [_ first-key second-key value _]
        (common/q-write handle
                        [(str "INSERT INTO room_state (room_id, type, state_key, payload) VALUES (?, ?, ?, ?) "
                              "ON CONFLICT(room_id, type, state_key) DO UPDATE SET payload = excluded.payload")
                         (Sqlite4cljModelBridge/roomStateRoomId ^RoomStateRepositoryKey first-key)
                         (.getType ^RoomStateRepositoryKey first-key)
                         second-key
                         (common/encode-json (common/json handle) serializer value)])
        nil)
      (delete [_ first-key second-key _]
        (common/q-write handle ["DELETE FROM room_state WHERE room_id = ? AND type = ? AND state_key = ?"
                                (Sqlite4cljModelBridge/roomStateRoomId ^RoomStateRepositoryKey first-key)
                                (.getType ^RoomStateRepositoryKey first-key)
                                second-key])
        nil)
      (deleteByRoomId [_ room-id _]
        (common/q-write handle ["DELETE FROM room_state WHERE room_id = ?" room-id])
        nil)
      (deleteAll [_ _] (delete-all! handle "room_state")))))

(defn- create-timeline-event-repository
  [^Sqlite4cljRepositoryHandle handle]
  (let [serializer (Sqlite4cljSerializers/timelineEvent (common/json handle))]
    (reify TimelineEventRepositoryOps
      (^String serializeKey [_ key] (str (Sqlite4cljModelBridge/timelineEventRoomId ^TimelineEventKey key)
                                         (Sqlite4cljModelBridge/timelineEventEventId ^TimelineEventKey key)))
      (get [_ key _]
        (when-let [payload (common/first-row
                            (common/q-read handle ["SELECT payload FROM timeline_event WHERE room_id = ? AND event_id = ?"
                                                   (Sqlite4cljModelBridge/timelineEventRoomId ^TimelineEventKey key)
                                                   (Sqlite4cljModelBridge/timelineEventEventId ^TimelineEventKey key)]))]
          (common/decode-json (common/json handle) serializer payload)))
      (save [_ key value _]
        (common/q-write handle
                        [(str "INSERT INTO timeline_event (room_id, event_id, payload) VALUES (?, ?, ?) "
                              "ON CONFLICT(room_id, event_id) DO UPDATE SET payload = excluded.payload")
                         (Sqlite4cljModelBridge/timelineEventRoomId ^TimelineEventKey key)
                         (Sqlite4cljModelBridge/timelineEventEventId ^TimelineEventKey key)
                         (common/encode-json (common/json handle) serializer value)])
        nil)
      (delete [_ key _]
        (common/q-write handle ["DELETE FROM timeline_event WHERE room_id = ? AND event_id = ?"
                                (Sqlite4cljModelBridge/timelineEventRoomId ^TimelineEventKey key)
                                (Sqlite4cljModelBridge/timelineEventEventId ^TimelineEventKey key)])
        nil)
      (deleteByRoomId [_ room-id _]
        (common/q-write handle ["DELETE FROM timeline_event WHERE room_id = ?" room-id])
        nil)
      (deleteAll [_ _] (delete-all! handle "timeline_event")))))

(defn- create-timeline-event-relation-repository
  [^Sqlite4cljRepositoryHandle handle]
  (let [serializer (Sqlite4cljSerializers/timelineEventRelation)]
    (reify TimelineEventRelationRepositoryOps
      (^String serializeKey [_ first-key second-key]
        (str (Sqlite4cljModelBridge/timelineEventRelationRoomId ^TimelineEventRelationKey first-key)
             (Sqlite4cljModelBridge/timelineEventRelationRelatedEventId ^TimelineEventRelationKey first-key)
             (.getRelationType ^TimelineEventRelationKey first-key)
             (event-id-str second-key)))
      (get [_ first-key _]
        (into {}
              (map (fn [[event-id payload]]
                     [(Sqlite4cljModelBridge/boxedEventId event-id)
                      (common/decode-json (common/json handle) serializer payload)]))
              (common/maybe-rows
               (common/q-read handle ["SELECT event_id, payload FROM timeline_event_relation WHERE room_id = ? AND related_event_id = ? AND relation_type = ?"
                                      (Sqlite4cljModelBridge/timelineEventRelationRoomId ^TimelineEventRelationKey first-key)
                                      (Sqlite4cljModelBridge/timelineEventRelationRelatedEventId ^TimelineEventRelationKey first-key)
                                      (str (.getRelationType ^TimelineEventRelationKey first-key))]))))
      (get [_ first-key second-key _]
        (when-let [payload (common/first-row
                            (common/q-read handle ["SELECT payload FROM timeline_event_relation WHERE room_id = ? AND related_event_id = ? AND relation_type = ? AND event_id = ?"
                                                   (Sqlite4cljModelBridge/timelineEventRelationRoomId ^TimelineEventRelationKey first-key)
                                                   (Sqlite4cljModelBridge/timelineEventRelationRelatedEventId ^TimelineEventRelationKey first-key)
                                                   (str (.getRelationType ^TimelineEventRelationKey first-key))
                                                   (event-id-str second-key)]))]
          (common/decode-json (common/json handle) serializer payload)))
      (save [_ first-key second-key value _]
        (common/q-write handle
                        [(str "INSERT INTO timeline_event_relation (room_id, related_event_id, relation_type, event_id, payload) VALUES (?, ?, ?, ?, ?) "
                              "ON CONFLICT(room_id, related_event_id, relation_type, event_id) DO UPDATE SET payload = excluded.payload")
                         (Sqlite4cljModelBridge/timelineEventRelationRoomId ^TimelineEventRelationKey first-key)
                         (Sqlite4cljModelBridge/timelineEventRelationRelatedEventId ^TimelineEventRelationKey first-key)
                         (str (.getRelationType ^TimelineEventRelationKey first-key))
                         (event-id-str second-key)
                         (common/encode-json (common/json handle) serializer value)])
        nil)
      (delete [_ first-key second-key _]
        (common/q-write handle ["DELETE FROM timeline_event_relation WHERE room_id = ? AND related_event_id = ? AND relation_type = ? AND event_id = ?"
                                (Sqlite4cljModelBridge/timelineEventRelationRoomId ^TimelineEventRelationKey first-key)
                                (Sqlite4cljModelBridge/timelineEventRelationRelatedEventId ^TimelineEventRelationKey first-key)
                                (str (.getRelationType ^TimelineEventRelationKey first-key))
                                (event-id-str second-key)])
        nil)
      (deleteByRoomId [_ room-id _]
        (common/q-write handle ["DELETE FROM timeline_event_relation WHERE room_id = ?" room-id])
        nil)
      (deleteAll [_ _] (delete-all! handle "timeline_event_relation")))))

(defn- create-media-cache-mapping-repository
  [^Sqlite4cljRepositoryHandle handle]
  (let [serializer (Sqlite4cljSerializers/mediaCacheMapping)]
    (reify MediaCacheMappingRepository
      (^String serializeKey [_ key] (str key))
      (get [_ key _] (minimal-get handle "media_cache_mapping" (str key) serializer))
      (save [_ key value _]
        (minimal-save! handle "media_cache_mapping" (str key) (common/encode-json (common/json handle) serializer value)))
      (delete [_ key _] (minimal-delete! handle "media_cache_mapping" (str key)))
      (deleteAll [_ _] (delete-all! handle "media_cache_mapping")))))

(defn- create-global-account-data-repository
  [^Sqlite4cljRepositoryHandle handle]
  (let [serializer (Sqlite4cljSerializers/globalAccountData (common/json handle))]
    (reify GlobalAccountDataRepository
      (^String serializeKey [_ first-key second-key] (str first-key second-key))
      (get [_ first-key _]
        (into {}
              (map (fn [[item-key payload]]
                     [item-key (common/decode-json (common/json handle) serializer payload)]))
              (common/maybe-rows
               (common/q-read handle ["SELECT item_key, payload FROM global_account_data WHERE type = ?" first-key]))))
      (get [_ first-key second-key _]
        (when-let [payload (common/first-row
                            (common/q-read handle ["SELECT payload FROM global_account_data WHERE type = ? AND item_key = ?"
                                                   first-key second-key]))]
          (common/decode-json (common/json handle) serializer payload)))
      (save [_ first-key second-key value _]
        (common/q-write handle
                        [(str "INSERT INTO global_account_data (type, item_key, payload) VALUES (?, ?, ?) "
                              "ON CONFLICT(type, item_key) DO UPDATE SET payload = excluded.payload")
                         first-key second-key
                         (common/encode-json (common/json handle) serializer value)])
        nil)
      (delete [_ first-key second-key _]
        (common/q-write handle ["DELETE FROM global_account_data WHERE type = ? AND item_key = ?" first-key second-key])
        nil)
      (deleteAll [_ _] (delete-all! handle "global_account_data")))))

(defn- create-user-presence-repository
  [^Sqlite4cljRepositoryHandle handle]
  (let [serializer (Sqlite4cljSerializers/userPresence)]
    (reify UserPresenceRepository
      (^String serializeKey [_ key] (.getFull ^UserId key))
      (get [_ key _] (minimal-get handle "user_presence" (.getFull ^UserId key) serializer))
      (save [_ key value _]
        (minimal-save! handle "user_presence" (.getFull ^UserId key) (common/encode-json (common/json handle) serializer value)))
      (delete [_ key _] (minimal-delete! handle "user_presence" (.getFull ^UserId key)))
      (deleteAll [_ _] (delete-all! handle "user_presence")))))

(defn- create-room-account-data-repository
  [^Sqlite4cljRepositoryHandle handle]
  (let [serializer (Sqlite4cljSerializers/roomAccountData (common/json handle))]
    (reify RoomAccountDataRepositoryOps
      (^String serializeKey [_ first-key second-key]
        (str (Sqlite4cljModelBridge/roomAccountDataRoomId ^RoomAccountDataRepositoryKey first-key)
             (.getType ^RoomAccountDataRepositoryKey first-key)
             second-key))
      (get [_ first-key _]
        (into {}
              (map (fn [[item-key payload]]
                     [item-key (common/decode-json (common/json handle) serializer payload)]))
              (common/maybe-rows
               (common/q-read handle ["SELECT item_key, payload FROM room_account_data WHERE room_id = ? AND type = ?"
                                      (Sqlite4cljModelBridge/roomAccountDataRoomId ^RoomAccountDataRepositoryKey first-key)
                                      (.getType ^RoomAccountDataRepositoryKey first-key)]))))
      (get [_ first-key second-key _]
        (when-let [payload (common/first-row
                            (common/q-read handle ["SELECT payload FROM room_account_data WHERE room_id = ? AND type = ? AND item_key = ?"
                                                   (Sqlite4cljModelBridge/roomAccountDataRoomId ^RoomAccountDataRepositoryKey first-key)
                                                   (.getType ^RoomAccountDataRepositoryKey first-key)
                                                   second-key]))]
          (common/decode-json (common/json handle) serializer payload)))
      (save [_ first-key second-key value _]
        (common/q-write handle
                        [(str "INSERT INTO room_account_data (room_id, type, item_key, payload) VALUES (?, ?, ?, ?) "
                              "ON CONFLICT(room_id, type, item_key) DO UPDATE SET payload = excluded.payload")
                         (Sqlite4cljModelBridge/roomAccountDataRoomId ^RoomAccountDataRepositoryKey first-key)
                         (.getType ^RoomAccountDataRepositoryKey first-key)
                         second-key
                         (common/encode-json (common/json handle) serializer value)])
        nil)
      (delete [_ first-key second-key _]
        (common/q-write handle ["DELETE FROM room_account_data WHERE room_id = ? AND type = ? AND item_key = ?"
                                (Sqlite4cljModelBridge/roomAccountDataRoomId ^RoomAccountDataRepositoryKey first-key)
                                (.getType ^RoomAccountDataRepositoryKey first-key)
                                second-key])
        nil)
      (deleteByRoomId [_ room-id _]
        (common/q-write handle ["DELETE FROM room_account_data WHERE room_id = ?" room-id])
        nil)
      (deleteAll [_ _] (delete-all! handle "room_account_data")))))

(defn- create-secret-key-request-repository
  [^Sqlite4cljRepositoryHandle handle]
  (let [serializer (Sqlite4cljSerializers/secretKeyRequest)]
    (reify SecretKeyRequestRepository
      (^String serializeKey [_ key] key)
      (get [_ key _] (minimal-get handle "secret_key_request" key serializer))
      (getAll [_ _] (full-get-all handle "secret_key_request" serializer))
      (save [_ key value _]
        (minimal-save! handle "secret_key_request" key (common/encode-json (common/json handle) serializer value)))
      (delete [_ key _] (minimal-delete! handle "secret_key_request" key))
      (deleteAll [_ _] (delete-all! handle "secret_key_request")))))

(defn- create-room-key-request-repository
  [^Sqlite4cljRepositoryHandle handle]
  (let [serializer (Sqlite4cljSerializers/roomKeyRequest)]
    (reify RoomKeyRequestRepository
      (^String serializeKey [_ key] key)
      (get [_ key _] (minimal-get handle "room_key_request" key serializer))
      (getAll [_ _] (full-get-all handle "room_key_request" serializer))
      (save [_ key value _]
        (minimal-save! handle "room_key_request" key (common/encode-json (common/json handle) serializer value)))
      (delete [_ key _] (minimal-delete! handle "room_key_request" key))
      (deleteAll [_ _] (delete-all! handle "room_key_request")))))

(defn- create-room-outbox-message-repository
  [^Sqlite4cljRepositoryHandle handle ^EventContentSerializerMappings mappings]
  (let [json (common/json handle)]
    (reify RoomOutboxMessageRepositoryOps
      (^String serializeKey [_ key] (str (Sqlite4cljModelBridge/roomOutboxMessageRoomId ^RoomOutboxMessageRepositoryKey key)
                                         (.getTransactionId ^RoomOutboxMessageRepositoryKey key)))
      (get [_ key _]
        (when-let [[content-type payload]
                   (common/first-row
                    (common/q-read handle ["SELECT content_type, payload FROM room_outbox_message WHERE room_id = ? AND transaction_id = ?"
                                           (Sqlite4cljModelBridge/roomOutboxMessageRoomId ^RoomOutboxMessageRepositoryKey key)
                                           (.getTransactionId ^RoomOutboxMessageRepositoryKey key)]))]
          (common/decode-json json (Sqlite4cljSerializers/roomOutboxSerializer mappings content-type) payload)))
      (getAll [_ _]
        (mapv (fn [[content-type payload]]
                (common/decode-json json (Sqlite4cljSerializers/roomOutboxSerializer mappings content-type) payload))
              (common/maybe-rows
               (common/q-read handle ["SELECT content_type, payload FROM room_outbox_message"]))))
      (save [_ key value _]
        (let [content-type (Sqlite4cljSerializers/roomOutboxContentType mappings value)
              serializer   (Sqlite4cljSerializers/roomOutboxSerializer mappings content-type)]
          (common/q-write handle
                          [(str "INSERT INTO room_outbox_message (room_id, transaction_id, content_type, payload) VALUES (?, ?, ?, ?) "
                                "ON CONFLICT(room_id, transaction_id) DO UPDATE SET content_type = excluded.content_type, payload = excluded.payload")
                           (Sqlite4cljModelBridge/roomOutboxMessageRoomId ^RoomOutboxMessageRepositoryKey key)
                           (.getTransactionId ^RoomOutboxMessageRepositoryKey key)
                           content-type
                           (common/encode-json json serializer value)]))
        nil)
      (delete [_ key _]
        (common/q-write handle ["DELETE FROM room_outbox_message WHERE room_id = ? AND transaction_id = ?"
                                (Sqlite4cljModelBridge/roomOutboxMessageRoomId ^RoomOutboxMessageRepositoryKey key)
                                (.getTransactionId ^RoomOutboxMessageRepositoryKey key)])
        nil)
      (deleteByRoomId [_ room-id _]
        (common/q-write handle ["DELETE FROM room_outbox_message WHERE room_id = ?" room-id])
        nil)
      (deleteAll [_ _] (delete-all! handle "room_outbox_message")))))

(defn- create-notification-repository
  [^Sqlite4cljRepositoryHandle handle]
  (let [serializer (Sqlite4cljSerializers/notification)]
    (reify NotificationRepositoryOps
      (^String serializeKey [_ key] key)
      (get [_ key _] (minimal-get handle "notification" key serializer))
      (getAll [_ _] (full-get-all handle "notification" serializer))
      (save [_ key value _]
        (common/q-write handle
                        [(str "INSERT INTO notification (repo_key, room_id, payload) VALUES (?, ?, ?) "
                              "ON CONFLICT(repo_key) DO UPDATE SET room_id = excluded.room_id, payload = excluded.payload")
                         key
                         (Sqlite4cljModelBridge/notificationRoomId ^StoredNotification value)
                         (common/encode-json (common/json handle) serializer value)])
        nil)
      (delete [_ key _] (minimal-delete! handle "notification" key))
      (deleteByRoomId [_ room-id _]
        (common/q-write handle ["DELETE FROM notification WHERE room_id = ?" room-id])
        nil)
      (deleteAll [_ _] (delete-all! handle "notification")))))

(defn- create-notification-update-repository
  [^Sqlite4cljRepositoryHandle handle]
  (let [serializer (Sqlite4cljSerializers/notificationUpdate)]
    (reify NotificationUpdateRepositoryOps
      (^String serializeKey [_ key] key)
      (get [_ key _] (minimal-get handle "notification_update" key serializer))
      (getAll [_ _] (full-get-all handle "notification_update" serializer))
      (save [_ key value _]
        (common/q-write handle
                        [(str "INSERT INTO notification_update (repo_key, room_id, payload) VALUES (?, ?, ?) "
                              "ON CONFLICT(repo_key) DO UPDATE SET room_id = excluded.room_id, payload = excluded.payload")
                         key
                         (Sqlite4cljModelBridge/notificationUpdateRoomId ^StoredNotificationUpdate value)
                         (common/encode-json (common/json handle) serializer value)])
        nil)
      (delete [_ key _] (minimal-delete! handle "notification_update" key))
      (deleteByRoomId [_ room-id _]
        (common/q-write handle ["DELETE FROM notification_update WHERE room_id = ?" room-id])
        nil)
      (deleteAll [_ _] (delete-all! handle "notification_update")))))

(defn- create-notification-state-repository
  [^Sqlite4cljRepositoryHandle handle]
  (let [serializer (Sqlite4cljSerializers/notificationState)]
    (reify NotificationStateRepository
      (^String serializeKey [_ key] (.getFull ^RoomId key))
      (get [_ key _] (minimal-get handle "notification_state" (.getFull ^RoomId key) serializer))
      (getAll [_ _] (full-get-all handle "notification_state" serializer))
      (save [_ key value _]
        (minimal-save! handle "notification_state" (.getFull ^RoomId key) (common/encode-json (common/json handle) serializer value)))
      (delete [_ key _] (minimal-delete! handle "notification_state" (.getFull ^RoomId key)))
      (deleteAll [_ _] (delete-all! handle "notification_state")))))

(defn- create-key-chain-link-repository
  [^Sqlite4cljRepositoryHandle handle]
  (let [encode-key (fn [^Key$Ed25519Key key]
                     (str (or (Sqlite4cljModelBridge/ed25519KeyId key) "") "|" (Sqlite4cljModelBridge/ed25519KeyValue key)))
        decode-key (fn [^String encoded]
                     (let [[key-id value] (.split encoded "\\|" 2)]
                       (Sqlite4cljModelBridge/ed25519Key (not-empty key-id) value)))]
    (reify KeyChainLinkRepositoryOps
      (save [_ key-chain-link _]
        (common/q-write handle
                        [(str "INSERT INTO key_chain_link (signing_user_id, signing_key, signed_user_id, signed_key, payload) VALUES (?, ?, ?, ?, ?) "
                              "ON CONFLICT(signing_user_id, signing_key, signed_user_id, signed_key) DO UPDATE SET payload = excluded.payload")
                         (Sqlite4cljModelBridge/keyChainLinkSigningUserId ^KeyChainLink key-chain-link)
                         (encode-key (.getSigningKey ^KeyChainLink key-chain-link))
                         (Sqlite4cljModelBridge/keyChainLinkSignedUserId ^KeyChainLink key-chain-link)
                         (encode-key (.getSignedKey ^KeyChainLink key-chain-link))
                         ""])
        nil)
      (getBySigningKey [_ signing-user-id signing-key _]
        (set (map (fn [[signed-user-id signed-key]]
                    (Sqlite4cljModelBridge/keyChainLink signing-user-id signing-key signed-user-id (decode-key signed-key)))
                  (common/maybe-rows
                   (common/q-read handle ["SELECT signed_user_id, signed_key FROM key_chain_link WHERE signing_user_id = ? AND signing_key = ?"
                                          signing-user-id
                                          (encode-key ^Key$Ed25519Key signing-key)])))))
      (deleteBySignedKey [_ signed-user-id signed-key _]
        (common/q-write handle ["DELETE FROM key_chain_link WHERE signed_user_id = ? AND signed_key = ?"
                                signed-user-id
                                (encode-key ^Key$Ed25519Key signed-key)])
        nil)
      (deleteAll [_ _] (delete-all! handle "key_chain_link")))))

(defn- create-migration-repository
  [^Sqlite4cljRepositoryHandle handle]
  (reify MigrationRepository
    (^String serializeKey [_ key] key)
    (get [_ key _] (common/first-row (common/q-read handle ["SELECT payload FROM migration WHERE repo_key = ?" key])))
    (save [_ key value _] (minimal-save! handle "migration" key value))
    (delete [_ key _] (minimal-delete! handle "migration" key))
    (deleteAll [_ _] (delete-all! handle "migration"))))

(defn create-repositories
  [^Sqlite4cljRepositoryHandle handle ^EventContentSerializerMappings mappings]
  {:account                       (create-account-repository handle)
   :authentication                (create-authentication-repository handle)
   :server-data                   (create-server-data-repository handle)
   :outdated-keys                 (create-outdated-keys-repository handle)
   :device-keys                   (create-device-keys-repository handle)
   :cross-signing-keys            (create-cross-signing-keys-repository handle)
   :key-verification-state        (create-key-verification-state-repository handle)
   :key-chain-link                (create-key-chain-link-repository handle)
   :secrets                       (create-secrets-repository handle)
   :secret-key-request            (create-secret-key-request-repository handle)
   :room-key-request              (create-room-key-request-repository handle)
   :olm-account                   (create-olm-account-repository handle)
   :olm-forget-fallback-key-after (create-olm-forget-fallback-key-after-repository handle)
   :olm-session                   (create-olm-session-repository handle)
   :inbound-megolm-session        (create-inbound-megolm-session-repository handle)
   :inbound-megolm-message-index  (create-inbound-megolm-message-index-repository handle)
   :outbound-megolm-session       (create-outbound-megolm-session-repository handle)
   :room                          (create-room-repository handle)
   :room-user                     (create-room-user-repository handle)
   :room-user-receipts            (create-room-user-receipts-repository handle)
   :room-state                    (create-room-state-repository handle)
   :timeline-event                (create-timeline-event-repository handle)
   :timeline-event-relation       (create-timeline-event-relation-repository handle)
   :room-outbox-message           (create-room-outbox-message-repository handle mappings)
   :media-cache-mapping           (create-media-cache-mapping-repository handle)
   :global-account-data           (create-global-account-data-repository handle)
   :room-account-data             (create-room-account-data-repository handle)
   :user-presence                 (create-user-presence-repository handle)
   :notification                  (create-notification-repository handle)
   :notification-state            (create-notification-state-repository handle)
   :notification-update           (create-notification-update-repository handle)
   :migration                     (create-migration-repository handle)})
