package ol.trixnity.bridge

internal data class TxHandle(
    val readConn: Any?,
    val writeConn: Any?,
)

private val currentTx = ThreadLocal<TxHandle?>()

object CurrentTx {
    @JvmStatic
    fun currentReadConn(): Any? = currentTx.get()?.readConn ?: currentTx.get()?.writeConn

    @JvmStatic
    fun currentWriteConn(): Any? = currentTx.get()?.writeConn
}

internal object CurrentTxState {
    val threadLocal: ThreadLocal<TxHandle?> = currentTx
}
