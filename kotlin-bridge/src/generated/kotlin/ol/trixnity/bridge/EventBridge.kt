package ol.trixnity.bridge

object EventBridge {
    @JvmStatic
    fun startTimelinePump(request: StartTimelinePumpRequest): TimelinePumpHandle =
        BridgeRuntime.startTimelinePump(request)

    @JvmStatic
    fun stopTimelinePump(request: StopTimelinePumpRequest) {
        BridgeRuntime.stopTimelinePump(request)
    }
}
