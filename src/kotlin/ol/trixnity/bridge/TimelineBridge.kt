package ol.trixnity.bridge

import clojure.lang.Keyword
import de.connect2x.trixnity.client.room
import de.connect2x.trixnity.client.room.GetTimelineEventConfig
import de.connect2x.trixnity.client.room.GetTimelineEventsConfig
import de.connect2x.trixnity.client.room.getTimelineEventsAround
import de.connect2x.trixnity.client.room.toFlowList
import de.connect2x.trixnity.client.store.TimelineEvent
import de.connect2x.trixnity.clientserverapi.model.room.GetEvents.Direction
import de.connect2x.trixnity.clientserverapi.model.room.GetEvents.Direction.BACKWARDS
import de.connect2x.trixnity.clientserverapi.model.room.GetEvents.Direction.FORWARDS
import de.connect2x.trixnity.clientserverapi.model.sync.Sync
import de.connect2x.trixnity.core.model.EventId
import de.connect2x.trixnity.core.model.RoomId
import de.connect2x.trixnity.core.model.events.m.RelationType
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapNotNull
import kotlin.time.Duration.Companion.milliseconds

object TimelineBridge {
    private fun timelineEventConfig(
        decryptionTimeoutMs: Long?,
        fetchTimeoutMs: Long?,
        fetchSize: Long?,
        allowReplaceContent: Boolean?,
    ): GetTimelineEventConfig.() -> Unit = {
        decryptionTimeoutMs?.let { decryptionTimeout = it.milliseconds }
        fetchTimeoutMs?.let { fetchTimeout = it.milliseconds }
        fetchSize?.let { this.fetchSize = it }
        allowReplaceContent?.let { this.allowReplaceContent = it }
    }

    private fun timelineEventsConfig(
        decryptionTimeoutMs: Long?,
        fetchTimeoutMs: Long?,
        fetchSize: Long?,
        allowReplaceContent: Boolean?,
        minSize: Long?,
        maxSize: Long?,
    ): GetTimelineEventsConfig.() -> Unit = {
        decryptionTimeoutMs?.let { decryptionTimeout = it.milliseconds }
        fetchTimeoutMs?.let { fetchTimeout = it.milliseconds }
        fetchSize?.let { this.fetchSize = it }
        allowReplaceContent?.let { this.allowReplaceContent = it }
        minSize?.let { this.minSize = it }
        maxSize?.let { this.maxSize = it }
    }

    private fun directionOf(direction: String?): Direction =
        when (direction?.lowercase()) {
            null, "backwards" -> BACKWARDS
            "forwards" -> FORWARDS
            else -> throw IllegalArgumentException("unsupported direction: $direction")
        }

    @JvmStatic
    fun timelineEventsFromNowOn(
        client: de.connect2x.trixnity.client.MatrixClient,
        decryptionTimeoutMs: Long?,
        syncResponseBufferSize: Int?,
    ): Flow<Map<Keyword, Any?>> =
        when {
            decryptionTimeoutMs != null && syncResponseBufferSize != null ->
                client.room.getTimelineEventsFromNowOn(
                    decryptionTimeout = decryptionTimeoutMs.milliseconds,
                    syncResponseBufferSize = syncResponseBufferSize,
                )

            decryptionTimeoutMs != null ->
                client.room.getTimelineEventsFromNowOn(
                    decryptionTimeout = decryptionTimeoutMs.milliseconds,
                )

            syncResponseBufferSize != null ->
                client.room.getTimelineEventsFromNowOn(
                    syncResponseBufferSize = syncResponseBufferSize,
                )

            else -> client.room.getTimelineEventsFromNowOn()
        }.mapNotNull(::normalizeTimelineEvent)

    @JvmStatic
    fun timelineEvents(
        client: de.connect2x.trixnity.client.MatrixClient,
        response: Sync.Response,
        decryptionTimeoutMs: Long?,
    ): Flow<Map<Keyword, Any?>> =
        if (decryptionTimeoutMs != null) {
            client.room.getTimelineEvents(
                response = response,
                decryptionTimeout = decryptionTimeoutMs.milliseconds,
            )
        } else {
            client.room.getTimelineEvents(response = response)
        }.mapNotNull(::normalizeTimelineEvent)

    @JvmStatic
    fun timelineEvent(
        client: de.connect2x.trixnity.client.MatrixClient,
        roomId: String,
        eventId: String,
        decryptionTimeoutMs: Long?,
        fetchTimeoutMs: Long?,
        fetchSize: Long?,
        allowReplaceContent: Boolean?,
    ): Flow<Map<Keyword, Any?>?> =
        client.room.getTimelineEvent(
            RoomId(roomId),
            EventId(eventId),
            timelineEventConfig(
                decryptionTimeoutMs = decryptionTimeoutMs,
                fetchTimeoutMs = fetchTimeoutMs,
                fetchSize = fetchSize,
                allowReplaceContent = allowReplaceContent,
            ),
        ).map(::normalizeTimelineEvent)

    @JvmStatic
    fun previousTimelineEvent(
        client: de.connect2x.trixnity.client.MatrixClient,
        event: TimelineEvent,
        decryptionTimeoutMs: Long?,
        fetchTimeoutMs: Long?,
        fetchSize: Long?,
        allowReplaceContent: Boolean?,
    ): Flow<Map<Keyword, Any?>?>? =
        client.room.getPreviousTimelineEvent(
            event,
            timelineEventConfig(
                decryptionTimeoutMs = decryptionTimeoutMs,
                fetchTimeoutMs = fetchTimeoutMs,
                fetchSize = fetchSize,
                allowReplaceContent = allowReplaceContent,
            ),
        )?.map(::normalizeTimelineEvent)

    @JvmStatic
    fun nextTimelineEvent(
        client: de.connect2x.trixnity.client.MatrixClient,
        event: TimelineEvent,
        decryptionTimeoutMs: Long?,
        fetchTimeoutMs: Long?,
        fetchSize: Long?,
        allowReplaceContent: Boolean?,
    ): Flow<Map<Keyword, Any?>?>? =
        client.room.getNextTimelineEvent(
            event,
            timelineEventConfig(
                decryptionTimeoutMs = decryptionTimeoutMs,
                fetchTimeoutMs = fetchTimeoutMs,
                fetchSize = fetchSize,
                allowReplaceContent = allowReplaceContent,
            ),
        )?.map(::normalizeTimelineEvent)

    @JvmStatic
    fun lastTimelineEvent(
        client: de.connect2x.trixnity.client.MatrixClient,
        roomId: String,
        decryptionTimeoutMs: Long?,
        fetchTimeoutMs: Long?,
        fetchSize: Long?,
        allowReplaceContent: Boolean?,
    ): Flow<Flow<Map<Keyword, Any?>>?> =
        client.room.getLastTimelineEvent(
            RoomId(roomId),
            timelineEventConfig(
                decryptionTimeoutMs = decryptionTimeoutMs,
                fetchTimeoutMs = fetchTimeoutMs,
                fetchSize = fetchSize,
                allowReplaceContent = allowReplaceContent,
            ),
        ).map { inner ->
            inner?.mapNotNull(::normalizeTimelineEvent)
        }

    @JvmStatic
    fun timelineEventChain(
        client: de.connect2x.trixnity.client.MatrixClient,
        roomId: String,
        startFrom: String,
        direction: String?,
        decryptionTimeoutMs: Long?,
        fetchTimeoutMs: Long?,
        fetchSize: Long?,
        allowReplaceContent: Boolean?,
        minSize: Long?,
        maxSize: Long?,
    ): Flow<Flow<Map<Keyword, Any?>>> =
        client.room.getTimelineEvents(
            roomId = RoomId(roomId),
            startFrom = EventId(startFrom),
            direction = directionOf(direction),
            config = timelineEventsConfig(
                decryptionTimeoutMs = decryptionTimeoutMs,
                fetchTimeoutMs = fetchTimeoutMs,
                fetchSize = fetchSize,
                allowReplaceContent = allowReplaceContent,
                minSize = minSize,
                maxSize = maxSize,
            ),
        ).map { it.mapNotNull(::normalizeTimelineEvent) }

    @JvmStatic
    fun lastTimelineEvents(
        client: de.connect2x.trixnity.client.MatrixClient,
        roomId: String,
        decryptionTimeoutMs: Long?,
        fetchTimeoutMs: Long?,
        fetchSize: Long?,
        allowReplaceContent: Boolean?,
        minSize: Long?,
        maxSize: Long?,
    ): Flow<Flow<Flow<Map<Keyword, Any?>>>?> =
        client.room.getLastTimelineEvents(
            roomId = RoomId(roomId),
            config = timelineEventsConfig(
                decryptionTimeoutMs = decryptionTimeoutMs,
                fetchTimeoutMs = fetchTimeoutMs,
                fetchSize = fetchSize,
                allowReplaceContent = allowReplaceContent,
                minSize = minSize,
                maxSize = maxSize,
            ),
        ).map { chain ->
            chain?.map { inner -> inner.mapNotNull(::normalizeTimelineEvent) }
        }

    @JvmStatic
    fun timelineEventsList(
        client: de.connect2x.trixnity.client.MatrixClient,
        roomId: String,
        startFrom: String,
        direction: String?,
        maxSize: Int,
        minSize: Int,
        decryptionTimeoutMs: Long?,
        fetchTimeoutMs: Long?,
        fetchSize: Long?,
        allowReplaceContent: Boolean?,
    ): Flow<List<Flow<Map<Keyword, Any?>>>> =
        client.room.getTimelineEvents(
            roomId = RoomId(roomId),
            startFrom = EventId(startFrom),
            direction = directionOf(direction),
            config = timelineEventsConfig(
                decryptionTimeoutMs = decryptionTimeoutMs,
                fetchTimeoutMs = fetchTimeoutMs,
                fetchSize = fetchSize,
                allowReplaceContent = allowReplaceContent,
                minSize = null,
                maxSize = null,
            ),
        ).toFlowList(
            maxSize = MutableStateFlow(maxSize),
            minSize = MutableStateFlow(minSize),
        ).map { flows ->
            flows.map { it.mapNotNull(::normalizeTimelineEvent) }
        }

    @JvmStatic
    fun lastTimelineEventsList(
        client: de.connect2x.trixnity.client.MatrixClient,
        roomId: String,
        maxSize: Int,
        minSize: Int,
        decryptionTimeoutMs: Long?,
        fetchTimeoutMs: Long?,
        fetchSize: Long?,
        allowReplaceContent: Boolean?,
    ): Flow<List<Flow<Map<Keyword, Any?>>>> =
        client.room.getLastTimelineEvents(
            roomId = RoomId(roomId),
            config = timelineEventsConfig(
                decryptionTimeoutMs = decryptionTimeoutMs,
                fetchTimeoutMs = fetchTimeoutMs,
                fetchSize = fetchSize,
                allowReplaceContent = allowReplaceContent,
                minSize = null,
                maxSize = null,
            ),
        ).toFlowList(
            maxSize = MutableStateFlow(maxSize),
            minSize = MutableStateFlow(minSize),
        ).map { flows ->
            flows.map { it.mapNotNull(::normalizeTimelineEvent) }
        }

    @JvmStatic
    fun timelineEventsAround(
        client: de.connect2x.trixnity.client.MatrixClient,
        roomId: String,
        startFrom: String,
        maxSizeBefore: Int,
        maxSizeAfter: Int,
        decryptionTimeoutMs: Long?,
        fetchTimeoutMs: Long?,
        fetchSize: Long?,
        allowReplaceContent: Boolean?,
    ): Flow<List<Flow<Map<Keyword, Any?>>>> =
        client.room.getTimelineEventsAround(
            roomId = RoomId(roomId),
            startFrom = EventId(startFrom),
            maxSizeBefore = MutableStateFlow(maxSizeBefore),
            maxSizeAfter = MutableStateFlow(maxSizeAfter),
            configStart = timelineEventConfig(
                decryptionTimeoutMs = decryptionTimeoutMs,
                fetchTimeoutMs = fetchTimeoutMs,
                fetchSize = fetchSize,
                allowReplaceContent = allowReplaceContent,
            ),
            configBefore = timelineEventsConfig(
                decryptionTimeoutMs = decryptionTimeoutMs,
                fetchTimeoutMs = fetchTimeoutMs,
                fetchSize = fetchSize,
                allowReplaceContent = allowReplaceContent,
                minSize = null,
                maxSize = null,
            ),
            configAfter = timelineEventsConfig(
                decryptionTimeoutMs = decryptionTimeoutMs,
                fetchTimeoutMs = fetchTimeoutMs,
                fetchSize = fetchSize,
                allowReplaceContent = allowReplaceContent,
                minSize = null,
                maxSize = null,
            ),
        ).map { flows ->
            flows.map { it.mapNotNull(::normalizeTimelineEvent) }
        }

    @JvmStatic
    fun timelineEventRelations(
        client: de.connect2x.trixnity.client.MatrixClient,
        roomId: String,
        eventId: String,
        relationType: String,
    ): Flow<Map<String, Flow<Map<Keyword, Any?>?>>?> =
        client.room.getTimelineEventRelations(
            roomId = RoomId(roomId),
            eventId = EventId(eventId),
            relationType = RelationType.of(relationType),
        ).map { relationMap ->
            relationMap?.entries?.associate { (relatedEventId, relationFlow) ->
                relatedEventId.full to relationFlow.map(::normalizeTimelineEventRelation)
            }
        }
}
