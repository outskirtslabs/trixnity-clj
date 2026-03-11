package ol.trixnity.bridge

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
    fun createRoom(request: KeywordMap) =
        BridgeAsync.submitFuture(
            scope = BridgeAsync.clientScope(
                requireKeywordClient(request, BridgeSchema.CreateRoomRequest.client),
            ),
        ) {
            val client = requireKeywordClient(request, BridgeSchema.CreateRoomRequest.client)
            val roomName = requireKeywordString(request, BridgeSchema.CreateRoomRequest.roomName)
            client.api.room.createRoom(
                name = roomName,
                initialState = listOf(
                    InitialStateEvent(
                        content = EncryptionEventContent(),
                        stateKey = "",
                    ),
                ),
            ).getOrThrow().full
        }

    @JvmStatic
    fun inviteUser(request: KeywordMap) =
        BridgeAsync.submitFuture(
            scope = BridgeAsync.clientScope(
                requireKeywordClient(request, BridgeSchema.InviteUserRequest.client),
            ),
            timeout = optionalKeywordDuration(request, BridgeSchema.InviteUserRequest.timeout),
        ) {
            val client = requireKeywordClient(request, BridgeSchema.InviteUserRequest.client)
            val roomId = RoomId(requireKeywordString(request, BridgeSchema.InviteUserRequest.roomId))
            val userId = UserId(requireKeywordString(request, BridgeSchema.InviteUserRequest.userId))
            client.api.room.inviteUser(roomId, userId).getOrThrow()
            null
        }

    @JvmStatic
    fun sendMessage(request: KeywordMap) =
        BridgeAsync.submitFuture(
            scope = BridgeAsync.clientScope(
                requireKeywordClient(request, BridgeSchema.SendMessageRequest.client),
            ),
            timeout = optionalKeywordDuration(request, BridgeSchema.SendMessageRequest.timeout),
        ) {
            val client = requireKeywordClient(request, BridgeSchema.SendMessageRequest.client)
            val roomId = RoomId(requireKeywordString(request, BridgeSchema.SendMessageRequest.roomId))
            val message = requireMessageSpec(request, BridgeSchema.SendMessageRequest.message)

            client.room.sendMessage(roomId) {
                when (message.kind) {
                    "text", ":text" -> text(
                        body = message.body,
                        format = message.format,
                        formattedBody = message.formattedBody,
                    )

                    else -> error("unsupported message kind: ${message.kind}")
                }

                message.replyTo?.let {
                    reply(EventId(it.eventId), relationFrom(it.relatesTo))
                }
            }
        }

    @JvmStatic
    fun sendReaction(request: KeywordMap) =
        BridgeAsync.submitFuture(
            scope = BridgeAsync.clientScope(
                requireKeywordClient(request, BridgeSchema.SendReactionRequest.client),
            ),
            timeout = optionalKeywordDuration(request, BridgeSchema.SendReactionRequest.timeout),
        ) {
            val client = requireKeywordClient(request, BridgeSchema.SendReactionRequest.client)
            val roomId = RoomId(requireKeywordString(request, BridgeSchema.SendReactionRequest.roomId))
            val eventId = EventId(requireKeywordString(request, BridgeSchema.SendReactionRequest.eventId))
            val key = requireKeywordString(request, BridgeSchema.SendReactionRequest.key)
            client.room.sendMessage(roomId) {
                react(eventId, key)
            }
        }
}
