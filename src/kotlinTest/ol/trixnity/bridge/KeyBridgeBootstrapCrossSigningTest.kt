package ol.trixnity.bridge

import de.connect2x.trixnity.clientserverapi.client.UIA
import de.connect2x.trixnity.clientserverapi.model.authentication.IdentifierType
import de.connect2x.trixnity.clientserverapi.model.uia.AuthenticationRequest
import de.connect2x.trixnity.clientserverapi.model.uia.AuthenticationType
import de.connect2x.trixnity.clientserverapi.model.uia.UIAState
import de.connect2x.trixnity.core.ErrorResponse
import io.ktor.http.Url
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertSame
import kotlin.test.assertTrue

class KeyBridgeBootstrapCrossSigningTest {
    private fun passwordState(): UIAState =
        UIAState(
            flows = setOf(
                UIAState.FlowInformation(listOf(AuthenticationType.Password)),
            ),
            session = "session1",
        )

    @Test
    fun passwordUiaCompletionAuthenticatesStepAndRunsSuccessCallback() = runBlocking {
        var seenRequest: AuthenticationRequest? = null
        var successCallbackRan = false
        val step = UIA.Step<Unit>(
            state = passwordState(),
            getFallbackUrlCallback = { Url("https://matrix.example.org/_matrix/client/fallback") },
            authenticateCallback = { request ->
                seenRequest = request
                Result.success(UIA.Success(Unit))
            },
            onSuccessCallback = { successCallbackRan = true },
        )

        val completed = completeBootstrapCrossSigningUia(
            step,
            BootstrapCrossSigningOptions(password = "secret"),
            defaultUserIdentifier = "@bot:example.org",
        )

        assertIs<UIA.Success<Unit>>(completed)
        assertTrue(successCallbackRan)
        val passwordRequest = assertIs<AuthenticationRequest.Password>(seenRequest)
        assertEquals("secret", passwordRequest.password)
        assertEquals("@bot:example.org", assertIs<IdentifierType.User>(passwordRequest.identifier).user)
    }

    @Test
    fun passwordUiaCompletionReturnsUnchangedStepWhenPasswordIsMissing() = runBlocking {
        val step = UIA.Step<Unit>(
            state = passwordState(),
            getFallbackUrlCallback = { Url("https://matrix.example.org/_matrix/client/fallback") },
            authenticateCallback = { error("should not authenticate without a password") },
        )

        val completed = completeBootstrapCrossSigningUia(
            step,
            BootstrapCrossSigningOptions(),
            defaultUserIdentifier = "@bot:example.org",
        )

        assertSame(step, completed)
    }

    @Test
    fun bootstrapResultNormalizationIncludesRecoveryKeyAndRequiredStages() {
        val step = UIA.Step<Unit>(
            state = passwordState(),
            getFallbackUrlCallback = { Url("https://matrix.example.org/_matrix/client/fallback") },
            authenticateCallback = { error("not used") },
        )

        val normalized = normalizeBootstrapCrossSigning("RECOVERY", step)
        val uia = normalized[BridgeSchema.uia] as Map<*, *>

        assertEquals("uia-required", normalized[BridgeSchema.MessageSpec.kind])
        assertEquals("RECOVERY", normalized[BridgeSchema.recoveryKey])
        assertEquals("step", uia[BridgeSchema.MessageSpec.kind])
        assertEquals(listOf(listOf("m.login.password")), uia[BridgeSchema.flows])
        assertEquals("session1", uia[BridgeSchema.session])
    }

    @Test
    fun bootstrapResultNormalizationIncludesUiaErrorDetails() {
        val uiaError = UIA.Error<Unit>(
            state = passwordState(),
            errorResponse = ErrorResponse.Unauthorized("bad password"),
            getFallbackUrlCallback = { Url("https://matrix.example.org/_matrix/client/fallback") },
            authenticateCallback = { error("not used") },
        )

        val normalized = normalizeBootstrapCrossSigning("RECOVERY", uiaError)
        val uia = normalized[BridgeSchema.uia] as Map<*, *>

        assertEquals("uia-error", normalized[BridgeSchema.MessageSpec.kind])
        assertEquals("error", uia[BridgeSchema.MessageSpec.kind])
        assertEquals("unauthorized", uia[BridgeSchema.errorKind])
        assertEquals("bad password", uia[BridgeSchema.errorMessage])
        assertEquals(listOf(listOf("m.login.password")), uia[BridgeSchema.flows])
    }
}
