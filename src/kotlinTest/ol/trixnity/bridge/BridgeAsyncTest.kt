package ol.trixnity.bridge

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import java.util.concurrent.atomic.AtomicReference
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import java.util.concurrent.atomic.AtomicInteger

class BridgeAsyncTest {
    private fun testScope(): CoroutineScope =
        CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private class PrivateCallback(
        private val seen: AtomicReference<Any?>,
    ) {
        fun invoke(value: Any?) {
            seen.set(value)
        }
    }

    @Test
    fun submitBridgeTaskCompletesNormally() {
        val scope = testScope()
        val success = CompletableFuture<String>()
        val failure = CompletableFuture<Throwable>()

        try {
            submitBridgeTask(
                scope = scope,
                onSuccess = { value: Any? -> success.complete(value as String) },
                onFailure = { error: Any? -> failure.complete(error as Throwable) },
            ) {
                delay(10)
                "slept"
            }

            assertEquals("slept", success.get(1, TimeUnit.SECONDS))
            assertFalse(failure.isDone)
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun closingTaskCancelsTheUnderlyingCoroutine() {
        val scope = testScope()
        val started = CompletableFuture<Unit>()
        val cancelled = CompletableFuture<Unit>()
        val finished = CompletableFuture<Unit>()
        val success = CompletableFuture<Any?>()
        val failure = CompletableFuture<Any?>()

        try {
            val handle = submitBridgeTask(
                scope = scope,
                onSuccess = { value: Any? -> success.complete(value) },
                onFailure = { error: Any? -> failure.complete(error) },
            ) {
                try {
                    started.complete(Unit)
                    delay(60_000)
                    "slept"
                } catch (_: CancellationException) {
                    cancelled.complete(Unit)
                    throw CancellationException("cancelled")
                } finally {
                    finished.complete(Unit)
                }
            }

            started.get(1, TimeUnit.SECONDS)
            handle.close()
            cancelled.get(1, TimeUnit.SECONDS)
            finished.get(1, TimeUnit.SECONDS)
            assertFalse(success.isDone)
            assertFalse(failure.isDone)
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun coroutineCancellationDoesNotInvokeCallbacks() {
        val scope = testScope()
        val success = CompletableFuture<Any?>()
        val failure = CompletableFuture<Any?>()

        try {
            submitBridgeTask(
                scope = scope,
                onSuccess = { value: Any? -> success.complete(value) },
                onFailure = { error: Any? -> failure.complete(error) },
            ) {
                currentCoroutineContext().cancel(CancellationException("cancelled in coroutine"))
                delay(1)
                "unreachable"
            }

            Thread.sleep(100)
            assertFalse(success.isDone)
            assertFalse(failure.isDone)
            assertTrue(scope.isActive)
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun explicitTimeoutInvokesFailureCallback() {
        val scope = testScope()
        val success = CompletableFuture<Any?>()
        val failure = CompletableFuture<Throwable>()

        try {
            submitBridgeTask(
                scope = scope,
                onSuccess = { value: Any? -> success.complete(value) },
                onFailure = { error: Any? -> failure.complete(error as Throwable) },
                timeout = java.time.Duration.ofMillis(25),
            ) {
                delay(60_000)
                "unreachable"
            }

            val error = failure.get(1, TimeUnit.SECONDS)
            assertTrue(error is kotlinx.coroutines.TimeoutCancellationException)
            assertFalse(success.isDone)
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun submitBridgeTaskSupportsNonPublicCallbackClasses() {
        val scope = testScope()
        val seen = AtomicReference<Any?>()
        val failure = CompletableFuture<Throwable>()

        try {
            submitBridgeTask(
                scope = scope,
                onSuccess = PrivateCallback(seen),
                onFailure = { error: Any? -> failure.complete(error as Throwable) },
            ) {
                "delivered"
            }

            while (seen.get() == null && !failure.isDone) {
                Thread.sleep(10)
            }

            assertEquals("delivered", seen.get())
            assertFalse(failure.isDone)
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun successCallbackFailureDoesNotInvokeFailureCallback() {
        val scope = testScope()
        val failureCalls = AtomicInteger(0)

        try {
            submitBridgeTask(
                scope = scope,
                onSuccess = { _: Any? -> throw IllegalStateException("boom") },
                onFailure = { _: Any? -> failureCalls.incrementAndGet() },
            ) {
                "delivered"
            }

            Thread.sleep(100)
            assertEquals(0, failureCalls.get())
        } finally {
            scope.cancel()
        }
    }
}
