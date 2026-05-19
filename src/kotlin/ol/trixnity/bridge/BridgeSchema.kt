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
    val roomType: Keyword = Keyword.intern(schemaNamespace, "room-type")
    val key: Keyword = Keyword.intern(schemaNamespace, "key")
    val wait: Keyword = Keyword.intern(schemaNamespace, "wait")
    val force: Keyword = Keyword.intern(schemaNamespace, "force")
    val keepInCache: Keyword = Keyword.intern(schemaNamespace, "keep-in-cache")
    val limit: Keyword = Keyword.intern(schemaNamespace, "limit")
    val deviceName: Keyword = Keyword.intern(schemaNamespace, "device-name")
    val displayName: Keyword = Keyword.intern(schemaNamespace, "display-name")
    val senderDisplayName: Keyword = Keyword.intern(schemaNamespace, "sender-display-name")
    val avatarUrl: Keyword = Keyword.intern(schemaNamespace, "avatar-url")
    val cacheUri: Keyword = Keyword.intern(schemaNamespace, "cache-uri")
    val mxcUri: Keyword = Keyword.intern(schemaNamespace, "mxc-uri")
    val url: Keyword = Keyword.intern(schemaNamespace, "url")
    val timeZone: Keyword = Keyword.intern(schemaNamespace, "time-zone")
    val versions: Keyword = Keyword.intern(schemaNamespace, "versions")
    val mediaConfig: Keyword = Keyword.intern(schemaNamespace, "media-config")
    val capabilities: Keyword = Keyword.intern(schemaNamespace, "capabilities")
    val auth: Keyword = Keyword.intern(schemaNamespace, "auth")
    val content: Keyword = Keyword.intern(schemaNamespace, "content")
    val message: Keyword = Keyword.intern(schemaNamespace, "message")
    val timeout: Keyword = Keyword.intern(schemaNamespace, "timeout")
    val type: Keyword = Keyword.intern(schemaNamespace, "type")
    val msgtype: Keyword = Keyword.intern(schemaNamespace, "msgtype")
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
    val transferred: Keyword = Keyword.intern(schemaNamespace, "transferred")
    val total: Keyword = Keyword.intern(schemaNamespace, "total")
    val mediaUploadProgress: Keyword = Keyword.intern(schemaNamespace, "media-upload-progress")
    val receipts: Keyword = Keyword.intern(schemaNamespace, "receipts")
    val receiptType: Keyword = Keyword.intern(schemaNamespace, "receipt-type")
    val name: Keyword = Keyword.intern(schemaNamespace, "name")
    val presence: Keyword = Keyword.intern(schemaNamespace, "presence")
    val lastUpdate: Keyword = Keyword.intern(schemaNamespace, "last-update")
    val lastActive: Keyword = Keyword.intern(schemaNamespace, "last-active")
    val currentlyActive: Keyword = Keyword.intern(schemaNamespace, "currently-active")
    val statusMessage: Keyword = Keyword.intern(schemaNamespace, "status-message")
    val level: Keyword = Keyword.intern(schemaNamespace, "level")
    val banLevel: Keyword = Keyword.intern(schemaNamespace, "ban-level")
    val eventLevels: Keyword = Keyword.intern(schemaNamespace, "event-levels")
    val eventsDefaultLevel: Keyword = Keyword.intern(schemaNamespace, "events-default-level")
    val inviteLevel: Keyword = Keyword.intern(schemaNamespace, "invite-level")
    val kickLevel: Keyword = Keyword.intern(schemaNamespace, "kick-level")
    val redactLevel: Keyword = Keyword.intern(schemaNamespace, "redact-level")
    val stateDefaultLevel: Keyword = Keyword.intern(schemaNamespace, "state-default-level")
    val userLevels: Keyword = Keyword.intern(schemaNamespace, "user-levels")
    val usersDefaultLevel: Keyword = Keyword.intern(schemaNamespace, "users-default-level")
    val notificationLevels: Keyword = Keyword.intern(schemaNamespace, "notification-levels")
    val externalUrl: Keyword = Keyword.intern(schemaNamespace, "external-url")
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
    val verificationId: Keyword = Keyword.intern(schemaNamespace, "verification-id")
    val verificationKind: Keyword = Keyword.intern(schemaNamespace, "verification-kind")
    val verificationDirection: Keyword = Keyword.intern(schemaNamespace, "verification-direction")
    val verificationMethod: Keyword = Keyword.intern(schemaNamespace, "verification-method")
    val sasState: Keyword = Keyword.intern(schemaNamespace, "sas-state")
    val sasDecimal: Keyword = Keyword.intern(schemaNamespace, "sas-decimal")
    val sasEmojis: Keyword = Keyword.intern(schemaNamespace, "sas-emojis")
    val emoji: Keyword = Keyword.intern(schemaNamespace, "emoji")
    val description: Keyword = Keyword.intern(schemaNamespace, "description")
    val index: Keyword = Keyword.intern(schemaNamespace, "index")
    val isOurOwn: Keyword = Keyword.intern(schemaNamespace, "is-our-own")
    val cancelCode: Keyword = Keyword.intern(schemaNamespace, "cancel-code")
    val senderUserId: Keyword = Keyword.intern(schemaNamespace, "sender-user-id")
    val senderDeviceId: Keyword = Keyword.intern(schemaNamespace, "sender-device-id")
    val methods: Keyword = Keyword.intern(schemaNamespace, "methods")
    val reasons: Keyword = Keyword.intern(schemaNamespace, "reasons")
    val algorithm: Keyword = Keyword.intern(schemaNamespace, "algorithm")
    val jwk: Keyword = Keyword.intern(schemaNamespace, "jwk")
    val jwkKey: Keyword = Keyword.intern(schemaNamespace, "jwk-key")
    val keyType: Keyword = Keyword.intern(schemaNamespace, "key-type")
    val keyOperations: Keyword = Keyword.intern(schemaNamespace, "key-operations")
    val extractable: Keyword = Keyword.intern(schemaNamespace, "extractable")
    val initializationVector: Keyword = Keyword.intern(schemaNamespace, "initialization-vector")
    val hashes: Keyword = Keyword.intern(schemaNamespace, "hashes")
    val version: Keyword = Keyword.intern(schemaNamespace, "version")
    val encryptedFile: Keyword = Keyword.intern(schemaNamespace, "encrypted-file")
    val thumbnailUrl: Keyword = Keyword.intern(schemaNamespace, "thumbnail-url")
    val thumbnailEncryptedFile: Keyword = Keyword.intern(schemaNamespace, "thumbnail-encrypted-file")
    val inputStream: Keyword = Keyword.intern(schemaNamespace, "input-stream")
    val path: Keyword = Keyword.intern(schemaNamespace, "path")
    val method: Keyword = Keyword.intern(schemaNamespace, "method")
    val animated: Keyword = Keyword.intern(schemaNamespace, "animated")
    val raw: Keyword = Keyword.intern(schemaNamespace, "raw")
    val powerLevels: Keyword = Keyword.intern(schemaNamespace, "power-levels")
    val via: Keyword = Keyword.intern(schemaNamespace, "via")
    val order: Keyword = Keyword.intern(schemaNamespace, "order")
    val suggested: Keyword = Keyword.intern(schemaNamespace, "suggested")
    val canonical: Keyword = Keyword.intern(schemaNamespace, "canonical")
    val from: Keyword = Keyword.intern(schemaNamespace, "from")
    val maxDepth: Keyword = Keyword.intern(schemaNamespace, "max-depth")
    val suggestedOnly: Keyword = Keyword.intern(schemaNamespace, "suggested-only")
    val nextBatch: Keyword = Keyword.intern(schemaNamespace, "next-batch")
    val rooms: Keyword = Keyword.intern(schemaNamespace, "rooms")
    val allowedRoomIds: Keyword = Keyword.intern(schemaNamespace, "allowed-room-ids")
    val canonicalAlias: Keyword = Keyword.intern(schemaNamespace, "canonical-alias")
    val childrenState: Keyword = Keyword.intern(schemaNamespace, "children-state")
    val encryption: Keyword = Keyword.intern(schemaNamespace, "encryption")
    val guestCanJoin: Keyword = Keyword.intern(schemaNamespace, "guest-can-join")
    val joinRule: Keyword = Keyword.intern(schemaNamespace, "join-rule")
    val joinedMembersCount: Keyword = Keyword.intern(schemaNamespace, "joined-members-count")
    val roomVersion: Keyword = Keyword.intern(schemaNamespace, "room-version")
    val worldReadable: Keyword = Keyword.intern(schemaNamespace, "world-readable")

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
        val type: Keyword = BridgeSchema.type
        val roomId: Keyword = Keyword.intern(schemaNamespace, "room-id")
        val eventId: Keyword = Keyword.intern(schemaNamespace, "event-id")
        val sender: Keyword = Keyword.intern(schemaNamespace, "sender")
        val senderDisplayName: Keyword = BridgeSchema.senderDisplayName
        val body: Keyword = Keyword.intern(schemaNamespace, "body")
        val msgtype: Keyword = BridgeSchema.msgtype
        val url: Keyword = BridgeSchema.url
        val encryptedFile: Keyword = BridgeSchema.encryptedFile
        val fileName: Keyword = MessageSpec.fileName
        val mimeType: Keyword = MessageSpec.mimeType
        val sizeBytes: Keyword = MessageSpec.sizeBytes
        val duration: Keyword = MessageSpec.duration
        val height: Keyword = MessageSpec.height
        val width: Keyword = MessageSpec.width
        val thumbnailUrl: Keyword = BridgeSchema.thumbnailUrl
        val thumbnailEncryptedFile: Keyword = BridgeSchema.thumbnailEncryptedFile
        val key: Keyword = Keyword.intern(schemaNamespace, "key")
        val relatesTo: Keyword = Keyword.intern(schemaNamespace, "relates-to")
        val raw: Keyword = Keyword.intern(schemaNamespace, "raw")
    }

    object Room {
        val roomId: Keyword = BridgeSchema.roomId
        val membership: Keyword = BridgeSchema.membership
        val roomName: Keyword = BridgeSchema.roomName
        val roomType: Keyword = BridgeSchema.roomType
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
        val mediaUploadProgress: Keyword = BridgeSchema.mediaUploadProgress
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

    object PowerLevelsContent {
        val banLevel: Keyword = BridgeSchema.banLevel
        val eventLevels: Keyword = BridgeSchema.eventLevels
        val eventsDefaultLevel: Keyword = BridgeSchema.eventsDefaultLevel
        val inviteLevel: Keyword = BridgeSchema.inviteLevel
        val kickLevel: Keyword = BridgeSchema.kickLevel
        val redactLevel: Keyword = BridgeSchema.redactLevel
        val stateDefaultLevel: Keyword = BridgeSchema.stateDefaultLevel
        val userLevels: Keyword = BridgeSchema.userLevels
        val usersDefaultLevel: Keyword = BridgeSchema.usersDefaultLevel
        val notificationLevels: Keyword = BridgeSchema.notificationLevels
        val externalUrl: Keyword = BridgeSchema.externalUrl
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
        val verificationId: Keyword = BridgeSchema.verificationId
        val verificationKind: Keyword = BridgeSchema.verificationKind
        val verificationDirection: Keyword = BridgeSchema.verificationDirection
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
        val verificationDirection: Keyword = BridgeSchema.verificationDirection
        val verificationMethod: Keyword = BridgeSchema.verificationMethod
        val methods: Keyword = BridgeSchema.methods
        val senderUserId: Keyword = BridgeSchema.senderUserId
        val senderDeviceId: Keyword = BridgeSchema.senderDeviceId
        val isOurOwn: Keyword = BridgeSchema.isOurOwn
        val cancelCode: Keyword = BridgeSchema.cancelCode
        val reason: Keyword = BridgeSchema.reason
        val sasState: Keyword = BridgeSchema.sasState
        val raw: Keyword = BridgeSchema.raw
    }

    object SasVerificationState {
        val kind: Keyword = BridgeSchema.MessageSpec.kind
        val sasDecimal: Keyword = BridgeSchema.sasDecimal
        val sasEmojis: Keyword = BridgeSchema.sasEmojis
        val raw: Keyword = BridgeSchema.raw
    }

    object SasEmoji {
        val index: Keyword = BridgeSchema.index
        val emoji: Keyword = BridgeSchema.emoji
        val description: Keyword = BridgeSchema.description
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
        val powerLevels: Keyword = BridgeSchema.powerLevels
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

    object SendStateEventRequest {
        val client: Keyword = BridgeSchema.client
        val roomId: Keyword = BridgeSchema.roomId
        val stateEvent: Keyword = Keyword.intern(schemaNamespace, "state-event")
        val timeout: Keyword = BridgeSchema.timeout
    }

}
