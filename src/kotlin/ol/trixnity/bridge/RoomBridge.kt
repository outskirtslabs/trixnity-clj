package ol.trixnity.bridge

import kotlinx.coroutines.runBlocking
import de.connect2x.trixnity.client.room
import de.connect2x.trixnity.client.room.message.react
import de.connect2x.trixnity.client.room.message.reply
import de.connect2x.trixnity.client.room.message.text
import de.connect2x.trixnity.core.model.EventId
import de.connect2x.trixnity.core.model.RoomId
import de.connect2x.trixnity.core.model.UserId
import de.connect2x.trixnity.core.model.events.InitialStateEvent
import de.connect2x.trixnity.core.model.events.m.room.EncryptionEventContent

object RoomBridge {
    @JvmStatic
    fun createRoomBlocking(request: KeywordMap): String {
        val client = requireKeywordClient(request, BridgeSchema.CreateRoomRequest.client)
        val roomName = requireKeywordString(request, BridgeSchema.CreateRoomRequest.roomName)
        return runBlocking {
            client.api.room.createRoom(
                name = roomName,
                initialState = listOf(InitialStateEvent(content = EncryptionEventContent(), stateKey = "")),
            ).getOrThrow().full
        }
    }

    @JvmStatic
    fun inviteUserBlocking(request: KeywordMap) {
        val client = requireKeywordClient(request, BridgeSchema.InviteUserRequest.client)
        val roomId = RoomId(requireKeywordString(request, BridgeSchema.InviteUserRequest.roomId))
        val userId = UserId(requireKeywordString(request, BridgeSchema.InviteUserRequest.userId))
        runBlocking {
            client.api.room.inviteUser(roomId, userId).onFailure { ex ->
                if (!isAlreadyInRoomInviteFailure(ex)) throw ex
            }
        }
    }

    @JvmStatic
    fun sendTextReplyBlocking(request: KeywordMap) {
        val client = requireKeywordClient(request, BridgeSchema.SendTextReplyRequest.client)
        val roomId = RoomId(requireKeywordString(request, BridgeSchema.SendTextReplyRequest.roomId))
        val eventId = EventId(requireKeywordString(request, BridgeSchema.SendTextReplyRequest.eventId))
        val body = requireKeywordString(request, BridgeSchema.SendTextReplyRequest.body)
        runBlocking {
            client.room.sendMessage(roomId) {
                text(body)
                reply(eventId, null)
            }
        }
    }

    @JvmStatic
    fun sendReactionBlocking(request: KeywordMap) {
        val client = requireKeywordClient(request, BridgeSchema.SendReactionRequest.client)
        val roomId = RoomId(requireKeywordString(request, BridgeSchema.SendReactionRequest.roomId))
        val eventId = EventId(requireKeywordString(request, BridgeSchema.SendReactionRequest.eventId))
        val key = requireKeywordString(request, BridgeSchema.SendReactionRequest.key)
        runBlocking {
            client.room.sendMessage(roomId) {
                react(eventId, key)
            }
        }
    }

    private fun isAlreadyInRoomInviteFailure(error: Throwable): Boolean =
        error.message?.contains("already in the room", ignoreCase = true) == true
}
