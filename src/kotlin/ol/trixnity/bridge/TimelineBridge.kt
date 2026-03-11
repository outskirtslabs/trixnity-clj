package ol.trixnity.bridge

import clojure.lang.Keyword
import de.connect2x.trixnity.client.room
import de.connect2x.trixnity.client.store.TimelineEvent
import de.connect2x.trixnity.client.store.eventId
import de.connect2x.trixnity.client.store.relatesTo
import de.connect2x.trixnity.client.store.roomId
import de.connect2x.trixnity.client.store.sender
import de.connect2x.trixnity.core.model.events.m.RelatesTo
import de.connect2x.trixnity.core.model.events.m.ReactionEventContent
import de.connect2x.trixnity.core.model.events.m.room.RoomMessageEventContent
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.milliseconds
import java.io.Closeable

private class TimelineSubscription(
    private val job: Job,
) : Closeable {
    override fun close() {
        job.cancel()
    }
}

object TimelineBridge {
    @JvmStatic
    fun subscribeTimeline(request: KeywordMap): Closeable {
        val client = requireKeywordClient(request, BridgeSchema.SubscribeTimelineRequest.client)
        val callback = requireKeywordValue(request, BridgeSchema.SubscribeTimelineRequest.onEvent)
        val timeout = optionalKeywordDuration(request, BridgeSchema.SubscribeTimelineRequest.decryptionTimeout)
        val scope = BridgeAsync.clientScope(client)
        val job = scope.launch {
            val events = if (timeout != null) {
                client.room.getTimelineEventsFromNowOn(decryptionTimeout = timeout.toMillis().milliseconds)
            } else {
                client.room.getTimelineEventsFromNowOn()
            }

            events.collect { timelineEvent ->
                normalizeEvent(timelineEvent)?.let { event ->
                    try {
                        invokeCallback(callback, event)
                    } catch (_: Throwable) {
                        // Callback failures are isolated to that delivery.
                    }
                }
            }
        }
        return TimelineSubscription(job)
    }

    private fun normalizeEvent(timelineEvent: TimelineEvent): Map<Keyword, Any?>? {
        val roomId = timelineEvent.roomId.full
        val sender = timelineEvent.sender.full
        val content = timelineEvent.content?.getOrNull()
        return when (content) {
            is RoomMessageEventContent.TextBased.Text -> mapOf(
                BridgeSchema.Event.type to "m.room.message",
                BridgeSchema.Event.roomId to roomId,
                BridgeSchema.Event.eventId to timelineEvent.eventId.full,
                BridgeSchema.Event.sender to sender,
                BridgeSchema.Event.body to content.body,
                BridgeSchema.Event.relatesTo to timelineEvent.relatesTo?.let(::normalizeRelation),
                BridgeSchema.Event.raw to timelineEvent,
            )

            is ReactionEventContent -> {
                val annotation = content.relatesTo as? RelatesTo.Annotation ?: return null
                val key = annotation.key ?: return null
                mapOf(
                    BridgeSchema.Event.type to "m.reaction",
                    BridgeSchema.Event.roomId to roomId,
                    BridgeSchema.Event.eventId to annotation.eventId.full,
                    BridgeSchema.Event.sender to sender,
                    BridgeSchema.Event.key to key,
                    BridgeSchema.Event.relatesTo to normalizeRelation(annotation),
                    BridgeSchema.Event.raw to timelineEvent,
                )
            }

            else -> null
        }
    }

    private fun normalizeRelation(relatesTo: RelatesTo): Map<Keyword, Any?> =
        buildMap {
            put(BridgeSchema.Relation.type, relationTypeName(relatesTo))
            put(BridgeSchema.Relation.eventId, relatesTo.eventId.full)
            when (relatesTo) {
                is RelatesTo.Annotation -> relatesTo.key?.let { put(BridgeSchema.Relation.key, it) }
                is RelatesTo.Thread -> {
                    relatesTo.replyTo?.eventId?.full?.let {
                        put(BridgeSchema.Relation.replyToEventId, it)
                    }
                    relatesTo.isFallingBack?.let {
                        put(BridgeSchema.Relation.isFallingBack, it)
                    }
                }

                is RelatesTo.Reply -> {
                    put(BridgeSchema.Relation.replyToEventId, relatesTo.replyTo.eventId.full)
                }

                else -> Unit
            }
        }

    private fun relationTypeName(relatesTo: RelatesTo): String =
        when (relatesTo) {
            is RelatesTo.Thread -> "m.thread"
            is RelatesTo.Reply -> "m.in_reply_to"
            is RelatesTo.Annotation -> "m.annotation"
            is RelatesTo.Reference -> "m.reference"
            is RelatesTo.Replace -> "m.replace"
            is RelatesTo.Unknown -> relatesTo.relationType.name
        }
}
