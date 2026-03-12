package ol.trixnity.bridge

import de.connect2x.trixnity.client.MatrixClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import java.io.Closeable

private class FlowSubscription(
    private val job: Job,
) : Closeable {
    override fun close() {
        job.cancel()
    }
}

object FlowBridge {
    @JvmStatic
    fun observe(flow: Flow<*>, scope: CoroutineScope, callback: Any): Closeable {
        val job = scope.launch {
            flow.collect { value ->
                try {
                    invokeCallback(callback, value)
                } catch (_: Throwable) {
                    // Callback failures are isolated to that delivery.
                }
            }
        }
        return FlowSubscription(job)
    }

    @JvmStatic
    fun clientScope(client: MatrixClient): CoroutineScope =
        BridgeAsync.clientScope(client)
}
