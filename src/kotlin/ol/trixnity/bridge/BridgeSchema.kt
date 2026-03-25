package ol.trixnity.bridge

import clojure.lang.Keyword

internal typealias KeywordMap = Map<Keyword, *>

internal object BridgeSchema {
    private const val schemaNamespace = "ol.trixnity.schemas"

    val id: Keyword = Keyword.intern(schemaNamespace, "id")
    val homeserverUrl: Keyword = Keyword.intern(schemaNamespace, "homeserver-url")
    val password: Keyword = Keyword.intern(schemaNamespace, "password")
    val databasePath: Keyword = Keyword.intern(schemaNamespace, "database-path")
    val mediaPath: Keyword = Keyword.intern(schemaNamespace, "media-path")
    val client: Keyword = Keyword.intern(schemaNamespace, "client")
    val roomName: Keyword = Keyword.intern(schemaNamespace, "room-name")
    val topic: Keyword = Keyword.intern(schemaNamespace, "topic")
    val roomId: Keyword = Keyword.intern(schemaNamespace, "room-id")
    val userId: Keyword = Keyword.intern(schemaNamespace, "user-id")
    val invite: Keyword = Keyword.intern(schemaNamespace, "invite")
    val preset: Keyword = Keyword.intern(schemaNamespace, "preset")
    val visibility: Keyword = Keyword.intern(schemaNamespace, "visibility")
    val users: Keyword = Keyword.intern(schemaNamespace, "users")
    val eventId: Keyword = Keyword.intern(schemaNamespace, "event-id")
    val stateKey: Keyword = Keyword.intern(schemaNamespace, "state-key")
    val transactionId: Keyword = Keyword.intern(schemaNamespace, "transaction-id")
    val key: Keyword = Keyword.intern(schemaNamespace, "key")
    val wait: Keyword = Keyword.intern(schemaNamespace, "wait")
    val force: Keyword = Keyword.intern(schemaNamespace, "force")
    val limit: Keyword = Keyword.intern(schemaNamespace, "limit")
    val deviceName: Keyword = Keyword.intern(schemaNamespace, "device-name")
    val displayName: Keyword = Keyword.intern(schemaNamespace, "display-name")
    val senderDisplayName: Keyword = Keyword.intern(schemaNamespace, "sender-display-name")
    val avatarUrl: Keyword = Keyword.intern(schemaNamespace, "avatar-url")
    val timeZone: Keyword = Keyword.intern(schemaNamespace, "time-zone")
    val versions: Keyword = Keyword.intern(schemaNamespace, "versions")
    val mediaConfig: Keyword = Keyword.intern(schemaNamespace, "media-config")
    val capabilities: Keyword = Keyword.intern(schemaNamespace, "capabilities")
    val auth: Keyword = Keyword.intern(schemaNamespace, "auth")
    val content: Keyword = Keyword.intern(schemaNamespace, "content")
    val message: Keyword = Keyword.intern(schemaNamespace, "message")
    val timeout: Keyword = Keyword.intern(schemaNamespace, "timeout")
    val decryptionTimeout: Keyword = Keyword.intern(schemaNamespace, "decryption-timeout")
    val fetchTimeout: Keyword = Keyword.intern(schemaNamespace, "fetch-timeout")
    val fetchSize: Keyword = Keyword.intern(schemaNamespace, "fetch-size")
    val allowReplaceContent: Keyword = Keyword.intern(schemaNamespace, "allow-replace-content")
    val minSize: Keyword = Keyword.intern(schemaNamespace, "min-size")
    val maxSize: Keyword = Keyword.intern(schemaNamespace, "max-size")
    val syncResponseBufferSize: Keyword = Keyword.intern(schemaNamespace, "sync-response-buffer-size")
    val direction: Keyword = Keyword.intern(schemaNamespace, "direction")
    val response: Keyword = Keyword.intern(schemaNamespace, "response")
    val eventContentClass: Keyword = Keyword.intern(schemaNamespace, "event-content-class")
    val eventContent: Keyword = Keyword.intern(schemaNamespace, "event-content")
    val membership: Keyword = Keyword.intern(schemaNamespace, "membership")
    val isDirect: Keyword = Keyword.intern(schemaNamespace, "is-direct")
    val createdAt: Keyword = Keyword.intern(schemaNamespace, "created-at")
    val sentAt: Keyword = Keyword.intern(schemaNamespace, "sent-at")
    val sendError: Keyword = Keyword.intern(schemaNamespace, "send-error")
    val receipts: Keyword = Keyword.intern(schemaNamespace, "receipts")
    val receiptType: Keyword = Keyword.intern(schemaNamespace, "receipt-type")
    val name: Keyword = Keyword.intern(schemaNamespace, "name")
    val presence: Keyword = Keyword.intern(schemaNamespace, "presence")
    val lastUpdate: Keyword = Keyword.intern(schemaNamespace, "last-update")
    val lastActive: Keyword = Keyword.intern(schemaNamespace, "last-active")
    val currentlyActive: Keyword = Keyword.intern(schemaNamespace, "currently-active")
    val statusMessage: Keyword = Keyword.intern(schemaNamespace, "status-message")
    val level: Keyword = Keyword.intern(schemaNamespace, "level")
    val verified: Keyword = Keyword.intern(schemaNamespace, "verified")
    val reason: Keyword = Keyword.intern(schemaNamespace, "reason")
    val dismissed: Keyword = Keyword.intern(schemaNamespace, "dismissed")
    val sortKey: Keyword = Keyword.intern(schemaNamespace, "sort-key")
    val actions: Keyword = Keyword.intern(schemaNamespace, "actions")
    val notificationKind: Keyword = Keyword.intern(schemaNamespace, "notification-kind")
    val notificationUpdateKind: Keyword = Keyword.intern(schemaNamespace, "notification-update-kind")
    val timestamp: Keyword = Keyword.intern(schemaNamespace, "timestamp")
    val deviceId: Keyword = Keyword.intern(schemaNamespace, "device-id")
    val theirUserId: Keyword = Keyword.intern(schemaNamespace, "their-user-id")
    val theirDeviceId: Keyword = Keyword.intern(schemaNamespace, "their-device-id")
    val requestEventId: Keyword = Keyword.intern(schemaNamespace, "request-event-id")
    val verificationState: Keyword = Keyword.intern(schemaNamespace, "verification-state")
    val methods: Keyword = Keyword.intern(schemaNamespace, "methods")
    val reasons: Keyword = Keyword.intern(schemaNamespace, "reasons")
    val algorithm: Keyword = Keyword.intern(schemaNamespace, "algorithm")
    val raw: Keyword = Keyword.intern(schemaNamespace, "raw")

    object MessageSpec {
        val kind: Keyword = Keyword.intern(schemaNamespace, "kind")
        val body: Keyword = Keyword.intern(schemaNamespace, "body")
        val sourcePath: Keyword = Keyword.intern(schemaNamespace, "source-path")
        val fileName: Keyword = Keyword.intern(schemaNamespace, "file-name")
        val mimeType: Keyword = Keyword.intern(schemaNamespace, "mime-type")
        val sizeBytes: Keyword = Keyword.intern(schemaNamespace, "size-bytes")
        val duration: Keyword = Keyword.intern(schemaNamespace, "duration")
        val height: Keyword = Keyword.intern(schemaNamespace, "height")
        val width: Keyword = Keyword.intern(schemaNamespace, "width")
        val format: Keyword = Keyword.intern(schemaNamespace, "format")
        val formattedBody: Keyword = Keyword.intern(schemaNamespace, "formatted-body")
        val replyTo: Keyword = Keyword.intern(schemaNamespace, "reply-to")
    }

    object Event {
        val type: Keyword = Keyword.intern(schemaNamespace, "type")
        val roomId: Keyword = Keyword.intern(schemaNamespace, "room-id")
        val eventId: Keyword = Keyword.intern(schemaNamespace, "event-id")
        val sender: Keyword = Keyword.intern(schemaNamespace, "sender")
        val senderDisplayName: Keyword = BridgeSchema.senderDisplayName
        val body: Keyword = Keyword.intern(schemaNamespace, "body")
        val key: Keyword = Keyword.intern(schemaNamespace, "key")
        val relatesTo: Keyword = Keyword.intern(schemaNamespace, "relates-to")
        val raw: Keyword = Keyword.intern(schemaNamespace, "raw")
    }

    object Room {
        val roomId: Keyword = BridgeSchema.roomId
        val membership: Keyword = BridgeSchema.membership
        val roomName: Keyword = BridgeSchema.roomName
        val isDirect: Keyword = BridgeSchema.isDirect
        val raw: Keyword = BridgeSchema.raw
    }

    object Profile {
        val displayName: Keyword = BridgeSchema.displayName
        val avatarUrl: Keyword = BridgeSchema.avatarUrl
        val timeZone: Keyword = BridgeSchema.timeZone
        val raw: Keyword = BridgeSchema.raw
    }

    object ServerData {
        val versions: Keyword = BridgeSchema.versions
        val mediaConfig: Keyword = BridgeSchema.mediaConfig
        val capabilities: Keyword = BridgeSchema.capabilities
        val auth: Keyword = BridgeSchema.auth
        val raw: Keyword = BridgeSchema.raw
    }

    object TypingEventContent {
        val users: Keyword = BridgeSchema.users
        val raw: Keyword = BridgeSchema.raw
    }

    object Relation {
        val type: Keyword = Keyword.intern(schemaNamespace, "relation-type")
        val eventId: Keyword = Keyword.intern(schemaNamespace, "relation-event-id")
        val key: Keyword = BridgeSchema.key
        val replyToEventId: Keyword = Keyword.intern(schemaNamespace, "reply-to-event-id")
        val isFallingBack: Keyword = Keyword.intern(schemaNamespace, "is-falling-back")
    }

    object StateEvent {
        val type: Keyword = BridgeSchema.Event.type
        val roomId: Keyword = BridgeSchema.roomId
        val eventId: Keyword = BridgeSchema.eventId
        val sender: Keyword = BridgeSchema.Event.sender
        val stateKey: Keyword = BridgeSchema.stateKey
        val content: Keyword = BridgeSchema.content
        val raw: Keyword = BridgeSchema.raw
    }

    object RoomOutboxMessage {
        val roomId: Keyword = BridgeSchema.roomId
        val transactionId: Keyword = BridgeSchema.transactionId
        val eventId: Keyword = BridgeSchema.eventId
        val content: Keyword = BridgeSchema.content
        val createdAt: Keyword = BridgeSchema.createdAt
        val sentAt: Keyword = BridgeSchema.sentAt
        val sendError: Keyword = BridgeSchema.sendError
        val raw: Keyword = BridgeSchema.raw
    }

    object RoomUser {
        val roomId: Keyword = BridgeSchema.roomId
        val userId: Keyword = BridgeSchema.userId
        val name: Keyword = BridgeSchema.name
        val raw: Keyword = BridgeSchema.raw
    }

    object RoomUserReceipt {
        val receiptType: Keyword = BridgeSchema.receiptType
        val eventId: Keyword = BridgeSchema.eventId
        val raw: Keyword = BridgeSchema.raw
    }

    object RoomUserReceipts {
        val roomId: Keyword = BridgeSchema.roomId
        val userId: Keyword = BridgeSchema.userId
        val receipts: Keyword = BridgeSchema.receipts
        val raw: Keyword = BridgeSchema.raw
    }

    object PowerLevel {
        val kind: Keyword = BridgeSchema.MessageSpec.kind
        val level: Keyword = BridgeSchema.level
        val raw: Keyword = BridgeSchema.raw
    }

    object UserPresence {
        val presence: Keyword = BridgeSchema.presence
        val lastUpdate: Keyword = BridgeSchema.lastUpdate
        val lastActive: Keyword = BridgeSchema.lastActive
        val currentlyActive: Keyword = BridgeSchema.currentlyActive
        val statusMessage: Keyword = BridgeSchema.statusMessage
        val raw: Keyword = BridgeSchema.raw
    }

    object Notification {
        val id: Keyword = BridgeSchema.id
        val sortKey: Keyword = BridgeSchema.sortKey
        val actions: Keyword = BridgeSchema.actions
        val dismissed: Keyword = BridgeSchema.dismissed
        val kind: Keyword = BridgeSchema.notificationKind
        val timelineEvent: Keyword = Keyword.intern(schemaNamespace, "timeline-event")
        val stateEvent: Keyword = Keyword.intern(schemaNamespace, "state-event")
        val raw: Keyword = BridgeSchema.raw
    }

    object NotificationUpdate {
        val id: Keyword = BridgeSchema.id
        val sortKey: Keyword = BridgeSchema.sortKey
        val actions: Keyword = BridgeSchema.actions
        val kind: Keyword = BridgeSchema.notificationUpdateKind
        val content: Keyword = BridgeSchema.content
        val raw: Keyword = BridgeSchema.raw
    }

    object ActiveVerification {
        val theirUserId: Keyword = BridgeSchema.theirUserId
        val theirDeviceId: Keyword = BridgeSchema.theirDeviceId
        val requestEventId: Keyword = BridgeSchema.requestEventId
        val roomId: Keyword = BridgeSchema.roomId
        val transactionId: Keyword = BridgeSchema.transactionId
        val timestamp: Keyword = BridgeSchema.timestamp
        val verificationState: Keyword = BridgeSchema.verificationState
        val raw: Keyword = BridgeSchema.raw
    }

    object VerificationState {
        val kind: Keyword = BridgeSchema.MessageSpec.kind
        val raw: Keyword = BridgeSchema.raw
    }

    object SelfVerificationMethods {
        val kind: Keyword = BridgeSchema.MessageSpec.kind
        val methods: Keyword = BridgeSchema.methods
        val reasons: Keyword = BridgeSchema.reasons
        val raw: Keyword = BridgeSchema.raw
    }

    object TrustLevel {
        val kind: Keyword = BridgeSchema.MessageSpec.kind
        val verified: Keyword = BridgeSchema.verified
        val reason: Keyword = BridgeSchema.reason
        val raw: Keyword = BridgeSchema.raw
    }

    object BackupVersion {
        val version: Keyword = Keyword.intern(schemaNamespace, "version")
        val algorithm: Keyword = BridgeSchema.algorithm
        val auth: Keyword = BridgeSchema.auth
        val raw: Keyword = BridgeSchema.raw
    }

    object OpenClientRequest {
        val homeserverUrl: Keyword = BridgeSchema.homeserverUrl
        val userId: Keyword = BridgeSchema.userId
        val password: Keyword = BridgeSchema.password
        val deviceName: Keyword = BridgeSchema.deviceName
        val deviceId: Keyword = BridgeSchema.deviceId
        val databasePath: Keyword = BridgeSchema.databasePath
        val mediaPath: Keyword = BridgeSchema.mediaPath
    }

    object StartSyncRequest {
        val client: Keyword = BridgeSchema.client
    }

    object AwaitRunningRequest {
        val client: Keyword = BridgeSchema.client
        val timeout: Keyword = BridgeSchema.timeout
    }

    object StopSyncRequest {
        val client: Keyword = BridgeSchema.client
    }

    object CloseClientRequest {
        val client: Keyword = BridgeSchema.client
    }

    object CurrentUserIdRequest {
        val client: Keyword = BridgeSchema.client
    }

    object SyncStateRequest {
        val client: Keyword = BridgeSchema.client
    }

    object CreateRoomRequest {
        val client: Keyword = BridgeSchema.client
        val roomName: Keyword = BridgeSchema.roomName
        val topic: Keyword = BridgeSchema.topic
        val invite: Keyword = BridgeSchema.invite
        val preset: Keyword = BridgeSchema.preset
        val isDirect: Keyword = BridgeSchema.isDirect
        val visibility: Keyword = BridgeSchema.visibility
    }

    object InviteUserRequest {
        val client: Keyword = BridgeSchema.client
        val roomId: Keyword = BridgeSchema.roomId
        val userId: Keyword = BridgeSchema.userId
        val timeout: Keyword = BridgeSchema.timeout
    }

    object SendMessageRequest {
        val client: Keyword = BridgeSchema.client
        val roomId: Keyword = BridgeSchema.roomId
        val message: Keyword = BridgeSchema.message
        val timeout: Keyword = BridgeSchema.timeout
    }

    object JoinRoomRequest {
        val client: Keyword = BridgeSchema.client
        val roomId: Keyword = BridgeSchema.roomId
        val timeout: Keyword = BridgeSchema.timeout
    }

    object SendReactionRequest {
        val client: Keyword = BridgeSchema.client
        val roomId: Keyword = BridgeSchema.roomId
        val eventId: Keyword = BridgeSchema.eventId
        val key: Keyword = BridgeSchema.key
        val timeout: Keyword = BridgeSchema.timeout
    }

}
