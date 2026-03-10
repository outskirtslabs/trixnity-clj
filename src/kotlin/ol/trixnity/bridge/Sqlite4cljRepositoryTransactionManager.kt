package ol.trixnity.bridge

import de.connect2x.trixnity.client.store.repository.RepositoryTransactionManager
import kotlinx.coroutines.asContextElement
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext

class Sqlite4cljRepositoryTransactionManager(
    private val handle: Sqlite4cljRepositoryHandle,
) : RepositoryTransactionManager {
    override suspend fun writeTransaction(block: suspend () -> Unit) = coroutineScope {
        val existing = CurrentTxState.threadLocal.get()
        if (existing?.writeConn != null) {
            block()
        } else {
            val conn = Sqlite4cljClojure.borrowWriterConnection(handle)
            try {
                Sqlite4cljClojure.beginImmediate(conn)
                withContext(CurrentTxState.threadLocal.asContextElement(TxHandle(readConn = conn, writeConn = conn))) {
                    block()
                }
                Sqlite4cljClojure.commit(conn)
            } catch (t: Throwable) {
                Sqlite4cljClojure.rollback(conn)
                throw t
            } finally {
                Sqlite4cljClojure.releaseWriterConnection(handle, conn)
            }
        }
    }

    override suspend fun <T> readTransaction(block: suspend () -> T): T = coroutineScope {
        val existing = CurrentTxState.threadLocal.get()
        when {
            existing?.writeConn != null -> block()
            existing?.readConn != null -> block()
            else -> {
                val conn = Sqlite4cljClojure.borrowReaderConnection(handle)
                try {
                    Sqlite4cljClojure.beginDeferred(conn)
                    withContext(CurrentTxState.threadLocal.asContextElement(TxHandle(readConn = conn, writeConn = null))) {
                        block()
                    }.also {
                        Sqlite4cljClojure.commit(conn)
                    }
                } catch (t: Throwable) {
                    Sqlite4cljClojure.rollback(conn)
                    throw t
                } finally {
                    Sqlite4cljClojure.releaseReaderConnection(handle, conn)
                }
            }
        }
    }
}
