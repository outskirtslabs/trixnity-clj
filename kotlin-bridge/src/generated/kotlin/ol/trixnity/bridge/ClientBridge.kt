package ol.trixnity.bridge

object ClientBridge {
    @JvmStatic
    fun loginBlocking(request: LoginRequest): Any = BridgeRuntime.loginBlocking(request)

    @JvmStatic
    fun fromStoreBlocking(request: FromStoreRequest): Any? = BridgeRuntime.fromStoreBlocking(request)

    @JvmStatic
    fun startSyncBlocking(request: StartSyncRequest) {
        BridgeRuntime.startSyncBlocking(request)
    }
}
