package ol.trixnity.bridge

import io.ktor.http.URLBuilder
import io.ktor.http.takeFrom
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import net.folivo.trixnity.client.MatrixClient
import net.folivo.trixnity.client.fromStore
import net.folivo.trixnity.client.login
import net.folivo.trixnity.client.room
import net.folivo.trixnity.client.room.message.react
import net.folivo.trixnity.client.room.message.reply
import net.folivo.trixnity.client.room.message.text
import net.folivo.trixnity.client.store.eventId
import net.folivo.trixnity.client.store.roomId
import net.folivo.trixnity.client.store.sender
import net.folivo.trixnity.client.store.repository.exposed.createExposedRepositoriesModule
import net.folivo.trixnity.client.media.okio.OkioMediaStore
import net.folivo.trixnity.clientserverapi.client.SyncState
import net.folivo.trixnity.clientserverapi.model.authentication.IdentifierType
import net.folivo.trixnity.core.model.EventId
import net.folivo.trixnity.core.model.RoomId
import net.folivo.trixnity.core.model.UserId
import net.folivo.trixnity.core.model.events.InitialStateEvent
import net.folivo.trixnity.core.model.events.m.ReactionEventContent
import net.folivo.trixnity.core.model.events.m.RelatesTo
import net.folivo.trixnity.core.model.events.m.room.EncryptionEventContent
import net.folivo.trixnity.core.model.events.m.room.RoomMessageEventContent
import okio.Path.Companion.toPath
import org.jetbrains.exposed.sql.Database
import java.nio.file.Files
import java.nio.file.Path
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import kotlin.time.Duration.Companion.seconds

internal object BridgeRuntime {
    private val timelineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val timelineJobs = ConcurrentHashMap<String, Job>()

    fun loginBlocking(request: LoginRequest): MatrixClient = runBlocking {
        val payload = normalizePayload(request.payload)
        val homeserverUrl = requireString(payload, "homeserver-url")
        val username = requireString(payload, "username")
        val password = requireString(payload, "password")
        val storePath = requireString(payload, "store-path")
        val mediaPath = requireString(payload, "media-path")
        val repositoriesModule = createRepositoriesModule(storePath)
        val mediaStore = createMediaStore(mediaPath)
        MatrixClient.login(
            baseUrl = URLBuilder().takeFrom(homeserverUrl).build(),
            identifier = IdentifierType.User(username),
            password = password,
            repositoriesModule = repositoriesModule,
            mediaStore = mediaStore,
        ).getOrThrow()
    }

    fun fromStoreBlocking(request: FromStoreRequest): MatrixClient? = runBlocking {
        val payload = normalizePayload(request.payload)
        val storePath = requireString(payload, "store-path")
        val mediaPath = requireString(payload, "media-path")
        val repositoriesModule = createRepositoriesModule(storePath)
        val mediaStore = createMediaStore(mediaPath)
        MatrixClient.fromStore(
            repositoriesModule = repositoriesModule,
            mediaStore = mediaStore,
        ).getOrThrow()
    }

    fun startSyncBlocking(request: StartSyncRequest) {
        val payload = normalizePayload(request.payload)
        val client = requireClient(payload)
        runBlocking {
            client.startSync()
            client.syncState.first { it == SyncState.RUNNING }
        }
    }

    fun createRoomBlocking(request: CreateRoomRequest): String {
        val payload = normalizePayload(request.payload)
        val client = requireClient(payload)
        val roomName = requireString(payload, "room-name")
        return runBlocking {
            client.api.room.createRoom(
                name = roomName,
                initialState = listOf(InitialStateEvent(content = EncryptionEventContent(), stateKey = "")),
            ).getOrThrow().full
        }
    }

    fun inviteUserBlocking(request: InviteUserRequest) {
        val payload = normalizePayload(request.payload)
        val client = requireClient(payload)
        val roomId = RoomId(requireString(payload, "room-id"))
        val userId = UserId(requireString(payload, "user-id"))
        runBlocking {
            client.api.room.inviteUser(roomId, userId).onFailure { ex ->
                if (!isAlreadyInRoomInviteFailure(ex)) throw ex
            }
        }
    }

    fun sendTextReplyBlocking(request: SendTextReplyRequest) {
        val payload = normalizePayload(request.payload)
        val client = requireClient(payload)
        val roomId = RoomId(requireString(payload, "room-id"))
        val eventId = EventId(requireString(payload, "event-id"))
        val body = requireString(payload, "body")
        runBlocking {
            client.room.sendMessage(roomId) {
                text(body)
                reply(eventId, null)
            }
        }
    }

    fun sendReactionBlocking(request: SendReactionRequest) {
        val payload = normalizePayload(request.payload)
        val client = requireClient(payload)
        val roomId = RoomId(requireString(payload, "room-id"))
        val eventId = EventId(requireString(payload, "event-id"))
        val key = requireString(payload, "key")
        runBlocking {
            client.room.sendMessage(roomId) {
                react(eventId, key)
            }
        }
    }

    fun startTimelinePump(request: StartTimelinePumpRequest): TimelinePumpHandle {
        val payload = normalizePayload(request.payload)
        val client = requireClient(payload)
        val callback = payload["on-event"]
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

    fun stopTimelinePump(request: StopTimelinePumpRequest) {
        val payload = normalizePayload(request.payload)
        val handle = payload["timeline-pump"] as? TimelinePumpHandle ?: return
        val job = timelineJobs.remove(handle.id) ?: return
        runBlocking {
            job.cancelAndJoin()
        }
    }

    private suspend fun createRepositoriesModule(storePath: String): org.koin.core.module.Module {
        val dbPath = Path.of(storePath).toAbsolutePath()
        dbPath.parent?.let { Files.createDirectories(it) }
        val database = Database.connect("jdbc:h2:file:${dbPath};DB_CLOSE_DELAY=-1;")
        return createExposedRepositoriesModule(database)
    }

    private fun createMediaStore(mediaPath: String): OkioMediaStore {
        val path = Path.of(mediaPath).toAbsolutePath()
        Files.createDirectories(path)
        return OkioMediaStore(path.toString().toPath())
    }

    private fun requireClient(payload: Map<String, Any?>): MatrixClient =
        payload["client"] as? MatrixClient
            ?: throw IllegalArgumentException("request payload is missing MatrixClient under :client")

    private fun requireString(payload: Map<String, Any?>, key: String): String =
        payload[key]?.toString()?.takeIf { it.isNotBlank() }
            ?: throw IllegalArgumentException("request payload is missing required key :$key")

    private fun normalizePayload(payload: Map<String, Any?>): Map<String, Any?> {
        val normalized = LinkedHashMap<String, Any?>()
        for ((rawKey, value) in (payload as Map<*, *>)) {
            val key = normalizeKey(rawKey) ?: continue
            normalized[key] = value
        }
        return normalized
    }

    private fun normalizeKey(key: Any?): String? {
        val asString = key?.toString() ?: return null
        val withoutColon = asString.removePrefix(":")
        return withoutColon.substringAfter('/')
    }

    private fun isAlreadyInRoomInviteFailure(error: Throwable): Boolean =
        error.message?.contains("already in the room", ignoreCase = true) == true

    @Suppress("UNCHECKED_CAST")
    private fun invokeOnEvent(callback: Any?, event: Map<String, Any?>) {
        if (callback == null) return
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
