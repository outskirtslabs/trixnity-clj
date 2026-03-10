package ol.trixnity.bridge

import io.ktor.http.URLBuilder
import io.ktor.http.takeFrom
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import net.folivo.trixnity.client.MatrixClient
import net.folivo.trixnity.client.fromStore
import net.folivo.trixnity.client.login
import net.folivo.trixnity.clientserverapi.client.SyncState
import net.folivo.trixnity.clientserverapi.model.authentication.IdentifierType

object ClientBridge {
    @JvmStatic
    fun loginBlocking(request: KeywordMap): Any = runBlocking {
        val homeserverUrl = requireKeywordString(request, BridgeSchema.LoginRequest.homeserverUrl)
        val username = requireKeywordString(request, BridgeSchema.LoginRequest.username)
        val password = requireKeywordString(request, BridgeSchema.LoginRequest.password)
        val storePath = requireKeywordString(request, BridgeSchema.LoginRequest.storePath)
        val mediaPath = requireKeywordString(request, BridgeSchema.LoginRequest.mediaPath)
        val repositoriesModule = createRepositoriesModule(storePath)
        val mediaStore = createMediaStore(mediaPath)
        MatrixClient.login(
            baseUrl = URLBuilder().takeFrom(homeserverUrl).build(),
            identifier = IdentifierType.User(username),
            password = password,
            repositoriesModule = repositoriesModule,
            mediaStore = mediaStore,
        ).getOrThrow()
    }

    @JvmStatic
    fun fromStoreBlocking(request: KeywordMap): Any? = runBlocking {
        val storePath = requireKeywordString(request, BridgeSchema.FromStoreRequest.storePath)
        val mediaPath = requireKeywordString(request, BridgeSchema.FromStoreRequest.mediaPath)
        val repositoriesModule = createRepositoriesModule(storePath)
        val mediaStore = createMediaStore(mediaPath)
        MatrixClient.fromStore(
            repositoriesModule = repositoriesModule,
            mediaStore = mediaStore,
        ).getOrThrow()
    }

    @JvmStatic
    fun startSyncBlocking(request: KeywordMap) {
        val client = request[BridgeSchema.StartSyncRequest.client] as? MatrixClient
            ?: throw IllegalArgumentException(
                "request payload is missing MatrixClient under ${BridgeSchema.StartSyncRequest.client}",
            )
        runBlocking {
            client.startSync()
            client.syncState.first { it == SyncState.RUNNING }
        }
    }
}
