package phase1

import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.first
import net.folivo.trixnity.client.*
import net.folivo.trixnity.client.media.MediaStore
import net.folivo.trixnity.client.room.message.react
import net.folivo.trixnity.client.room.message.reply
import net.folivo.trixnity.client.room.message.text
import net.folivo.trixnity.client.store.roomId
import net.folivo.trixnity.client.store.sender
import net.folivo.trixnity.clientserverapi.client.SyncState
import net.folivo.trixnity.clientserverapi.model.authentication.IdentifierType
import net.folivo.trixnity.core.model.events.InitialStateEvent
import net.folivo.trixnity.core.model.events.m.ReactionEventContent
import net.folivo.trixnity.core.model.events.m.room.EncryptionEventContent
import net.folivo.trixnity.core.model.events.m.room.RoomMessageEventContent
import java.nio.file.Files
import kotlin.io.path.absolutePathString
import kotlin.time.Duration.Companion.seconds

private val log = KotlinLogging.logger {}

suspend fun main(): Unit = coroutineScope {
    val config = BotConfigLoader.load()

    Files.createDirectories(config.mediaPath)
    val dbParent = config.databasePath.parent
    if (dbParent != null) Files.createDirectories(dbParent)
    val roomIdParent = config.roomIdFile.parent
    if (roomIdParent != null) Files.createDirectories(roomIdParent)

    val repositoriesModule = createRepositoriesModule(config.databasePath.absolutePathString())
    val mediaStore = createMediaStore(config.mediaPath.absolutePathString())

    val matrixClient = MatrixClient.fromStore(
        repositoriesModule = repositoriesModule,
        mediaStore = mediaStore,
    ).getOrThrow()
        ?: loginOrRegister(config, repositoriesModule, mediaStore)

    matrixClient.startSync()
    matrixClient.syncState.first { it == SyncState.RUNNING }

    val storedRoomId = RoomStateStore.load(config.roomIdFile)
    val roomId = storedRoomId ?: matrixClient.api.room.createRoom(
        name = config.roomName,
        initialState = listOf(InitialStateEvent(content = EncryptionEventContent(), "")),
    ).getOrThrow().also {
        RoomStateStore.save(config.roomIdFile, it)
        log.info { "created new room and persisted id to ${config.roomIdFile}" }
    }

    config.inviteUser?.let { userId ->
        matrixClient.api.room.inviteUser(roomId, userId).onFailure { error ->
            if (isAlreadyInRoomInviteFailure(error)) log.info { "invite skipped: $userId is already in the room" }
            else log.warn(error) { "failed to invite $userId" }
        }
    }

    if (storedRoomId != null) println("Reusing room from state file: $roomId")
    println("Room name: ${config.roomName}")
    println("Room id: $roomId")
    println("Bot user: ${matrixClient.userId}")

    matrixClient.room.getTimelineEventsFromNowOn(decryptionTimeout = 8.seconds).collect { timelineEvent ->
        if (timelineEvent.roomId != roomId) return@collect
        if (!BotLogic.shouldHandleSender(timelineEvent.sender, matrixClient.userId)) return@collect

        when (val content = timelineEvent.content?.getOrNull()) {
            is RoomMessageEventContent.TextBased.Text -> {
                val mirrored = BotLogic.mirroredBody(content.body)
                matrixClient.room.sendMessage(timelineEvent.roomId) {
                    text(mirrored)
                    reply(timelineEvent)
                }
            }

            is ReactionEventContent -> {
                val reaction = BotLogic.reactionToMirror(content) ?: return@collect
                matrixClient.room.sendMessage(timelineEvent.roomId) {
                    react(reaction.eventId, reaction.key)
                }
            }

            else -> {}
        }
    }
}

private suspend fun loginOrRegister(
    config: BotConfig,
    repositoriesModule: org.koin.core.module.Module,
    mediaStore: MediaStore,
): MatrixClient {
    config.registrationSharedSecret?.let { sharedSecret ->
        SynapseSharedSecretRegistration.register(
            baseUrl = config.homeserverUrl,
            username = config.username,
            password = config.password,
            sharedSecret = sharedSecret,
            admin = config.botAdmin,
        ).onSuccess {
            log.info { "synapse shared-secret registration completed for ${config.username}" }
        }.onFailure {
            log.warn(it) { "shared-secret registration failed, will try login anyway" }
        }

        return MatrixClient.login(
            baseUrl = config.homeserverUrl,
            identifier = IdentifierType.User(config.username),
            password = config.password,
            repositoriesModule = repositoriesModule,
            mediaStore = mediaStore,
        ).getOrThrow()
    }

    if (config.tryRegister) {
        MatrixClient.loginWith(
            baseUrl = config.homeserverUrl,
            repositoriesModule = repositoriesModule,
            mediaStore = mediaStore,
            getLoginInfo = { api -> api.register(config.username, config.password) },
        ).onSuccess {
            log.info { "registered and logged in as ${config.username}" }
        }.onFailure {
            log.warn(it) { "registration path failed, falling back to login" }
        }.getOrNull()?.let { return it }
    }

    return MatrixClient.login(
        baseUrl = config.homeserverUrl,
        identifier = IdentifierType.User(config.username),
        password = config.password,
        repositoriesModule = repositoriesModule,
        mediaStore = mediaStore,
    ).getOrThrow()
}
