package ol.trixnity.bridge

import de.connect2x.trixnity.client.CryptoDriverModule
import de.connect2x.trixnity.client.MatrixClient
import de.connect2x.trixnity.client.create
import de.connect2x.trixnity.client.cryptodriver.vodozemac.vodozemac
import de.connect2x.trixnity.clientserverapi.client.MatrixClientAuthProviderData
import de.connect2x.trixnity.clientserverapi.client.SyncState
import de.connect2x.trixnity.clientserverapi.client.classicLoginWithPassword
import de.connect2x.trixnity.clientserverapi.model.authentication.IdentifierType
import io.ktor.http.URLBuilder
import io.ktor.http.takeFrom
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.first

object ClientBridge {
    private fun isFreshStoreWithoutAuth(error: IllegalArgumentException): Boolean =
        error.message?.contains("authProviderData must not be null when repositories are empty") == true

    private suspend fun createClient(request: KeywordMap): MatrixClient {
        val homeserverUrl = requireKeywordString(request, BridgeSchema.OpenClientRequest.homeserverUrl)
        val username = requireKeywordString(request, BridgeSchema.OpenClientRequest.username)
        val password = requireKeywordString(request, BridgeSchema.OpenClientRequest.password)
        val databasePath = requireKeywordString(request, BridgeSchema.OpenClientRequest.databasePath)
        val mediaPath = requireKeywordString(request, BridgeSchema.OpenClientRequest.mediaPath)
        val repositoriesModule = createRepositoriesModule(databasePath)
        val mediaStoreModule = createMediaStoreModule(mediaPath)

        val client = try {
            MatrixClient.create(
                repositoriesModule = repositoriesModule,
                mediaStoreModule = mediaStoreModule,
                cryptoDriverModule = CryptoDriverModule.vodozemac(),
            ).getOrThrow()
        } catch (error: IllegalArgumentException) {
            if (!isFreshStoreWithoutAuth(error)) throw error
            MatrixClient.create(
                repositoriesModule = repositoriesModule,
                mediaStoreModule = mediaStoreModule,
                cryptoDriverModule = CryptoDriverModule.vodozemac(),
                authProviderData = MatrixClientAuthProviderData.classicLoginWithPassword(
                    baseUrl = URLBuilder().takeFrom(homeserverUrl).build(),
                    identifier = IdentifierType.User(username),
                    password = password,
                    refreshToken = true,
                ).getOrThrow(),
            ).getOrThrow()
        }

        return BridgeAsync.registerClient(client)
    }

    @JvmStatic
    fun openClient(request: KeywordMap) =
        BridgeAsync.submitFuture(BridgeAsync.closeScope()) {
            createClient(request)
        }

    @JvmStatic
    fun startSync(request: KeywordMap) =
        BridgeAsync.submitFuture(
            scope = BridgeAsync.clientScope(
                requireKeywordClient(request, BridgeSchema.StartSyncRequest.client),
            ),
        ) {
            val client = requireKeywordClient(request, BridgeSchema.StartSyncRequest.client)
            client.startSync()
            null
        }

    @JvmStatic
    fun awaitRunning(request: KeywordMap) =
        BridgeAsync.submitFuture(
            scope = BridgeAsync.clientScope(
                requireKeywordClient(request, BridgeSchema.AwaitRunningRequest.client),
            ),
            timeout = optionalKeywordDuration(request, BridgeSchema.AwaitRunningRequest.timeout),
        ) {
            val client = requireKeywordClient(request, BridgeSchema.AwaitRunningRequest.client)
            client.syncState.first { it == SyncState.RUNNING }
            null
        }

    @JvmStatic
    fun stopSync(request: KeywordMap) =
        BridgeAsync.submitFuture(
            scope = BridgeAsync.clientScope(
                requireKeywordClient(request, BridgeSchema.StopSyncRequest.client),
            ),
        ) {
            val client = requireKeywordClient(request, BridgeSchema.StopSyncRequest.client)
            client.stopSync()
            null
        }

    @JvmStatic
    fun closeClient(request: KeywordMap) =
        BridgeAsync.submitFuture(
            scope = BridgeAsync.closeScope(),
        ) {
            val client = requireKeywordClient(request, BridgeSchema.CloseClientRequest.client)
            BridgeAsync.removeClientScope(client)?.coroutineContext?.get(kotlinx.coroutines.Job)?.cancelAndJoin()
            client.closeSuspending()
            null
        }

    @JvmStatic
    fun currentUserId(request: KeywordMap): String {
        val client = requireKeywordClient(request, BridgeSchema.CurrentUserIdRequest.client)
        return client.userId.full
    }

    @JvmStatic
    fun syncState(request: KeywordMap): String {
        val client = requireKeywordClient(request, BridgeSchema.SyncStateRequest.client)
        return client.syncState.value.name
    }
}
