package ol.trixnity.bridge

import clojure.lang.Keyword
import de.connect2x.trixnity.client.flatten
import de.connect2x.trixnity.client.flattenValues
import de.connect2x.trixnity.client.room
import de.connect2x.trixnity.client.room.message.MessageBuilder
import de.connect2x.trixnity.client.room.message.audio
import de.connect2x.trixnity.client.room.message.emote
import de.connect2x.trixnity.client.room.message.file
import de.connect2x.trixnity.client.room.message.image
import de.connect2x.trixnity.clientserverapi.model.room.CreateRoom
import de.connect2x.trixnity.clientserverapi.model.room.DirectoryVisibility
import de.connect2x.trixnity.client.room.message.react
import de.connect2x.trixnity.client.room.message.reply
import de.connect2x.trixnity.client.room.message.text
import de.connect2x.trixnity.client.store.Room
import de.connect2x.trixnity.core.model.EventId
import de.connect2x.trixnity.core.model.RoomAliasId
import de.connect2x.trixnity.core.model.RoomId
import de.connect2x.trixnity.core.model.UserId
import de.connect2x.trixnity.core.model.events.InitialStateEvent
import de.connect2x.trixnity.core.model.events.RoomAccountDataEventContent
import de.connect2x.trixnity.core.model.events.StateEventContent
import de.connect2x.trixnity.core.model.events.m.TypingEventContent
import de.connect2x.trixnity.core.model.events.m.room.EncryptionEventContent
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.io.Closeable
import java.time.Duration

internal suspend fun MessageBuilder.applyMessageSpec(messageSpec: MessageSpec) {
    when (messageSpec) {
        is TextMessageSpec -> text(
            body = messageSpec.body,
            format = messageSpec.format,
            formattedBody = messageSpec.formattedBody,
        )

        is EmoteMessageSpec -> emote(
            body = messageSpec.body,
            format = messageSpec.format,
            formattedBody = messageSpec.formattedBody,
        )

        is AudioMessageSpec -> audio(
            body = messageSpec.body,
            audio = byteArrayFlowFromPath(messageSpec.sourcePath),
            format = messageSpec.format,
            formattedBody = messageSpec.formattedBody,
            fileName = messageSpec.fileName,
            type = messageSpec.mimeType,
            size = messageSpec.sizeBytes,
            duration = messageSpec.durationMillis,
        )

        is ImageMessageSpec -> image(
            body = messageSpec.body,
            image = byteArrayFlowFromPath(messageSpec.sourcePath),
            format = messageSpec.format,
            formattedBody = messageSpec.formattedBody,
            fileName = messageSpec.fileName,
            type = messageSpec.mimeType,
            size = messageSpec.sizeBytes,
            height = messageSpec.height,
            width = messageSpec.width,
        )

        is FileMessageSpec -> file(
            body = messageSpec.body,
            file = byteArrayFlowFromPath(messageSpec.sourcePath),
            format = messageSpec.format,
            formattedBody = messageSpec.formattedBody,
            fileName = messageSpec.fileName,
            type = messageSpec.mimeType,
            size = messageSpec.sizeBytes,
        )
    }

    messageSpec.replyTo?.let {
        reply(EventId(it.eventId), relationFrom(it.relatesTo))
    }
}

internal suspend fun buildMessageContent(
    messageBuilder: MessageBuilder,
    messageSpec: MessageSpec,
) = messageBuilder.build {
    applyMessageSpec(messageSpec)
}

internal fun parseJoinRoomTarget(roomIdOrAlias: String): Any =
    when {
        roomIdOrAlias.startsWith(RoomAliasId.sigilCharacter) -> RoomAliasId(roomIdOrAlias)
        roomIdOrAlias.startsWith(RoomId.sigilCharacter) -> RoomId(roomIdOrAlias)
        else -> throw IllegalArgumentException(
            "join room target must be a Matrix room id or room alias: $roomIdOrAlias",
        )
    }

object RoomBridge {
    private data class CreateRoomSpec(
        val roomName: String? = null,
        val topic: String? = null,
        val invite: Set<UserId>? = null,
        val preset: CreateRoom.Request.Preset? = null,
        val isDirect: Boolean? = null,
        val visibility: DirectoryVisibility? = null,
    )

    private fun parseCreateRoomSpec(request: KeywordMap): CreateRoomSpec {
        val invite = (request[BridgeSchema.invite] as? Iterable<*>)?.map {
            UserId(it?.toString() ?: error("invite list contains null entry"))
        }?.toSet()?.takeIf { it.isNotEmpty() }

        val preset = optionalKeywordString(request, BridgeSchema.preset)?.removePrefix(":")?.let {
            when (it) {
                "private-chat" -> CreateRoom.Request.Preset.PRIVATE
                "public-chat" -> CreateRoom.Request.Preset.PUBLIC
                "trusted-private-chat" -> CreateRoom.Request.Preset.TRUSTED_PRIVATE
                else -> error("unsupported create-room preset: $it")
            }
        }

        val visibility =
            optionalKeywordString(request, BridgeSchema.visibility)?.removePrefix(":")?.let {
                when (it) {
                    "private" -> DirectoryVisibility.PRIVATE
                    "public" -> DirectoryVisibility.PUBLIC
                    else -> error("unsupported create-room visibility: $it")
                }
            }

        return CreateRoomSpec(
            roomName = optionalKeywordString(request, BridgeSchema.roomName),
            topic = optionalKeywordString(request, BridgeSchema.topic),
            invite = invite,
            preset = preset,
            isDirect = request[BridgeSchema.isDirect] as? Boolean,
            visibility = visibility,
        )
    }

    private fun normalizeRoom(room: Room?): Map<Keyword, Any?>? {
        if (room == null) return null
        return buildMap {
            put(BridgeSchema.Room.roomId, room.roomId.full)
            put(BridgeSchema.Room.membership, room.membership.name.lowercase())
            room.name?.explicitName?.let { put(BridgeSchema.Room.roomName, it) }
            put(BridgeSchema.Room.isDirect, room.isDirect)
            put(BridgeSchema.Room.raw, room)
        }
    }

    private fun normalizeTypingEventContent(
        content: TypingEventContent,
    ): Map<Keyword, Any?> =
        buildMap {
            put(BridgeSchema.TypingEventContent.users, content.users.map { it.full }.toSet())
            put(BridgeSchema.TypingEventContent.raw, content)
        }

    @JvmStatic
    fun createRoom(
        client: de.connect2x.trixnity.client.MatrixClient,
        request: KeywordMap,
        onSuccess: Any,
        onFailure: Any,
    ): Closeable = submitBridgeTask(
        scope = BridgeAsync.clientScope(client),
        onSuccess = onSuccess,
        onFailure = onFailure,
    ) {
        val parsedRequest = parseCreateRoomSpec(request)
        client.api.room.createRoom(
            visibility = parsedRequest.visibility ?: DirectoryVisibility.PRIVATE,
            name = parsedRequest.roomName,
            topic = parsedRequest.topic,
            invite = parsedRequest.invite,
            preset = parsedRequest.preset,
            isDirect = parsedRequest.isDirect,
            initialState = listOf(
                InitialStateEvent(
                    content = EncryptionEventContent(),
                    stateKey = "",
                ),
            ),
        ).getOrThrow().full
    }

    @JvmStatic
    fun inviteUser(
        client: de.connect2x.trixnity.client.MatrixClient,
        roomId: String,
        userId: String,
        timeout: Duration?,
        onSuccess: Any,
        onFailure: Any,
    ): Closeable = submitBridgeTask(
        scope = BridgeAsync.clientScope(client),
        onSuccess = onSuccess,
        onFailure = onFailure,
        timeout = timeout,
    ) {
        client.api.room.inviteUser(RoomId(roomId), UserId(userId)).getOrThrow()
        null
    }

    @JvmStatic
    fun joinRoom(
        client: de.connect2x.trixnity.client.MatrixClient,
        roomIdOrAlias: String,
        timeout: Duration?,
        onSuccess: Any,
        onFailure: Any,
    ): Closeable = submitBridgeTask(
        scope = BridgeAsync.clientScope(client),
        onSuccess = onSuccess,
        onFailure = onFailure,
        timeout = timeout,
    ) {
        when (val target = parseJoinRoomTarget(roomIdOrAlias)) {
            is RoomAliasId -> client.api.room.joinRoom(target).getOrThrow().full
            is RoomId -> client.api.room.joinRoom(target).getOrThrow().full
            else -> error("unsupported join room target: $target")
        }
    }

    @JvmStatic
    fun forgetRoom(
        client: de.connect2x.trixnity.client.MatrixClient,
        roomId: String,
        force: Boolean,
        onSuccess: Any,
        onFailure: Any,
    ): Closeable = submitBridgeTask(
        scope = BridgeAsync.clientScope(client),
        onSuccess = onSuccess,
        onFailure = onFailure,
    ) {
        client.room.forgetRoom(RoomId(roomId), force)
        null
    }

    @JvmStatic
    fun sendMessage(
        client: de.connect2x.trixnity.client.MatrixClient,
        roomId: String,
        message: KeywordMap,
        timeout: Duration?,
        onSuccess: Any,
        onFailure: Any,
    ): Closeable = submitBridgeTask(
        scope = BridgeAsync.clientScope(client),
        onSuccess = onSuccess,
        onFailure = onFailure,
        timeout = timeout,
    ) {
        val parsedMessage = requireMessageSpec(
            mapOf(BridgeSchema.SendMessageRequest.message to message),
            BridgeSchema.SendMessageRequest.message,
        )

        client.room.sendMessage(RoomId(roomId)) {
            applyMessageSpec(parsedMessage)
        }
    }

    @JvmStatic
    fun sendReaction(
        client: de.connect2x.trixnity.client.MatrixClient,
        roomId: String,
        eventId: String,
        key: String,
        timeout: Duration?,
        onSuccess: Any,
        onFailure: Any,
    ): Closeable = submitBridgeTask(
        scope = BridgeAsync.clientScope(client),
        onSuccess = onSuccess,
        onFailure = onFailure,
        timeout = timeout,
    ) {
        client.room.sendMessage(RoomId(roomId)) {
            react(EventId(eventId), key)
        }
    }

    @JvmStatic
    fun sendStateEvent(
        client: de.connect2x.trixnity.client.MatrixClient,
        roomId: String,
        stateEvent: KeywordMap,
        timeout: Duration?,
        onSuccess: Any,
        onFailure: Any,
    ): Closeable = submitBridgeTask(
        scope = BridgeAsync.clientScope(client),
        onSuccess = onSuccess,
        onFailure = onFailure,
        timeout = timeout,
    ) {
        val parsedStateEvent = requireStateEventSpec(
            mapOf(BridgeSchema.SendStateEventRequest.stateEvent to stateEvent),
            BridgeSchema.SendStateEventRequest.stateEvent,
        )

        client.api.room.sendStateEvent(
            RoomId(roomId),
            parsedStateEvent.toEventContent(),
            parsedStateEvent.stateKey,
        ).getOrThrow().full
    }

    @JvmStatic
    fun redactEvent(
        client: de.connect2x.trixnity.client.MatrixClient,
        roomId: String,
        eventId: String,
        reason: String?,
        timeout: Duration?,
        onSuccess: Any,
        onFailure: Any,
    ): Closeable = submitBridgeTask(
        scope = BridgeAsync.clientScope(client),
        onSuccess = onSuccess,
        onFailure = onFailure,
        timeout = timeout,
    ) {
        client.api.room.redactEvent(
            roomId = RoomId(roomId),
            eventId = EventId(eventId),
            reason = reason,
        ).getOrThrow().full
    }

    @JvmStatic
    fun cancelSendMessage(
        client: de.connect2x.trixnity.client.MatrixClient,
        roomId: String,
        transactionId: String,
        onSuccess: Any,
        onFailure: Any,
    ): Closeable = submitBridgeTask(
        scope = BridgeAsync.clientScope(client),
        onSuccess = onSuccess,
        onFailure = onFailure,
    ) {
        client.room.cancelSendMessage(RoomId(roomId), transactionId)
        null
    }

    @JvmStatic
    fun retrySendMessage(
        client: de.connect2x.trixnity.client.MatrixClient,
        roomId: String,
        transactionId: String,
        onSuccess: Any,
        onFailure: Any,
    ): Closeable = submitBridgeTask(
        scope = BridgeAsync.clientScope(client),
        onSuccess = onSuccess,
        onFailure = onFailure,
    ) {
        client.room.retrySendMessage(RoomId(roomId), transactionId)
        null
    }

    @JvmStatic
    fun currentUsersTyping(
        client: de.connect2x.trixnity.client.MatrixClient,
    ): Map<String, Map<Keyword, Any?>> =
        client.room.usersTyping.value.entries.associate { (roomId, content) ->
            roomId.full to normalizeTypingEventContent(content)
        }

    @JvmStatic
    fun usersTypingFlow(client: de.connect2x.trixnity.client.MatrixClient):
        Flow<Map<String, Map<Keyword, Any?>>> =
        client.room.usersTyping.map { usersTyping ->
            usersTyping.entries.associate { (roomId, content) ->
                roomId.full to normalizeTypingEventContent(content)
            }
        }

    @JvmStatic
    fun setTyping(
        client: de.connect2x.trixnity.client.MatrixClient,
        roomId: String,
        typing: Boolean,
        timeout: Duration?,
        onSuccess: Any,
        onFailure: Any,
    ): Closeable = submitBridgeTask(
        scope = BridgeAsync.clientScope(client),
        onSuccess = onSuccess,
        onFailure = onFailure,
    ) {
        client.api.room.setTyping(
            RoomId(roomId),
            client.userId,
            typing,
            timeout?.toMillis(),
        ).getOrThrow()
        null
    }

    @JvmStatic
    fun roomById(
        client: de.connect2x.trixnity.client.MatrixClient,
        roomId: String,
    ): Flow<Map<Keyword, Any?>?> =
        client.room.getById(RoomId(roomId)).map(::normalizeRoom)

    @JvmStatic
    fun rooms(client: de.connect2x.trixnity.client.MatrixClient):
        Flow<Map<String, Flow<Map<Keyword, Any?>?>>> =
        client.room.getAll().map { rooms ->
            rooms.entries.associate { (roomId, roomFlow) ->
                roomId.full to roomFlow.map(::normalizeRoom)
            }
        }

    @JvmStatic
    fun roomsFlat(client: de.connect2x.trixnity.client.MatrixClient):
        Flow<List<Map<Keyword, Any?>>> =
        client.room.getAll().flattenValues().map { rooms ->
            rooms
                .sortedBy { it.roomId.full }
                .mapNotNull(::normalizeRoom)
        }

    @JvmStatic
    fun accountData(
        client: de.connect2x.trixnity.client.MatrixClient,
        roomId: String,
        eventContentClass: Class<*>,
        key: String,
    ): Flow<Map<Keyword, Any?>?> =
        client.room.getAccountData(
            RoomId(roomId),
            javaClassToKClass<RoomAccountDataEventContent>(eventContentClass),
            key,
        ).map(::normalizeContent)

    @JvmStatic
    fun state(
        client: de.connect2x.trixnity.client.MatrixClient,
        roomId: String,
        eventContentClass: Class<*>,
        stateKey: String,
    ): Flow<Map<Keyword, Any?>?> =
        client.room.getState(
            RoomId(roomId),
            javaClassToKClass<StateEventContent>(eventContentClass),
            stateKey,
        ).map(::normalizeStateEvent)

    @JvmStatic
    fun allState(
        client: de.connect2x.trixnity.client.MatrixClient,
        roomId: String,
        eventContentClass: Class<*>,
    ): Flow<Map<String, Flow<Map<Keyword, Any?>?>>> =
        client.room.getAllState(
            RoomId(roomId),
            javaClassToKClass<StateEventContent>(eventContentClass),
        ).map { stateMap ->
            stateMap.entries.associate { (stateKey, eventFlow) ->
                stateKey to eventFlow.map(::normalizeStateEvent)
            }
        }

    @JvmStatic
    fun outbox(
        client: de.connect2x.trixnity.client.MatrixClient,
    ): Flow<List<Flow<Map<Keyword, Any?>?>>> =
        client.room.getOutbox().map { flows ->
            flows.map { it.map(::normalizeRoomOutboxMessage) }
        }

    @JvmStatic
    fun outboxFlat(
        client: de.connect2x.trixnity.client.MatrixClient,
    ): Flow<List<Map<Keyword, Any?>>> =
        client.room.getOutbox().flatten().map { messages ->
            messages.map(::normalizeRoomOutboxMessage).filterNotNull()
        }

    @JvmStatic
    fun outboxByRoom(
        client: de.connect2x.trixnity.client.MatrixClient,
        roomId: String,
    ): Flow<List<Flow<Map<Keyword, Any?>?>>> =
        client.room.getOutbox(RoomId(roomId)).map { flows ->
            flows.map { it.map(::normalizeRoomOutboxMessage) }
        }

    @JvmStatic
    fun outboxByRoomFlat(
        client: de.connect2x.trixnity.client.MatrixClient,
        roomId: String,
    ): Flow<List<Map<Keyword, Any?>>> =
        client.room.getOutbox(RoomId(roomId)).flatten().map { messages ->
            messages.map(::normalizeRoomOutboxMessage).filterNotNull()
        }

    @JvmStatic
    fun outboxMessage(
        client: de.connect2x.trixnity.client.MatrixClient,
        roomId: String,
        transactionId: String,
    ): Flow<Map<Keyword, Any?>?> =
        client.room.getOutbox(RoomId(roomId), transactionId).map(::normalizeRoomOutboxMessage)

    @JvmStatic
    fun fillTimelineGaps(
        client: de.connect2x.trixnity.client.MatrixClient,
        roomId: String,
        eventId: String,
        limit: Long,
        onSuccess: Any,
        onFailure: Any,
    ): Closeable = submitBridgeTask(
        scope = BridgeAsync.clientScope(client),
        onSuccess = onSuccess,
        onFailure = onFailure,
    ) {
        client.room.fillTimelineGaps(RoomId(roomId), EventId(eventId), limit)
        null
    }
}
