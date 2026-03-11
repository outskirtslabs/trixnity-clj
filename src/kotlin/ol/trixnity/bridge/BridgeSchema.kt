package ol.trixnity.bridge

import clojure.lang.Keyword

internal typealias KeywordMap = Map<Keyword, *>

internal object BridgeSchema {
    private const val schemaNamespace = "ol.trixnity.schemas"

    val homeserverUrl: Keyword = Keyword.intern(schemaNamespace, "homeserver-url")
    val username: Keyword = Keyword.intern(schemaNamespace, "username")
    val password: Keyword = Keyword.intern(schemaNamespace, "password")
    val databasePath: Keyword = Keyword.intern(schemaNamespace, "database-path")
    val mediaPath: Keyword = Keyword.intern(schemaNamespace, "media-path")
    val client: Keyword = Keyword.intern(schemaNamespace, "client")
    val roomName: Keyword = Keyword.intern(schemaNamespace, "room-name")
    val roomId: Keyword = Keyword.intern(schemaNamespace, "room-id")
    val userId: Keyword = Keyword.intern(schemaNamespace, "user-id")
    val eventId: Keyword = Keyword.intern(schemaNamespace, "event-id")
    val key: Keyword = Keyword.intern(schemaNamespace, "key")
    val message: Keyword = Keyword.intern(schemaNamespace, "message")
    val onEvent: Keyword = Keyword.intern(schemaNamespace, "on-event")
    val timeout: Keyword = Keyword.intern(schemaNamespace, "timeout")
    val decryptionTimeout: Keyword = Keyword.intern(schemaNamespace, "decryption-timeout")

    object MessageSpec {
        val kind: Keyword = Keyword.intern(schemaNamespace, "kind")
        val body: Keyword = Keyword.intern(schemaNamespace, "body")
        val format: Keyword = Keyword.intern(schemaNamespace, "format")
        val formattedBody: Keyword = Keyword.intern(schemaNamespace, "formatted-body")
        val replyTo: Keyword = Keyword.intern(schemaNamespace, "reply-to")
    }

    object Event {
        val type: Keyword = Keyword.intern(schemaNamespace, "type")
        val roomId: Keyword = Keyword.intern(schemaNamespace, "room-id")
        val eventId: Keyword = Keyword.intern(schemaNamespace, "event-id")
        val sender: Keyword = Keyword.intern(schemaNamespace, "sender")
        val body: Keyword = Keyword.intern(schemaNamespace, "body")
        val key: Keyword = Keyword.intern(schemaNamespace, "key")
        val relatesTo: Keyword = Keyword.intern(schemaNamespace, "relates-to")
        val raw: Keyword = Keyword.intern(schemaNamespace, "raw")
    }

    object Relation {
        val type: Keyword = Keyword.intern(schemaNamespace, "relation-type")
        val eventId: Keyword = Keyword.intern(schemaNamespace, "relation-event-id")
        val key: Keyword = BridgeSchema.key
        val replyToEventId: Keyword = Keyword.intern(schemaNamespace, "reply-to-event-id")
        val isFallingBack: Keyword = Keyword.intern(schemaNamespace, "is-falling-back")
    }

    object OpenClientRequest {
        val homeserverUrl: Keyword = BridgeSchema.homeserverUrl
        val username: Keyword = BridgeSchema.username
        val password: Keyword = BridgeSchema.password
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

    object SendReactionRequest {
        val client: Keyword = BridgeSchema.client
        val roomId: Keyword = BridgeSchema.roomId
        val eventId: Keyword = BridgeSchema.eventId
        val key: Keyword = BridgeSchema.key
        val timeout: Keyword = BridgeSchema.timeout
    }

    object SubscribeTimelineRequest {
        val client: Keyword = BridgeSchema.client
        val onEvent: Keyword = BridgeSchema.onEvent
        val decryptionTimeout: Keyword = BridgeSchema.decryptionTimeout
    }
}
