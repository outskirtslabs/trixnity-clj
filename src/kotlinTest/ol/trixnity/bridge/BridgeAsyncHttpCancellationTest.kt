package ol.trixnity.bridge

import de.connect2x.trixnity.clientserverapi.client.MatrixClientServerApiClientImpl
import de.connect2x.trixnity.clientserverapi.model.server.GetVersions
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.Url
import io.ktor.http.headersOf
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import java.util.concurrent.atomic.AtomicLong

class BridgeAsyncHttpCancellationTest {
    private data class RequestProbe(
        val started: CompletableFuture<Unit> = CompletableFuture(),
        val cancelled: CompletableFuture<Unit> = CompletableFuture(),
        val startedAtNanos: AtomicLong = AtomicLong(0),
        val cancelledAtNanos: AtomicLong = AtomicLong(0),
    )

    private val versionsResponse = GetVersions.Response(
        versions = listOf("v1.11"),
        unstableFeatures = emptyMap(),
    )

    private fun testScope(): CoroutineScope =
        CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private fun delayedGetVersionsFuture(
        scope: CoroutineScope,
        delayMillis: Long,
        probe: RequestProbe? = null,
    ): CompletableFuture<List<String>> {
        val client = MatrixClientServerApiClientImpl(
            baseUrl = Url("https://matrix.host"),
            httpClientEngine = MockEngine { requestData ->
                require(requestData.url.encodedPath == "/_matrix/client/versions")
                recordStarted(probe)
                try {
                    kotlinx.coroutines.delay(delayMillis)
                    respond(
                        Json.encodeToString(versionsResponse),
                        HttpStatusCode.OK,
                        headersOf(
                            HttpHeaders.ContentType,
                            ContentType.Application.Json.toString(),
                        ),
                    )
                } catch (error: CancellationException) {
                    recordCancelled(probe)
                    throw error
                }
            },
        )

        return BridgeAsync.submitFuture(scope) {
            try {
                client.server.getVersions().getOrThrow().versions
            } finally {
                client.close()
            }
        }
    }

    @Test
    fun realHttpBackedOperationCompletesNormally() {
        val scope = testScope()
        try {
            val future = delayedGetVersionsFuture(scope, 10)
            assertEquals(listOf("v1.11"), future.get(1, TimeUnit.SECONDS))
            assertFalse(future.cancel(true))
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun cancellingFutureCancelsRealOperationPromptly() {
        val scope = testScope()
        val probe = RequestProbe()

        try {
            val future = delayedGetVersionsFuture(scope, 60_000, probe)

            assertTrue(awaitSignal(probe.started, 1_000))
            assertTrue(future.cancel(true))
            assertTrue(awaitSignal(probe.cancelled, 1_000))

            val latencyMillis =
                TimeUnit.NANOSECONDS.toMillis(
                    probe.cancelledAtNanos.get() - probe.startedAtNanos.get(),
                )
            assertNotNull(latencyMillis)
            assertTrue(latencyMillis < 1_000)
            assertTrue(future.isCancelled)
        } finally {
            scope.cancel()
        }
    }

    private fun awaitSignal(signal: CompletableFuture<Unit>, timeoutMillis: Long): Boolean =
        try {
            signal.get(timeoutMillis, TimeUnit.MILLISECONDS)
            true
        } catch (_: TimeoutException) {
            false
        }

    private fun recordStarted(probe: RequestProbe?) {
        if (probe == null) return
        if (probe.startedAtNanos.compareAndSet(0, System.nanoTime())) {
            probe.started.complete(Unit)
        }
    }

    private fun recordCancelled(probe: RequestProbe?) {
        if (probe == null) return
        if (probe.cancelledAtNanos.compareAndSet(0, System.nanoTime())) {
            probe.cancelled.complete(Unit)
        }
    }
}
