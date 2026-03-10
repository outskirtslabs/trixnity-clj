package ol.trixnity.bridge

object RoomBridge {
    @JvmStatic
    fun createRoomBlocking(request: CreateRoomRequest): String = BridgeRuntime.createRoomBlocking(request)

    @JvmStatic
    fun inviteUserBlocking(request: InviteUserRequest) {
        BridgeRuntime.inviteUserBlocking(request)
    }

    @JvmStatic
    fun sendTextReplyBlocking(request: SendTextReplyRequest) {
        BridgeRuntime.sendTextReplyBlocking(request)
    }

    @JvmStatic
    fun sendReactionBlocking(request: SendReactionRequest) {
        BridgeRuntime.sendReactionBlocking(request)
    }
}
