package ol.trixnity.bridge

import de.connect2x.trixnity.client.MatrixClient
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.withTimeout
import java.time.Duration
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeoutException

internal object BridgeAsync {
    private val closeScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val clientScopes = ConcurrentHashMap<MatrixClient, CoroutineScope>()

    fun clientScope(client: MatrixClient): CoroutineScope =
        clientScopes.computeIfAbsent(client) {
            CoroutineScope(SupervisorJob() + Dispatchers.Default)
        }

    fun registerClient(client: MatrixClient): MatrixClient {
        clientScope(client)
        return client
    }

    fun removeClientScope(client: MatrixClient): CoroutineScope? =
        clientScopes.remove(client)

    fun closeScope(): CoroutineScope = closeScope

    @OptIn(ExperimentalCoroutinesApi::class)
    fun <T> submitFuture(
        scope: CoroutineScope,
        timeout: Duration? = null,
        block: suspend CoroutineScope.() -> T,
    ): CompletableFuture<T> {
        val deferred = scope.async {
            if (timeout != null) {
                withTimeout(timeout.toMillis()) { block() }
            } else {
                block()
            }
        }
        val future = JobBackedCompletableFuture(deferred)

        deferred.invokeOnCompletion { error ->
            when {
                error == null -> future.complete(deferred.getCompleted())
                error is TimeoutCancellationException -> {
                    val timeout = TimeoutException(error.message)
                    timeout.initCause(error)
                    future.completeExceptionally(timeout)
                }
                error is CancellationException -> future.cancelFromJob()
                else -> future.completeExceptionally(error)
            }
        }

        return future
    }

    private class JobBackedCompletableFuture<T>(
        private val deferred: Deferred<T>,
    ) : CompletableFuture<T>() {
        override fun cancel(mayInterruptIfRunning: Boolean): Boolean {
            if (isDone || deferred.isCompleted) return false
            deferred.cancel(CancellationException("CompletableFuture cancelled."))
            return super.cancel(mayInterruptIfRunning)
        }

        fun cancelFromJob(): Boolean {
            if (isDone) return false
            return super.cancel(false)
        }
    }
}
