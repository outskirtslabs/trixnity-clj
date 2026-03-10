package ol.trixnity.bridge

import clojure.lang.Keyword

internal typealias KeywordMap = Map<Keyword, *>

internal object BridgeSchema {
    private const val namespace = "ol.trixnity.schemas"

    val database: Keyword = Keyword.intern(namespace, "database")
    val mediaPath: Keyword = Keyword.intern(namespace, "media-path")
    val client: Keyword = Keyword.intern(namespace, "client")
    val roomName: Keyword = Keyword.intern(namespace, "room-name")
    val roomId: Keyword = Keyword.intern(namespace, "room-id")
    val userId: Keyword = Keyword.intern(namespace, "user-id")
    val eventId: Keyword = Keyword.intern(namespace, "event-id")
    val body: Keyword = Keyword.intern(namespace, "body")
    val key: Keyword = Keyword.intern(namespace, "key")
    val onEvent: Keyword = Keyword.intern(namespace, "on-event")
    val timelinePump: Keyword = Keyword.intern(namespace, "timeline-pump")

    object LoginRequest {
        val homeserverUrl: Keyword = Keyword.intern(namespace, "homeserver-url")
        val username: Keyword = Keyword.intern(namespace, "username")
        val password: Keyword = Keyword.intern(namespace, "password")
        val database: Keyword = BridgeSchema.database
        val mediaPath: Keyword = BridgeSchema.mediaPath
    }

    object FromStoreRequest {
        val database: Keyword = BridgeSchema.database
        val mediaPath: Keyword = BridgeSchema.mediaPath
    }

    object StartSyncRequest {
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
    }

    object SendTextReplyRequest {
        val client: Keyword = BridgeSchema.client
        val roomId: Keyword = BridgeSchema.roomId
        val eventId: Keyword = BridgeSchema.eventId
        val body: Keyword = BridgeSchema.body
    }

    object SendReactionRequest {
        val client: Keyword = BridgeSchema.client
        val roomId: Keyword = BridgeSchema.roomId
        val eventId: Keyword = BridgeSchema.eventId
        val key: Keyword = BridgeSchema.key
    }

    object StartTimelinePumpRequest {
        val client: Keyword = BridgeSchema.client
        val onEvent: Keyword = BridgeSchema.onEvent
    }

    object StopTimelinePumpRequest {
        val client: Keyword = BridgeSchema.client
        val timelinePump: Keyword = BridgeSchema.timelinePump
    }
}
