package ol.trixnity.bridge

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import java.time.Duration
import java.util.concurrent.CancellationException as FutureCancellationException
import java.util.concurrent.ExecutionException
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

class BridgeAsyncTest {
    private fun testScope(): CoroutineScope =
        CoroutineScope(SupervisorJob() + Dispatchers.Default)

    @Test
    fun submitFutureCompletesNormally() {
        val scope = testScope()
        try {
            val future = BridgeAsync.submitFuture(scope) {
                delay(10)
                "slept"
            }

            assertEquals("slept", future.get(1, TimeUnit.SECONDS))
            assertTrue(future.isDone)
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun cancellingFutureCancelsTheUnderlyingCoroutine() {
        val scope = testScope()
        val started = java.util.concurrent.CompletableFuture<Unit>()
        val cancelled = java.util.concurrent.CompletableFuture<Unit>()
        val finished = java.util.concurrent.CompletableFuture<Unit>()

        try {
            val future = BridgeAsync.submitFuture(scope) {
                try {
                    started.complete(Unit)
                    delay(60_000)
                    "slept"
                } catch (error: CancellationException) {
                    cancelled.complete(Unit)
                    throw error
                } finally {
                    finished.complete(Unit)
                }
            }

            started.get(1, TimeUnit.SECONDS)
            assertTrue(future.cancel(true))
            cancelled.get(1, TimeUnit.SECONDS)
            finished.get(1, TimeUnit.SECONDS)
            assertTrue(future.isCancelled)
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun coroutineCancellationMarksTheReturnedFutureCancelled() {
        val scope = testScope()
        try {
            val future = BridgeAsync.submitFuture(scope) {
                currentCoroutineContext().cancel(CancellationException("cancelled in coroutine"))
                delay(1)
                "unreachable"
            }

            assertFailsWith<FutureCancellationException> {
                future.get(1, TimeUnit.SECONDS)
            }
            assertTrue(future.isCancelled)
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun explicitTimeoutCompletesExceptionallyInsteadOfCancelling() {
        val scope = testScope()
        try {
            val future = BridgeAsync.submitFuture(scope, Duration.ofMillis(25)) {
                delay(60_000)
                "unreachable"
            }

            val error = assertFailsWith<ExecutionException> {
                future.get(1, TimeUnit.SECONDS)
            }
            assertTrue(error.cause is TimeoutException)
            assertTrue(!future.isCancelled)
        } finally {
            scope.cancel()
        }
    }
}
