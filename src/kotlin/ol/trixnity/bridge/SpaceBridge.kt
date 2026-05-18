package ol.trixnity.bridge

import clojure.lang.Keyword
import de.connect2x.trixnity.client.flattenNotNull
import de.connect2x.trixnity.client.flattenValues
import de.connect2x.trixnity.client.room
import de.connect2x.trixnity.client.store.Room
import de.connect2x.trixnity.clientserverapi.model.room.CreateRoom
import de.connect2x.trixnity.clientserverapi.model.room.DirectoryVisibility
import de.connect2x.trixnity.core.model.RoomId
import de.connect2x.trixnity.core.model.UserId
import de.connect2x.trixnity.core.model.events.m.room.CreateEventContent
import de.connect2x.trixnity.core.model.events.m.room.PowerLevelsEventContent
import de.connect2x.trixnity.core.model.events.m.space.ChildEventContent
import de.connect2x.trixnity.core.model.events.m.space.ParentEventContent
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.io.Closeable
import java.time.Duration

private data class CreateSpaceSpec(
    val roomName: String? = null,
    val topic: String? = null,
    val invite: Set<UserId>? = null,
    val preset: CreateRoom.Request.Preset? = null,
    val isDirect: Boolean? = null,
    val visibility: DirectoryVisibility? = null,
    val powerLevels: PowerLevelsEventContent? = null,
)

private data class HierarchySpec(
    val from: String? = null,
    val limit: Long? = null,
    val maxDepth: Long? = null,
    val suggestedOnly: Boolean = false,
)

private fun isSpace(room: Room?): Boolean =
    room?.createEventContent?.type == CreateEventContent.RoomType.Space

private fun parseCreateSpaceSpec(request: KeywordMap): CreateSpaceSpec {
    val invite = (request[BridgeSchema.invite] as? Iterable<*>)?.map {
        UserId(it?.toString() ?: error("invite list contains null entry"))
    }?.toSet()?.takeIf { it.isNotEmpty() }

    val preset = optionalKeywordString(request, BridgeSchema.preset)?.removePrefix(":")?.let {
        when (it) {
            "private-chat" -> CreateRoom.Request.Preset.PRIVATE
            "public-chat" -> CreateRoom.Request.Preset.PUBLIC
            "trusted-private-chat" -> CreateRoom.Request.Preset.TRUSTED_PRIVATE
            else -> error("unsupported create-space preset: $it")
        }
    }

    val visibility =
        optionalKeywordString(request, BridgeSchema.visibility)?.removePrefix(":")?.let {
            when (it) {
                "private" -> DirectoryVisibility.PRIVATE
                "public" -> DirectoryVisibility.PUBLIC
                else -> error("unsupported create-space visibility: $it")
            }
        }

    val powerLevels = if (request.containsKey(BridgeSchema.powerLevels)) {
        requirePowerLevelsContent(request, BridgeSchema.powerLevels)
    } else null

    return CreateSpaceSpec(
        roomName = optionalKeywordString(request, BridgeSchema.roomName),
        topic = optionalKeywordString(request, BridgeSchema.topic),
        invite = invite,
        preset = preset,
        isDirect = request[BridgeSchema.isDirect] as? Boolean,
        visibility = visibility,
        powerLevels = powerLevels,
    )
}

private fun parseHierarchySpec(request: KeywordMap): HierarchySpec =
    HierarchySpec(
        from = optionalKeywordString(request, BridgeSchema.from),
        limit = optionalKeywordLong(request, BridgeSchema.limit),
        maxDepth = optionalKeywordLong(request, BridgeSchema.maxDepth),
        suggestedOnly = optionalKeywordBoolean(request, BridgeSchema.suggestedOnly) ?: false,
    )

private suspend fun createSpaceRoom(
    client: de.connect2x.trixnity.client.MatrixClient,
    spec: CreateSpaceSpec,
): String {
    val creationContent = CreateEventContent(type = CreateEventContent.RoomType.Space)
    val roomId = if (spec.visibility == null) {
        client.api.room.createRoom(
            name = spec.roomName,
            topic = spec.topic,
            invite = spec.invite,
            creationContent = creationContent,
            preset = spec.preset,
            isDirect = spec.isDirect,
            powerLevelContentOverride = spec.powerLevels,
        )
    } else {
        client.api.room.createRoom(
            visibility = spec.visibility,
            name = spec.roomName,
            topic = spec.topic,
            invite = spec.invite,
            creationContent = creationContent,
            preset = spec.preset,
            isDirect = spec.isDirect,
            powerLevelContentOverride = spec.powerLevels,
        )
    }.getOrThrow()
    return roomId.full
}

object SpaceBridge {
    @JvmStatic
    fun createSpace(
        client: de.connect2x.trixnity.client.MatrixClient,
        request: KeywordMap,
        timeout: Duration?,
        onSuccess: Any,
        onFailure: Any,
    ): Closeable = submitBridgeTask(
        scope = BridgeAsync.clientScope(client),
        onSuccess = onSuccess,
        onFailure = onFailure,
        timeout = timeout,
    ) {
        createSpaceRoom(client, parseCreateSpaceSpec(request))
    }

    @JvmStatic
    fun hierarchy(
        client: de.connect2x.trixnity.client.MatrixClient,
        spaceId: String,
        request: KeywordMap,
        timeout: Duration?,
        onSuccess: Any,
        onFailure: Any,
    ): Closeable = submitBridgeTask(
        scope = BridgeAsync.clientScope(client),
        onSuccess = onSuccess,
        onFailure = onFailure,
        timeout = timeout,
    ) {
        val spec = parseHierarchySpec(request)
        normalizeHierarchyResponse(
            client.api.room.getHierarchy(
                roomId = RoomId(spaceId),
                from = spec.from,
                limit = spec.limit,
                maxDepth = spec.maxDepth,
                suggestedOnly = spec.suggestedOnly,
            ).getOrThrow(),
        )
    }

    @JvmStatic
    fun spaces(
        client: de.connect2x.trixnity.client.MatrixClient,
    ): Flow<Map<String, Flow<Map<Keyword, Any?>?>>> =
        client.room.getAll().flattenNotNull().map { rooms ->
            rooms
                .filter { (_, room) -> isSpace(room) }
                .entries
                .associate { (roomId, _) ->
                    roomId.full to client.room.getById(roomId).map { room ->
                        if (isSpace(room)) normalizeRoomSnapshot(room) else null
                    }
                }
        }

    @JvmStatic
    fun spacesFlat(
        client: de.connect2x.trixnity.client.MatrixClient,
    ): Flow<List<Map<Keyword, Any?>>> =
        client.room.getAll().flattenValues().map { rooms ->
            rooms
                .filter(::isSpace)
                .sortedBy { it.roomId.full }
                .mapNotNull(::normalizeRoomSnapshot)
        }

    @JvmStatic
    fun child(
        client: de.connect2x.trixnity.client.MatrixClient,
        spaceId: String,
        childRoomId: String,
    ): Flow<Map<Keyword, Any?>?> =
        client.room.getState(
            RoomId(spaceId),
            ChildEventContent::class,
            childRoomId,
        ).map(::normalizeStateEvent)

    @JvmStatic
    fun children(
        client: de.connect2x.trixnity.client.MatrixClient,
        spaceId: String,
    ): Flow<Map<String, Flow<Map<Keyword, Any?>?>>> =
        client.room.getAllState(
            RoomId(spaceId),
            ChildEventContent::class,
        ).map { stateMap ->
            stateMap.entries.associate { (stateKey, eventFlow) ->
                stateKey to eventFlow.map(::normalizeStateEvent)
            }
        }

    @JvmStatic
    fun parents(
        client: de.connect2x.trixnity.client.MatrixClient,
        roomId: String,
    ): Flow<Map<String, Flow<Map<Keyword, Any?>?>>> =
        client.room.getAllState(
            RoomId(roomId),
            ParentEventContent::class,
        ).map { stateMap ->
            stateMap.entries.associate { (stateKey, eventFlow) ->
                stateKey to eventFlow.map(::normalizeStateEvent)
            }
        }

    @JvmStatic
    fun setChild(
        client: de.connect2x.trixnity.client.MatrixClient,
        spaceId: String,
        childRoomId: String,
        content: KeywordMap,
        timeout: Duration?,
        onSuccess: Any,
        onFailure: Any,
    ): Closeable = submitBridgeTask(
        scope = BridgeAsync.clientScope(client),
        onSuccess = onSuccess,
        onFailure = onFailure,
        timeout = timeout,
    ) {
        client.api.room.sendStateEvent(
            RoomId(spaceId),
            requireSpaceChildContent(mapOf(BridgeSchema.content to content), BridgeSchema.content),
            childRoomId,
        ).getOrThrow().full
    }

    @JvmStatic
    fun removeChild(
        client: de.connect2x.trixnity.client.MatrixClient,
        spaceId: String,
        childRoomId: String,
        timeout: Duration?,
        onSuccess: Any,
        onFailure: Any,
    ): Closeable = submitBridgeTask(
        scope = BridgeAsync.clientScope(client),
        onSuccess = onSuccess,
        onFailure = onFailure,
        timeout = timeout,
    ) {
        client.api.room.sendStateEvent(
            RoomId(spaceId),
            emptyStateEventContent("m.space.child"),
            childRoomId,
        ).getOrThrow().full
    }

    @JvmStatic
    fun setParent(
        client: de.connect2x.trixnity.client.MatrixClient,
        roomId: String,
        parentSpaceId: String,
        content: KeywordMap,
        timeout: Duration?,
        onSuccess: Any,
        onFailure: Any,
    ): Closeable = submitBridgeTask(
        scope = BridgeAsync.clientScope(client),
        onSuccess = onSuccess,
        onFailure = onFailure,
        timeout = timeout,
    ) {
        client.api.room.sendStateEvent(
            RoomId(roomId),
            requireSpaceParentContent(mapOf(BridgeSchema.content to content), BridgeSchema.content),
            parentSpaceId,
        ).getOrThrow().full
    }

    @JvmStatic
    fun removeParent(
        client: de.connect2x.trixnity.client.MatrixClient,
        roomId: String,
        parentSpaceId: String,
        timeout: Duration?,
        onSuccess: Any,
        onFailure: Any,
    ): Closeable = submitBridgeTask(
        scope = BridgeAsync.clientScope(client),
        onSuccess = onSuccess,
        onFailure = onFailure,
        timeout = timeout,
    ) {
        client.api.room.sendStateEvent(
            RoomId(roomId),
            emptyStateEventContent("m.space.parent"),
            parentSpaceId,
        ).getOrThrow().full
    }
}
