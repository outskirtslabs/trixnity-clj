package ol.trixnity.bridge

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import de.connect2x.trixnity.client.room
import de.connect2x.trixnity.client.store.eventId
import de.connect2x.trixnity.client.store.roomId
import de.connect2x.trixnity.client.store.sender
import de.connect2x.trixnity.core.model.events.m.ReactionEventContent
import de.connect2x.trixnity.core.model.events.m.RelatesTo
import de.connect2x.trixnity.core.model.events.m.room.RoomMessageEventContent
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import kotlin.time.Duration.Companion.seconds

data class TimelinePumpHandle(val id: String)

object EventBridge {
    private val timelineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val timelineJobs = ConcurrentHashMap<String, Job>()

    @JvmStatic
    fun startTimelinePump(request: KeywordMap): TimelinePumpHandle {
        val client = requireKeywordClient(request, BridgeSchema.StartTimelinePumpRequest.client)
        val callback = requireKeywordValue(request, BridgeSchema.StartTimelinePumpRequest.onEvent)
        val handle = TimelinePumpHandle(UUID.randomUUID().toString())
        val job = timelineScope.launch {
            client.room.getTimelineEventsFromNowOn(decryptionTimeout = 8.seconds).collect { timelineEvent ->
                try {
                    val roomId = timelineEvent.roomId.full
                    val sender = timelineEvent.sender.full
                    val content = timelineEvent.content?.getOrNull()
                    when (content) {
                        is RoomMessageEventContent.TextBased.Text -> {
                            invokeOnEvent(
                                callback,
                                mapOf(
                                    "type" to "m.room.message",
                                    "room" to roomId,
                                    "sender" to sender,
                                    "id" to timelineEvent.eventId.full,
                                    "body" to content.body,
                                ),
                            )
                        }

                        is ReactionEventContent -> {
                            val annotation = content.relatesTo as? RelatesTo.Annotation ?: return@collect
                            val key = annotation.key ?: return@collect
                            invokeOnEvent(
                                callback,
                                mapOf(
                                    "type" to "m.reaction",
                                    "room" to roomId,
                                    "sender" to sender,
                                    "id" to annotation.eventId.full,
                                    "key" to key,
                                ),
                            )
                        }

                        else -> Unit
                    }
                } catch (_: Throwable) {
                }
            }
        }
        timelineJobs[handle.id] = job
        return handle
    }

    @JvmStatic
    fun stopTimelinePump(request: KeywordMap) {
        requireKeywordClient(request, BridgeSchema.StopTimelinePumpRequest.client)
        val handle = requireKeywordValue(request, BridgeSchema.StopTimelinePumpRequest.timelinePump) as? TimelinePumpHandle
            ?: return
        val job = timelineJobs.remove(handle.id) ?: return
        runBlocking {
            job.cancelAndJoin()
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun invokeOnEvent(callback: Any, event: Map<String, Any?>) {
        when (callback) {
            is Function1<*, *> -> (callback as Function1<Any?, Any?>).invoke(event)
            else -> {
                val invokeMethod = callback.javaClass.methods.firstOrNull {
                    it.name == "invoke" && it.parameterCount == 1
                } ?: throw IllegalArgumentException(
                    "on-event callback ${callback.javaClass.name} does not expose invoke(arg)",
                )
                invokeMethod.invoke(callback, event)
            }
        }
    }
}
