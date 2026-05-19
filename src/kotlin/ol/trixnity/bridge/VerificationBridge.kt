package ol.trixnity.bridge

import de.connect2x.trixnity.client.verification
import de.connect2x.trixnity.client.verification.ActiveSasVerificationMethod
import de.connect2x.trixnity.client.verification.ActiveSasVerificationState
import de.connect2x.trixnity.client.verification.ActiveVerification
import de.connect2x.trixnity.client.verification.ActiveVerificationState
import de.connect2x.trixnity.core.model.EventId
import de.connect2x.trixnity.core.model.RoomId
import de.connect2x.trixnity.core.model.UserId
import de.connect2x.trixnity.core.model.events.m.key.verification.VerificationMethod
import kotlinx.coroutines.flow.map
import java.io.Closeable

object VerificationBridge {
    private fun normalizeForClient(
        client: de.connect2x.trixnity.client.MatrixClient,
        verification: ActiveVerification?,
    ) = normalizeActiveVerification(verification, client.userId.full, client.deviceId)

    private fun allActiveVerifications(
        client: de.connect2x.trixnity.client.MatrixClient,
    ): Sequence<ActiveVerification> = sequence {
        client.verification.activeDeviceVerification.value?.let { yield(it) }
        yieldAll(client.verification.activeUserVerifications.value)
    }

    private fun findActiveVerification(
        client: de.connect2x.trixnity.client.MatrixClient,
        verificationId: String,
    ): ActiveVerification = allActiveVerifications(client)
        .firstOrNull { activeVerificationId(it) == verificationId }
        ?: throw NoSuchElementException("active verification not found: $verificationId")

    private fun unsupportedState(
        verification: ActiveVerification,
        expected: String,
    ): Nothing {
        val state = verification.state.value
        throw IllegalStateException(
            "verification ${activeVerificationId(verification)} is in " +
                "state ${state::class.simpleName}; expected $expected",
        )
    }

    private suspend fun actOnVerification(
        client: de.connect2x.trixnity.client.MatrixClient,
        verificationId: String,
        block: suspend (ActiveVerification) -> Unit,
    ): Map<clojure.lang.Keyword, Any?>? {
        val verification = findActiveVerification(client, verificationId)
        block(verification)
        return normalizeForClient(client, verification)
    }

    private fun sasState(
        verification: ActiveVerification,
    ): ActiveSasVerificationState? =
        ((verification.state.value as? ActiveVerificationState.Start)?.method as? ActiveSasVerificationMethod)
            ?.state
            ?.value

    @JvmStatic
    fun currentActiveDeviceVerification(
        client: de.connect2x.trixnity.client.MatrixClient,
    ) = normalizeForClient(client, client.verification.activeDeviceVerification.value)

    @JvmStatic
    fun activeDeviceVerificationFlow(
        client: de.connect2x.trixnity.client.MatrixClient,
    ) = client.verification.activeDeviceVerification.map { normalizeForClient(client, it) }

    @JvmStatic
    fun currentActiveUserVerifications(
        client: de.connect2x.trixnity.client.MatrixClient,
    ) = client.verification.activeUserVerifications.value.map { normalizeForClient(client, it) }

    @JvmStatic
    fun activeUserVerificationsFlow(
        client: de.connect2x.trixnity.client.MatrixClient,
    ) = client.verification.activeUserVerifications.map { verifications ->
        verifications.map { normalizeForClient(client, it) }
    }

    @JvmStatic
    fun selfVerificationMethods(
        client: de.connect2x.trixnity.client.MatrixClient,
    ) = client.verification.getSelfVerificationMethods().map(::normalizeSelfVerificationMethods)

    @JvmStatic
    fun startDeviceVerification(
        client: de.connect2x.trixnity.client.MatrixClient,
        userId: String,
        deviceId: String,
        onSuccess: Any,
        onFailure: Any,
    ): Closeable = submitBridgeTask(
        scope = BridgeAsync.clientScope(client),
        onSuccess = onSuccess,
        onFailure = onFailure,
    ) {
        val verification = client.verification
            .createDeviceVerificationRequest(UserId(userId), setOf(deviceId))
            .getOrThrow()
        normalizeForClient(client, verification)
    }

    @JvmStatic
    fun startUserVerification(
        client: de.connect2x.trixnity.client.MatrixClient,
        userId: String,
        onSuccess: Any,
        onFailure: Any,
    ): Closeable = submitBridgeTask(
        scope = BridgeAsync.clientScope(client),
        onSuccess = onSuccess,
        onFailure = onFailure,
    ) {
        val verification = client.verification
            .createUserVerificationRequest(UserId(userId))
            .getOrThrow()
        normalizeForClient(client, verification)
    }

    @JvmStatic
    fun getActiveUserVerification(
        client: de.connect2x.trixnity.client.MatrixClient,
        roomId: String,
        eventId: String,
        onSuccess: Any,
        onFailure: Any,
    ): Closeable = submitBridgeTask(
        scope = BridgeAsync.clientScope(client),
        onSuccess = onSuccess,
        onFailure = onFailure,
    ) {
        normalizeForClient(
            client,
            client.verification.getActiveUserVerification(RoomId(roomId), EventId(eventId)),
        )
    }

    @JvmStatic
    fun readyVerification(
        client: de.connect2x.trixnity.client.MatrixClient,
        verificationId: String,
        onSuccess: Any,
        onFailure: Any,
    ): Closeable = submitBridgeTask(
        scope = BridgeAsync.clientScope(client),
        onSuccess = onSuccess,
        onFailure = onFailure,
    ) {
        actOnVerification(client, verificationId) { verification ->
            when (val state = verification.state.value) {
                is ActiveVerificationState.TheirRequest -> state.ready()
                else -> unsupportedState(verification, "their-request")
            }
        }
    }

    @JvmStatic
    fun startSasVerification(
        client: de.connect2x.trixnity.client.MatrixClient,
        verificationId: String,
        onSuccess: Any,
        onFailure: Any,
    ): Closeable = submitBridgeTask(
        scope = BridgeAsync.clientScope(client),
        onSuccess = onSuccess,
        onFailure = onFailure,
    ) {
        actOnVerification(client, verificationId) { verification ->
            when (val state = verification.state.value) {
                is ActiveVerificationState.Ready -> state.start(VerificationMethod.Sas)
                else -> unsupportedState(verification, "ready")
            }
        }
    }

    @JvmStatic
    fun acceptSasVerification(
        client: de.connect2x.trixnity.client.MatrixClient,
        verificationId: String,
        onSuccess: Any,
        onFailure: Any,
    ): Closeable = submitBridgeTask(
        scope = BridgeAsync.clientScope(client),
        onSuccess = onSuccess,
        onFailure = onFailure,
    ) {
        actOnVerification(client, verificationId) { verification ->
            when (val state = sasState(verification)) {
                is ActiveSasVerificationState.TheirSasStart -> state.accept()
                else -> unsupportedState(verification, "their-sas-start")
            }
        }
    }

    @JvmStatic
    fun acceptVerification(
        client: de.connect2x.trixnity.client.MatrixClient,
        verificationId: String,
        onSuccess: Any,
        onFailure: Any,
    ): Closeable = submitBridgeTask(
        scope = BridgeAsync.clientScope(client),
        onSuccess = onSuccess,
        onFailure = onFailure,
    ) {
        actOnVerification(client, verificationId) { verification ->
            when (val state = verification.state.value) {
                is ActiveVerificationState.TheirRequest -> state.ready()
                is ActiveVerificationState.Start -> {
                    when (val activeSasState = sasState(verification)) {
                        is ActiveSasVerificationState.TheirSasStart -> activeSasState.accept()
                        else -> unsupportedState(verification, "their-request or their-sas-start")
                    }
                }

                else -> unsupportedState(verification, "their-request or their-sas-start")
            }
        }
    }

    @JvmStatic
    fun confirmVerification(
        client: de.connect2x.trixnity.client.MatrixClient,
        verificationId: String,
        onSuccess: Any,
        onFailure: Any,
    ): Closeable = submitBridgeTask(
        scope = BridgeAsync.clientScope(client),
        onSuccess = onSuccess,
        onFailure = onFailure,
    ) {
        actOnVerification(client, verificationId) { verification ->
            when (val state = sasState(verification)) {
                is ActiveSasVerificationState.ComparisonByUser -> state.match()
                else -> unsupportedState(verification, "comparison-by-user")
            }
        }
    }

    @JvmStatic
    fun noMatchVerification(
        client: de.connect2x.trixnity.client.MatrixClient,
        verificationId: String,
        onSuccess: Any,
        onFailure: Any,
    ): Closeable = submitBridgeTask(
        scope = BridgeAsync.clientScope(client),
        onSuccess = onSuccess,
        onFailure = onFailure,
    ) {
        actOnVerification(client, verificationId) { verification ->
            when (val state = sasState(verification)) {
                is ActiveSasVerificationState.ComparisonByUser -> state.noMatch()
                else -> unsupportedState(verification, "comparison-by-user")
            }
        }
    }

    @JvmStatic
    fun cancelVerification(
        client: de.connect2x.trixnity.client.MatrixClient,
        verificationId: String,
        reason: String?,
        onSuccess: Any,
        onFailure: Any,
    ): Closeable = submitBridgeTask(
        scope = BridgeAsync.clientScope(client),
        onSuccess = onSuccess,
        onFailure = onFailure,
    ) {
        actOnVerification(client, verificationId) { verification ->
            verification.cancel(reason ?: "user cancelled verification")
        }
    }
}
