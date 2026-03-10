package ol.trixnity.bridge

data class TimelinePumpHandle(val id: String)

data class CreateRoomRequest(val payload: Map<String, Any?>)

data class InviteUserRequest(val payload: Map<String, Any?>)

data class SendTextReplyRequest(val payload: Map<String, Any?>)

data class SendReactionRequest(val payload: Map<String, Any?>)

data class StartTimelinePumpRequest(val payload: Map<String, Any?>)

data class StopTimelinePumpRequest(val payload: Map<String, Any?>)
