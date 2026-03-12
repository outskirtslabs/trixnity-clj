package ol.trixnity.bridge

import de.connect2x.trixnity.client.MatrixClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import java.util.concurrent.ConcurrentHashMap

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
}
