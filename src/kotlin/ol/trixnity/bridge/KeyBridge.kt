package ol.trixnity.bridge

import de.connect2x.trixnity.client.MatrixClient
import de.connect2x.trixnity.client.key
import de.connect2x.trixnity.client.key.KeyBackupService
import de.connect2x.trixnity.clientserverapi.client.UIA
import de.connect2x.trixnity.clientserverapi.model.authentication.IdentifierType
import de.connect2x.trixnity.clientserverapi.model.uia.AuthenticationRequest
import de.connect2x.trixnity.clientserverapi.model.uia.AuthenticationType
import de.connect2x.trixnity.clientserverapi.model.uia.UIAState
import de.connect2x.trixnity.core.ErrorResponse
import de.connect2x.trixnity.core.model.EventId
import de.connect2x.trixnity.core.model.RoomId
import de.connect2x.trixnity.core.model.UserId
import kotlinx.coroutines.flow.map
import org.koin.core.qualifier.named
import java.io.Closeable

private val de.connect2x.trixnity.client.MatrixClient.keyBackup: KeyBackupService
    get() = di.get(named<KeyBackupService>())

object KeyBridge {
    @JvmStatic
    fun currentBootstrapRunning(
        client: de.connect2x.trixnity.client.MatrixClient,
    ): Boolean = client.key.bootstrapRunning.value

    @JvmStatic
    fun bootstrapRunningFlow(
        client: de.connect2x.trixnity.client.MatrixClient,
    ) = client.key.bootstrapRunning

    @JvmStatic
    fun bootstrapCrossSigning(
        client: MatrixClient,
        options: KeywordMap,
        onSuccess: Any,
        onFailure: Any,
    ): Closeable = submitBridgeTask(
        scope = BridgeAsync.clientScope(client),
        onSuccess = onSuccess,
        onFailure = onFailure,
    ) {
        val parsedOptions = parseBootstrapCrossSigningOptions(options)
        val result = client.key.bootstrapCrossSigning()
        val completedUia = completeBootstrapCrossSigningUia(
            result.result.getOrThrow(),
            parsedOptions,
            defaultUserIdentifier = client.userId.full,
        )
        normalizeBootstrapCrossSigning(result.recoveryKey, completedUia)
    }

    @JvmStatic
    fun bootstrapCrossSigningFromPassphrase(
        client: MatrixClient,
        passphrase: String,
        options: KeywordMap,
        onSuccess: Any,
        onFailure: Any,
    ): Closeable = submitBridgeTask(
        scope = BridgeAsync.clientScope(client),
        onSuccess = onSuccess,
        onFailure = onFailure,
    ) {
        val parsedOptions = parseBootstrapCrossSigningOptions(options)
        val result = client.key.bootstrapCrossSigningFromPassphrase(passphrase)
        val completedUia = completeBootstrapCrossSigningUia(
            result.result.getOrThrow(),
            parsedOptions,
            defaultUserIdentifier = client.userId.full,
        )
        normalizeBootstrapCrossSigning(result.recoveryKey, completedUia)
    }

    @JvmStatic
    fun currentBackupVersion(
        client: de.connect2x.trixnity.client.MatrixClient,
    ) = normalizeBackupVersion(client.keyBackup.version.value)

    @JvmStatic
    fun backupVersionFlow(
        client: de.connect2x.trixnity.client.MatrixClient,
    ) = client.keyBackup.version.map(::normalizeBackupVersion)

    @JvmStatic
    fun trustLevel(
        client: de.connect2x.trixnity.client.MatrixClient,
        userId: String,
        deviceId: String,
    ) = client.key.getTrustLevel(UserId(userId), deviceId).map(::normalizeDeviceTrustLevel)

    @JvmStatic
    fun trustLevelByTimelineEvent(
        client: de.connect2x.trixnity.client.MatrixClient,
        roomId: String,
        eventId: String,
    ) = client.key.getTrustLevel(RoomId(roomId), EventId(eventId)).map(::normalizeDeviceTrustLevel)

    @JvmStatic
    fun trustLevel(
        client: de.connect2x.trixnity.client.MatrixClient,
        userId: String,
    ) = client.key.getTrustLevel(UserId(userId)).map(::normalizeUserTrustLevel)

    @JvmStatic
    fun deviceKeys(
        client: de.connect2x.trixnity.client.MatrixClient,
        userId: String,
    ) = client.key.getDeviceKeys(UserId(userId)).map { keys ->
        keys?.map(::normalizeContent)
    }

    @JvmStatic
    fun crossSigningKeys(
        client: de.connect2x.trixnity.client.MatrixClient,
        userId: String,
    ) = client.key.getCrossSigningKeys(UserId(userId)).map { keys ->
        keys?.map(::normalizeContent)
    }
}

internal data class BootstrapCrossSigningOptions(
    val password: String? = null,
    val userIdentifier: String? = null,
)

private fun parseBootstrapCrossSigningOptions(options: KeywordMap): BootstrapCrossSigningOptions =
    BootstrapCrossSigningOptions(
        password = options[BridgeSchema.password] as? String,
        userIdentifier = options[BridgeSchema.userId] as? String,
    )

internal suspend fun completeBootstrapCrossSigningUia(
    uia: UIA<Unit>,
    options: BootstrapCrossSigningOptions,
    defaultUserIdentifier: String,
): UIA<Unit> {
    val password = options.password ?: return uia
    val request = AuthenticationRequest.Password(
        identifier = IdentifierType.User(options.userIdentifier ?: defaultUserIdentifier),
        password = password,
    )
    return when (uia) {
        is UIA.Success -> uia
        is UIA.Step -> if (hasPasswordFlow(uia.state)) uia.authenticate(request).getOrThrow() else uia
        is UIA.Error -> if (hasPasswordFlow(uia.state)) uia.authenticate(request).getOrThrow() else uia
    }
}

private fun hasPasswordFlow(state: UIAState): Boolean =
    state.flows.any { flow ->
        flow.stages.any { stage -> stage.name == AuthenticationType.Password.name }
    }

internal fun normalizeBootstrapCrossSigning(
    recoveryKey: String,
    uia: UIA<Unit>,
): Map<clojure.lang.Keyword, Any?> =
    buildMap {
        put(BridgeSchema.MessageSpec.kind, bootstrapCrossSigningKind(uia))
        put(BridgeSchema.recoveryKey, recoveryKey)
        put(BridgeSchema.uia, normalizeUia(uia))
    }

private fun bootstrapCrossSigningKind(uia: UIA<Unit>): String =
    when (uia) {
        is UIA.Success -> "success"
        is UIA.Step -> "uia-required"
        is UIA.Error -> "uia-error"
    }

private fun normalizeUia(uia: UIA<Unit>): Map<clojure.lang.Keyword, Any?> =
    when (uia) {
        is UIA.Success -> mapOf(BridgeSchema.MessageSpec.kind to "success")
        is UIA.Step -> normalizeUiaState("step", uia.state)
        is UIA.Error -> normalizeUiaState("error", uia.state, uia.errorResponse)
    }

private fun normalizeUiaState(
    kind: String,
    state: UIAState,
    errorResponse: ErrorResponse? = null,
): Map<clojure.lang.Keyword, Any?> =
    buildMap {
        put(BridgeSchema.MessageSpec.kind, kind)
        put(BridgeSchema.completed, state.completed.map { it.name })
        put(
            BridgeSchema.flows,
            state.flows
                .map { flow -> flow.stages.map { it.name } }
                .sortedWith(compareBy { it.joinToString("\u0000") }),
        )
        state.session?.let { put(BridgeSchema.session, it) }
        errorResponse?.let {
            put(BridgeSchema.errorKind, normalizedKind(it::class.simpleName))
            put(BridgeSchema.errorMessage, it.error)
        }
    }
