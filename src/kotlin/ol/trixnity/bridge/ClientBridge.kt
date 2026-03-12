package ol.trixnity.bridge

import de.connect2x.trixnity.client.CryptoDriverModule
import de.connect2x.trixnity.client.MatrixClient
import de.connect2x.trixnity.client.create
import de.connect2x.trixnity.client.cryptodriver.vodozemac.vodozemac
import de.connect2x.trixnity.client.store.ServerData
import de.connect2x.trixnity.clientserverapi.client.MatrixClientAuthProviderData
import de.connect2x.trixnity.clientserverapi.client.SyncState
import de.connect2x.trixnity.clientserverapi.client.classicLoginWithPassword
import de.connect2x.trixnity.clientserverapi.model.authentication.IdentifierType
import de.connect2x.trixnity.clientserverapi.model.user.Profile
import de.connect2x.trixnity.clientserverapi.model.user.avatarUrl
import de.connect2x.trixnity.clientserverapi.model.user.displayName
import de.connect2x.trixnity.clientserverapi.model.user.timeZone
import io.ktor.http.URLBuilder
import io.ktor.http.takeFrom
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.io.Closeable
import java.time.Duration

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

    private fun normalizeProfile(profile: Profile?): Map<clojure.lang.Keyword, Any?>? {
        if (profile == null) return null
        return buildMap {
            profile.displayName?.let { put(BridgeSchema.Profile.displayName, it) }
            profile.avatarUrl?.let { put(BridgeSchema.Profile.avatarUrl, it) }
            profile.timeZone?.let { put(BridgeSchema.Profile.timeZone, it) }
            put(BridgeSchema.Profile.raw, profile)
        }
    }

    private fun normalizeServerData(serverData: ServerData?): Map<clojure.lang.Keyword, Any?>? {
        if (serverData == null) return null
        return buildMap {
            put(BridgeSchema.ServerData.versions, serverData.versions)
            put(BridgeSchema.ServerData.mediaConfig, serverData.mediaConfig)
            serverData.capabilities?.let { put(BridgeSchema.ServerData.capabilities, it) }
            serverData.auth?.let { put(BridgeSchema.ServerData.auth, it) }
            put(BridgeSchema.ServerData.raw, serverData)
        }
    }

    @JvmStatic
    fun openClient(request: KeywordMap, onSuccess: Any, onFailure: Any): Closeable =
        submitBridgeTask(
            scope = BridgeAsync.closeScope(),
            onSuccess = onSuccess,
            onFailure = onFailure,
        ) {
            createClient(request)
        }

    @JvmStatic
    fun startSync(client: MatrixClient, onSuccess: Any, onFailure: Any): Closeable =
        submitBridgeTask(
            scope = BridgeAsync.clientScope(client),
            onSuccess = onSuccess,
            onFailure = onFailure,
        ) {
            client.startSync()
            null
        }

    @JvmStatic
    fun awaitRunning(
        client: MatrixClient,
        timeout: Duration?,
        onSuccess: Any,
        onFailure: Any,
    ): Closeable = submitBridgeTask(
        scope = BridgeAsync.clientScope(client),
        onSuccess = onSuccess,
        onFailure = onFailure,
        timeout = timeout,
    ) {
        client.syncState.first { it == SyncState.RUNNING }
        null
    }

    @JvmStatic
    fun stopSync(client: MatrixClient, onSuccess: Any, onFailure: Any): Closeable =
        submitBridgeTask(
            scope = BridgeAsync.clientScope(client),
            onSuccess = onSuccess,
            onFailure = onFailure,
        ) {
            client.stopSync()
            null
        }

    @JvmStatic
    fun closeClient(client: MatrixClient, onSuccess: Any, onFailure: Any): Closeable =
        submitBridgeTask(
            scope = BridgeAsync.closeScope(),
            onSuccess = onSuccess,
            onFailure = onFailure,
        ) {
            withContext(NonCancellable) {
                BridgeAsync.removeClientScope(client)
                    ?.coroutineContext
                    ?.get(kotlinx.coroutines.Job)
                    ?.cancelAndJoin()
                client.closeSuspending()
            }
            null
        }

    @JvmStatic
    fun currentUserId(client: MatrixClient): String = client.userId.full

    @JvmStatic
    fun currentSyncState(client: MatrixClient): String =
        client.syncState.value.name.lowercase()

    @JvmStatic
    fun currentProfile(client: MatrixClient): Map<clojure.lang.Keyword, Any?>? =
        normalizeProfile(client.profile.value)

    @JvmStatic
    fun profileFlow(client: MatrixClient) =
        client.profile.map { normalizeProfile(it) }

    @JvmStatic
    fun currentServerData(client: MatrixClient): Map<clojure.lang.Keyword, Any?>? =
        normalizeServerData(client.serverData.value)

    @JvmStatic
    fun serverDataFlow(client: MatrixClient) =
        client.serverData.map { normalizeServerData(it) }

    @JvmStatic
    fun syncStateFlow(client: MatrixClient) =
        client.syncState.map { it.name.lowercase() }

    @JvmStatic
    fun currentInitialSyncDone(client: MatrixClient): Boolean =
        client.initialSyncDone.value

    @JvmStatic
    fun initialSyncDoneFlow(client: MatrixClient) = client.initialSyncDone

    @JvmStatic
    fun currentLoginState(client: MatrixClient): String? =
        client.loginState.value?.name?.lowercase()

    @JvmStatic
    fun loginStateFlow(client: MatrixClient) =
        client.loginState.map { it?.name?.lowercase() }

    @JvmStatic
    fun currentStarted(client: MatrixClient): Boolean = client.started.value

    @JvmStatic
    fun startedFlow(client: MatrixClient) = client.started
}
