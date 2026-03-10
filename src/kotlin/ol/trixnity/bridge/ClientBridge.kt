package ol.trixnity.bridge

import io.ktor.http.URLBuilder
import io.ktor.http.takeFrom
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import net.folivo.trixnity.client.MatrixClient
import net.folivo.trixnity.client.fromStore
import net.folivo.trixnity.client.loginWithPassword
import net.folivo.trixnity.clientserverapi.client.SyncState
import net.folivo.trixnity.clientserverapi.model.authentication.IdentifierType

object ClientBridge {
    @JvmStatic
    fun loginBlocking(request: KeywordMap): Any = runBlocking {
        val homeserverUrl = requireKeywordString(request, BridgeSchema.LoginRequest.homeserverUrl)
        val username = requireKeywordString(request, BridgeSchema.LoginRequest.username)
        val password = requireKeywordString(request, BridgeSchema.LoginRequest.password)
        val database = requireKeywordDatabase(request, BridgeSchema.LoginRequest.database)
        val mediaPath = requireKeywordString(request, BridgeSchema.LoginRequest.mediaPath)
        val repositoriesModule = createRepositoriesModule(database)
        val mediaStoreModule = createMediaStoreModule(mediaPath)
        MatrixClient.loginWithPassword(
            baseUrl = URLBuilder().takeFrom(homeserverUrl).build(),
            identifier = IdentifierType.User(username),
            password = password,
            repositoriesModule = repositoriesModule,
            mediaStoreModule = mediaStoreModule,
        ).getOrThrow()
    }

    @JvmStatic
    fun fromStoreBlocking(request: KeywordMap): Any? = runBlocking {
        val database = requireKeywordDatabase(request, BridgeSchema.FromStoreRequest.database)
        val mediaPath = requireKeywordString(request, BridgeSchema.FromStoreRequest.mediaPath)
        val repositoriesModule = createRepositoriesModule(database)
        val mediaStoreModule = createMediaStoreModule(mediaPath)
        MatrixClient.fromStore(
            repositoriesModule = repositoriesModule,
            mediaStoreModule = mediaStoreModule,
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
