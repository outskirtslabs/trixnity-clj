(ns ol.trixnity.internal.bridge
  (:import
   [de.connect2x.trixnity.client MatrixClient]
   [de.connect2x.trixnity.client.store TimelineEvent]
   [de.connect2x.trixnity.clientserverapi.model.sync Sync$Response]
   [java.time Duration]
   [ol.trixnity.bridge ClientBridge
    KeyBridge
    NotificationBridge
    RoomBridge
    TimelineBridge
    UserBridge
    VerificationBridge]))

(set! *warn-on-reflection* true)

(defn open-client [request on-success on-failure]
  (ClientBridge/openClient request on-success on-failure))

(defn start-sync [^MatrixClient client on-success on-failure]
  (ClientBridge/startSync client on-success on-failure))

(defn await-running [^MatrixClient client ^Duration timeout on-success on-failure]
  (ClientBridge/awaitRunning client timeout on-success on-failure))

(defn stop-sync [^MatrixClient client on-success on-failure]
  (ClientBridge/stopSync client on-success on-failure))

(defn close-client [^MatrixClient client on-success on-failure]
  (ClientBridge/closeClient client on-success on-failure))

(defn current-user-id [^MatrixClient client]
  (ClientBridge/currentUserId client))

(defn current-sync-state [^MatrixClient client]
  (ClientBridge/currentSyncState client))

(defn current-profile [^MatrixClient client]
  (ClientBridge/currentProfile client))

(defn profile-flow [^MatrixClient client]
  (ClientBridge/profileFlow client))

(defn current-server-data [^MatrixClient client]
  (ClientBridge/currentServerData client))

(defn server-data-flow [^MatrixClient client]
  (ClientBridge/serverDataFlow client))

(defn sync-state-flow [^MatrixClient client]
  (ClientBridge/syncStateFlow client))

(defn current-initial-sync-done [^MatrixClient client]
  (ClientBridge/currentInitialSyncDone client))

(defn initial-sync-done-flow [^MatrixClient client]
  (ClientBridge/initialSyncDoneFlow client))

(defn current-login-state [^MatrixClient client]
  (ClientBridge/currentLoginState client))

(defn login-state-flow [^MatrixClient client]
  (ClientBridge/loginStateFlow client))

(defn current-started [^MatrixClient client]
  (ClientBridge/currentStarted client))

(defn started-flow [^MatrixClient client]
  (ClientBridge/startedFlow client))

(defn current-users-typing [^MatrixClient client]
  (RoomBridge/currentUsersTyping client))

(defn users-typing-flow [^MatrixClient client]
  (RoomBridge/usersTypingFlow client))

(defn create-room [^MatrixClient client room-name on-success on-failure]
  (RoomBridge/createRoom client room-name on-success on-failure))

(defn invite-user [^MatrixClient client room-id user-id ^Duration timeout on-success on-failure]
  (RoomBridge/inviteUser client room-id user-id timeout on-success on-failure))

(defn send-message [^MatrixClient client room-id message ^Duration timeout on-success on-failure]
  (RoomBridge/sendMessage client room-id message timeout on-success on-failure))

(defn send-reaction [^MatrixClient client room-id event-id key ^Duration timeout on-success on-failure]
  (RoomBridge/sendReaction client room-id event-id key timeout on-success on-failure))

(defn room-by-id [^MatrixClient client room-id]
  (RoomBridge/roomById client room-id))

(defn rooms [^MatrixClient client]
  (RoomBridge/rooms client))

(defn rooms-flat [^MatrixClient client]
  (RoomBridge/roomsFlat client))

(defn account-data [^MatrixClient client room-id event-content-class key]
  (RoomBridge/accountData client room-id event-content-class key))

(defn state [^MatrixClient client room-id event-content-class state-key]
  (RoomBridge/state client room-id event-content-class state-key))

(defn all-state [^MatrixClient client room-id event-content-class]
  (RoomBridge/allState client room-id event-content-class))

(defn outbox [^MatrixClient client]
  (RoomBridge/outbox client))

(defn outbox-flat [^MatrixClient client]
  (RoomBridge/outboxFlat client))

(defn outbox-by-room [^MatrixClient client room-id]
  (RoomBridge/outboxByRoom client room-id))

(defn outbox-by-room-flat [^MatrixClient client room-id]
  (RoomBridge/outboxByRoomFlat client room-id))

(defn outbox-message [^MatrixClient client room-id transaction-id]
  (RoomBridge/outboxMessage client room-id transaction-id))

(defn timeline-events-from-now-on
  [^MatrixClient client decryption-timeout-ms sync-response-buffer-size]
  (TimelineBridge/timelineEventsFromNowOn
   client
   decryption-timeout-ms
   sync-response-buffer-size))

(defn response-timeline-events [^MatrixClient client ^Sync$Response response decryption-timeout-ms]
  (TimelineBridge/timelineEvents client response decryption-timeout-ms))

(defn timeline-event
  [^MatrixClient client room-id event-id decryption-timeout-ms fetch-timeout-ms fetch-size allow-replace-content]
  (TimelineBridge/timelineEvent
   client
   room-id
   event-id
   decryption-timeout-ms
   fetch-timeout-ms
   fetch-size
   allow-replace-content))

(defn previous-timeline-event
  [^MatrixClient client ^TimelineEvent timeline-event decryption-timeout-ms fetch-timeout-ms fetch-size allow-replace-content]
  (TimelineBridge/previousTimelineEvent
   client
   timeline-event
   decryption-timeout-ms
   fetch-timeout-ms
   fetch-size
   allow-replace-content))

(defn next-timeline-event
  [^MatrixClient client ^TimelineEvent timeline-event decryption-timeout-ms fetch-timeout-ms fetch-size allow-replace-content]
  (TimelineBridge/nextTimelineEvent
   client
   timeline-event
   decryption-timeout-ms
   fetch-timeout-ms
   fetch-size
   allow-replace-content))

(defn last-timeline-event
  [^MatrixClient client room-id decryption-timeout-ms fetch-timeout-ms fetch-size allow-replace-content]
  (TimelineBridge/lastTimelineEvent
   client
   room-id
   decryption-timeout-ms
   fetch-timeout-ms
   fetch-size
   allow-replace-content))

(defn timeline-event-chain
  [^MatrixClient client room-id start-from direction decryption-timeout-ms fetch-timeout-ms fetch-size allow-replace-content min-size max-size]
  (TimelineBridge/timelineEventChain
   client
   room-id
   start-from
   direction
   decryption-timeout-ms
   fetch-timeout-ms
   fetch-size
   allow-replace-content
   min-size
   max-size))

(defn last-timeline-events
  [^MatrixClient client room-id decryption-timeout-ms fetch-timeout-ms fetch-size allow-replace-content min-size max-size]
  (TimelineBridge/lastTimelineEvents
   client
   room-id
   decryption-timeout-ms
   fetch-timeout-ms
   fetch-size
   allow-replace-content
   min-size
   max-size))

(defn timeline-events-list
  [^MatrixClient client room-id start-from direction max-size min-size decryption-timeout-ms fetch-timeout-ms fetch-size allow-replace-content]
  (TimelineBridge/timelineEventsList
   client
   room-id
   start-from
   direction
   max-size
   min-size
   decryption-timeout-ms
   fetch-timeout-ms
   fetch-size
   allow-replace-content))

(defn last-timeline-events-list
  [^MatrixClient client room-id max-size min-size decryption-timeout-ms fetch-timeout-ms fetch-size allow-replace-content]
  (TimelineBridge/lastTimelineEventsList
   client
   room-id
   max-size
   min-size
   decryption-timeout-ms
   fetch-timeout-ms
   fetch-size
   allow-replace-content))

(defn timeline-events-around
  [^MatrixClient client room-id start-from max-size-before max-size-after decryption-timeout-ms fetch-timeout-ms fetch-size allow-replace-content]
  (TimelineBridge/timelineEventsAround
   client
   room-id
   start-from
   max-size-before
   max-size-after
   decryption-timeout-ms
   fetch-timeout-ms
   fetch-size
   allow-replace-content))

(defn timeline-event-relations [^MatrixClient client room-id event-id relation-type]
  (TimelineBridge/timelineEventRelations client room-id event-id relation-type))

(defn user-all [^MatrixClient client room-id]
  (UserBridge/all client room-id))

(defn user-by-id [^MatrixClient client room-id user-id]
  (UserBridge/byId client room-id user-id))

(defn user-all-receipts [^MatrixClient client room-id]
  (UserBridge/allReceipts client room-id))

(defn user-receipts-by-id [^MatrixClient client room-id user-id]
  (UserBridge/receiptsById client room-id user-id))

(defn user-power-level [^MatrixClient client room-id user-id]
  (UserBridge/powerLevel client room-id user-id))

(defn can-kick-user [^MatrixClient client room-id user-id]
  (UserBridge/canKickUser client room-id user-id))

(defn can-ban-user [^MatrixClient client room-id user-id]
  (UserBridge/canBanUser client room-id user-id))

(defn can-unban-user [^MatrixClient client room-id user-id]
  (UserBridge/canUnbanUser client room-id user-id))

(defn can-invite-user [^MatrixClient client room-id user-id]
  (UserBridge/canInviteUser client room-id user-id))

(defn can-invite [^MatrixClient client room-id]
  (UserBridge/canInvite client room-id))

(defn can-redact-event [^MatrixClient client room-id event-id]
  (UserBridge/canRedactEvent client room-id event-id))

(defn can-set-power-level-to-max [^MatrixClient client room-id user-id]
  (UserBridge/canSetPowerLevelToMax client room-id user-id))

(defn can-send-event-by-class [^MatrixClient client room-id event-content-class]
  (UserBridge/canSendEventByClass client room-id event-content-class))

(defn can-send-event-by-content [^MatrixClient client room-id event-content]
  (UserBridge/canSendEventByContent client room-id event-content))

(defn user-presence [^MatrixClient client user-id]
  (UserBridge/presence client user-id))

(defn user-account-data [^MatrixClient client event-content-class key]
  (UserBridge/accountData client event-content-class key))

(defn notifications [^MatrixClient client decryption-timeout-ms sync-response-buffer-size]
  (NotificationBridge/notifications client decryption-timeout-ms sync-response-buffer-size))

(defn notifications-from-response [^MatrixClient client ^Sync$Response response decryption-timeout-ms]
  (NotificationBridge/notificationsFromResponse client response decryption-timeout-ms))

(defn notification-all [^MatrixClient client]
  (NotificationBridge/all client))

(defn notification-all-flat [^MatrixClient client]
  (NotificationBridge/allFlat client))

(defn notification-by-id [^MatrixClient client id]
  (NotificationBridge/byId client id))

(defn notification-count
  ([^MatrixClient client]
   (NotificationBridge/count client))
  ([^MatrixClient client room-id]
   (NotificationBridge/count client room-id)))

(defn notification-unread [^MatrixClient client room-id]
  (NotificationBridge/unread client room-id))

(defn notification-all-updates [^MatrixClient client]
  (NotificationBridge/allUpdates client))

(defn current-active-device-verification [^MatrixClient client]
  (VerificationBridge/currentActiveDeviceVerification client))

(defn active-device-verification-flow [^MatrixClient client]
  (VerificationBridge/activeDeviceVerificationFlow client))

(defn current-active-user-verifications [^MatrixClient client]
  (VerificationBridge/currentActiveUserVerifications client))

(defn active-user-verifications-flow [^MatrixClient client]
  (VerificationBridge/activeUserVerificationsFlow client))

(defn self-verification-methods [^MatrixClient client]
  (VerificationBridge/selfVerificationMethods client))

(defn current-bootstrap-running [^MatrixClient client]
  (KeyBridge/currentBootstrapRunning client))

(defn bootstrap-running-flow [^MatrixClient client]
  (KeyBridge/bootstrapRunningFlow client))

(defn current-backup-version [^MatrixClient client]
  (KeyBridge/currentBackupVersion client))

(defn backup-version-flow [^MatrixClient client]
  (KeyBridge/backupVersionFlow client))

(defn device-trust-level [^MatrixClient client user-id device-id]
  (KeyBridge/trustLevel client user-id device-id))

(defn timeline-trust-level [^MatrixClient client room-id event-id]
  (KeyBridge/trustLevelByTimelineEvent client room-id event-id))

(defn user-trust-level [^MatrixClient client user-id]
  (KeyBridge/trustLevel client user-id))

(defn device-keys-flow [^MatrixClient client user-id]
  (KeyBridge/deviceKeys client user-id))

(defn cross-signing-keys-flow [^MatrixClient client user-id]
  (KeyBridge/crossSigningKeys client user-id))
