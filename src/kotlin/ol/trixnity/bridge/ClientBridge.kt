package ol.trixnity.bridge

import de.connect2x.trixnity.client.CryptoDriverModule
import io.ktor.http.URLBuilder
import io.ktor.http.takeFrom
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import de.connect2x.trixnity.client.MatrixClient
import de.connect2x.trixnity.client.create
import de.connect2x.trixnity.client.cryptodriver.vodozemac.vodozemac
import de.connect2x.trixnity.clientserverapi.client.MatrixClientAuthProviderData
import de.connect2x.trixnity.clientserverapi.client.SyncState
import de.connect2x.trixnity.clientserverapi.client.classicLoginWithPassword
import de.connect2x.trixnity.clientserverapi.model.authentication.IdentifierType

object ClientBridge {
    private fun isFreshStoreWithoutAuth(error: IllegalArgumentException): Boolean =
        error.message?.contains("authProviderData must not be null when repositories are empty") == true

    @JvmStatic
    fun loginWithPasswordBlocking(request: KeywordMap): Any = runBlocking {
        val homeserverUrl = requireKeywordString(request, BridgeSchema.LoginRequest.homeserverUrl)
        val username = requireKeywordString(request, BridgeSchema.LoginRequest.username)
        val password = requireKeywordString(request, BridgeSchema.LoginRequest.password)
        val databasePath = requireKeywordString(request, BridgeSchema.LoginRequest.databasePath)
        val mediaPath = requireKeywordString(request, BridgeSchema.LoginRequest.mediaPath)
        val repositoriesModule = createRepositoriesModule(databasePath)
        val mediaStoreModule = createMediaStoreModule(mediaPath)
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

    @JvmStatic
    fun fromStoreBlocking(request: KeywordMap): Any? = runBlocking {
        val databasePath = requireKeywordString(request, BridgeSchema.FromStoreRequest.databasePath)
        val mediaPath = requireKeywordString(request, BridgeSchema.FromStoreRequest.mediaPath)
        val repositoriesModule = createRepositoriesModule(databasePath)
        val mediaStoreModule = createMediaStoreModule(mediaPath)
        try {
            MatrixClient.create(
                repositoriesModule = repositoriesModule,
                mediaStoreModule = mediaStoreModule,
                cryptoDriverModule = CryptoDriverModule.vodozemac(),
            ).getOrThrow()
        } catch (error: IllegalArgumentException) {
            if (isFreshStoreWithoutAuth(error)) null else throw error
        }
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

    @JvmStatic
    fun currentUserIdBlocking(request: KeywordMap): String {
        val client = requireKeywordClient(request, BridgeSchema.StartSyncRequest.client)
        return client.userId.full
    }
}
