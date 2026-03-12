package ol.trixnity.bridge

import de.connect2x.trixnity.client.verification
import kotlinx.coroutines.flow.map

object VerificationBridge {
    @JvmStatic
    fun currentActiveDeviceVerification(
        client: de.connect2x.trixnity.client.MatrixClient,
    ) = normalizeActiveVerification(client.verification.activeDeviceVerification.value)

    @JvmStatic
    fun activeDeviceVerificationFlow(
        client: de.connect2x.trixnity.client.MatrixClient,
    ) = client.verification.activeDeviceVerification.map(::normalizeActiveVerification)

    @JvmStatic
    fun currentActiveUserVerifications(
        client: de.connect2x.trixnity.client.MatrixClient,
    ) = client.verification.activeUserVerifications.value.map(::normalizeActiveVerification)

    @JvmStatic
    fun activeUserVerificationsFlow(
        client: de.connect2x.trixnity.client.MatrixClient,
    ) = client.verification.activeUserVerifications.map { verifications ->
        verifications.map(::normalizeActiveVerification)
    }

    @JvmStatic
    fun selfVerificationMethods(
        client: de.connect2x.trixnity.client.MatrixClient,
    ) = client.verification.getSelfVerificationMethods().map(::normalizeSelfVerificationMethods)
}
