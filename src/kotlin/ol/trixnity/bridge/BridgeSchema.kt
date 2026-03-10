package ol.trixnity.bridge

import clojure.lang.Keyword

internal typealias KeywordMap = Map<Keyword, *>

internal object BridgeSchema {
    private const val namespace = "ol.trixnity.schemas"

    val storePath: Keyword = Keyword.intern(namespace, "store-path")
    val mediaPath: Keyword = Keyword.intern(namespace, "media-path")
    val client: Keyword = Keyword.intern(namespace, "client")

    object LoginRequest {
        val homeserverUrl: Keyword = Keyword.intern(namespace, "homeserver-url")
        val username: Keyword = Keyword.intern(namespace, "username")
        val password: Keyword = Keyword.intern(namespace, "password")
        val storePath: Keyword = BridgeSchema.storePath
        val mediaPath: Keyword = BridgeSchema.mediaPath
    }

    object FromStoreRequest {
        val storePath: Keyword = BridgeSchema.storePath
        val mediaPath: Keyword = BridgeSchema.mediaPath
    }

    object StartSyncRequest {
        val client: Keyword = BridgeSchema.client
    }
}
