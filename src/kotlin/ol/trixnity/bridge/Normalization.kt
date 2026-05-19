package ol.trixnity.bridge

import clojure.lang.Keyword
import de.connect2x.trixnity.client.notification.Notification
import de.connect2x.trixnity.client.notification.NotificationUpdate
import de.connect2x.trixnity.client.store.Room
import de.connect2x.trixnity.client.user
import de.connect2x.trixnity.client.store.RoomOutboxMessage
import de.connect2x.trixnity.client.store.RoomUser
import de.connect2x.trixnity.client.store.RoomUserReceipts
import de.connect2x.trixnity.client.store.TimelineEvent
import de.connect2x.trixnity.client.store.TimelineEventRelation
import de.connect2x.trixnity.client.store.UserPresence
import de.connect2x.trixnity.client.user.PowerLevel
import de.connect2x.trixnity.client.verification.ActiveDeviceVerification
import de.connect2x.trixnity.client.verification.ActiveSasVerificationMethod
import de.connect2x.trixnity.client.verification.ActiveSasVerificationState
import de.connect2x.trixnity.client.verification.ActiveUserVerification
import de.connect2x.trixnity.client.verification.ActiveVerification
import de.connect2x.trixnity.client.verification.ActiveVerificationState
import de.connect2x.trixnity.client.verification.VerificationService
import de.connect2x.trixnity.clientserverapi.model.media.FileTransferProgress
import de.connect2x.trixnity.clientserverapi.model.key.GetRoomKeysBackupVersionResponse
import de.connect2x.trixnity.clientserverapi.model.room.GetHierarchy
import de.connect2x.trixnity.core.model.RoomId
import de.connect2x.trixnity.core.model.UserId
import de.connect2x.trixnity.core.model.events.ClientEvent
import de.connect2x.trixnity.core.model.events.MessageEventContent
import de.connect2x.trixnity.core.model.events.RoomEventContent
import de.connect2x.trixnity.core.model.events.StateEventContent
import de.connect2x.trixnity.core.model.events.UnknownEventContent
import de.connect2x.trixnity.core.model.events.m.RelatesTo
import de.connect2x.trixnity.core.model.events.m.key.verification.VerificationMethod
import de.connect2x.trixnity.core.model.events.m.ReactionEventContent
import de.connect2x.trixnity.core.model.events.m.room.AvatarEventContent
import de.connect2x.trixnity.core.model.events.m.room.CreateEventContent
import de.connect2x.trixnity.core.model.events.m.room.NameEventContent
import de.connect2x.trixnity.core.model.events.m.room.PowerLevelsEventContent
import de.connect2x.trixnity.core.model.events.m.room.RoomMessageEventContent
import de.connect2x.trixnity.core.model.events.m.room.TopicEventContent
import de.connect2x.trixnity.core.model.events.m.space.ChildEventContent
import de.connect2x.trixnity.core.model.events.m.space.ParentEventContent
import de.connect2x.trixnity.crypto.key.DeviceTrustLevel
import de.connect2x.trixnity.crypto.key.UserTrustLevel
import kotlinx.coroutines.flow.firstOrNull
import kotlin.reflect.KClass
import java.time.Duration

internal fun normalizedKind(value: String?): String? =
    value
        ?.replace(Regex("([a-z0-9])([A-Z])"), "$1-$2")
        ?.replace('_', '-')
        ?.lowercase()

internal fun normalizeRelation(relatesTo: RelatesTo): Map<Keyword, Any?> =
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

internal fun relationTypeName(relatesTo: RelatesTo): String =
    when (relatesTo) {
        is RelatesTo.Thread -> "m.thread"
        is RelatesTo.Reply -> "m.in_reply_to"
        is RelatesTo.Annotation -> "m.annotation"
        is RelatesTo.Reference -> "m.reference"
        is RelatesTo.Replace -> "m.replace"
        is RelatesTo.Unknown -> relatesTo.relationType.name
    }

internal fun roomTypeName(type: CreateEventContent.RoomType?): String? =
    when (type) {
        null,
        CreateEventContent.RoomType.Room -> null

        CreateEventContent.RoomType.Space -> "m.space"
        is CreateEventContent.RoomType.Unknown -> type.name
    }

private fun eventTypeName(content: RoomEventContent?): String? =
    when (content) {
        is RoomMessageEventContent -> "m.room.message"
        is ReactionEventContent -> "m.reaction"
        is UnknownEventContent -> content.eventType
        null -> null
        else -> content::class.simpleName?.let(::normalizedKind)
    }

private fun stateEventTypeName(content: StateEventContent?): String? =
    when (content) {
        is AvatarEventContent -> "m.room.avatar"
        is ChildEventContent -> "m.space.child"
        is NameEventContent -> "m.room.name"
        is ParentEventContent -> "m.space.parent"
        is PowerLevelsEventContent -> "m.room.power_levels"
        is TopicEventContent -> "m.room.topic"
        is UnknownEventContent -> content.eventType
        null -> null
        else -> content::class.simpleName?.let(::normalizedKind)
    }

private suspend fun senderDisplayName(
    client: de.connect2x.trixnity.client.MatrixClient,
    timelineEvent: TimelineEvent,
): String? =
    client.user
        .getById(
            RoomId(timelineEvent.event.roomId.full),
            UserId(timelineEvent.event.sender.full),
        )
        .firstOrNull()
        ?.name

internal fun normalizeRoomSnapshot(room: Room?): Map<Keyword, Any?>? {
    if (room == null) return null
    return buildMap {
        put(BridgeSchema.Room.roomId, room.roomId.full)
        put(BridgeSchema.Room.membership, room.membership.name.lowercase())
        room.name?.explicitName?.let { put(BridgeSchema.Room.roomName, it) }
        roomTypeName(room.createEventContent?.type)?.let { put(BridgeSchema.Room.roomType, it) }
        put(BridgeSchema.Room.isDirect, room.isDirect)
        put(BridgeSchema.Room.raw, room)
    }
}

private fun MutableMap<Keyword, Any?>.putFileBasedMetadata(
    content: RoomMessageEventContent.FileBased,
) {
    put(BridgeSchema.Event.msgtype, content.type)
    put(BridgeSchema.Event.body, content.body)
    content.url?.let { put(BridgeSchema.Event.url, it) }
    content.file?.let { put(BridgeSchema.Event.encryptedFile, normalizeEncryptedFile(it)) }
    content.fileName?.let { put(BridgeSchema.Event.fileName, it) }
    when (val info = content.info) {
        is de.connect2x.trixnity.core.model.events.m.room.AudioInfo -> {
            info.mimeType?.let { put(BridgeSchema.Event.mimeType, it) }
            info.size?.let { put(BridgeSchema.Event.sizeBytes, it) }
            info.duration?.let { put(BridgeSchema.Event.duration, Duration.ofMillis(it)) }
        }

        is de.connect2x.trixnity.core.model.events.m.room.ImageInfo -> {
            info.mimeType?.let { put(BridgeSchema.Event.mimeType, it) }
            info.size?.let { put(BridgeSchema.Event.sizeBytes, it) }
            info.height?.let { put(BridgeSchema.Event.height, it) }
            info.width?.let { put(BridgeSchema.Event.width, it) }
            info.thumbnailUrl?.let { put(BridgeSchema.Event.thumbnailUrl, it) }
            info.thumbnailFile?.let {
                put(BridgeSchema.Event.thumbnailEncryptedFile, normalizeEncryptedFile(it))
            }
        }

        is de.connect2x.trixnity.core.model.events.m.room.VideoInfo -> {
            info.mimeType?.let { put(BridgeSchema.Event.mimeType, it) }
            info.size?.let { put(BridgeSchema.Event.sizeBytes, it) }
            info.duration?.let { put(BridgeSchema.Event.duration, Duration.ofMillis(it)) }
            info.height?.let { put(BridgeSchema.Event.height, it) }
            info.width?.let { put(BridgeSchema.Event.width, it) }
            info.thumbnailUrl?.let { put(BridgeSchema.Event.thumbnailUrl, it) }
            info.thumbnailFile?.let {
                put(BridgeSchema.Event.thumbnailEncryptedFile, normalizeEncryptedFile(it))
            }
        }

        is de.connect2x.trixnity.core.model.events.m.room.FileInfo -> {
            info.mimeType?.let { put(BridgeSchema.Event.mimeType, it) }
            info.size?.let { put(BridgeSchema.Event.sizeBytes, it) }
            info.thumbnailUrl?.let { put(BridgeSchema.Event.thumbnailUrl, it) }
            info.thumbnailFile?.let {
                put(BridgeSchema.Event.thumbnailEncryptedFile, normalizeEncryptedFile(it))
            }
        }

        null -> Unit
    }
}

internal suspend fun normalizeTimelineEvent(
    client: de.connect2x.trixnity.client.MatrixClient,
    timelineEvent: TimelineEvent?,
): Map<Keyword, Any?>? {
    if (timelineEvent == null) return null
    val content = timelineEvent.content?.getOrNull()
    return buildMap {
        eventTypeName(content)?.let { put(BridgeSchema.Event.type, it) }
        put(BridgeSchema.Event.roomId, timelineEvent.event.roomId.full)
        put(BridgeSchema.Event.eventId, timelineEvent.event.id.full)
        put(BridgeSchema.Event.sender, timelineEvent.event.sender.full)
        senderDisplayName(client, timelineEvent)?.let {
            put(BridgeSchema.Event.senderDisplayName, it)
        }
        when (content) {
            is RoomMessageEventContent.TextBased.Text -> {
                put(BridgeSchema.Event.body, content.body)
                content.relatesTo?.let { put(BridgeSchema.Event.relatesTo, normalizeRelation(it)) }
            }

            is RoomMessageEventContent.FileBased -> {
                putFileBasedMetadata(content)
                content.relatesTo?.let { put(BridgeSchema.Event.relatesTo, normalizeRelation(it)) }
            }

            is ReactionEventContent -> {
                val annotation = content.relatesTo
                annotation?.key?.let { put(BridgeSchema.Event.key, it) }
                annotation?.let { put(BridgeSchema.Event.relatesTo, normalizeRelation(it)) }
            }

            is MessageEventContent -> {
                content.relatesTo?.let { put(BridgeSchema.Event.relatesTo, normalizeRelation(it)) }
            }

            else -> Unit
        }
        content?.let { put(BridgeSchema.content, it) }
        put(BridgeSchema.Event.raw, timelineEvent)
    }
}

internal fun normalizeStateEvent(event: ClientEvent.StateBaseEvent<*>?): Map<Keyword, Any?>? {
    if (event == null) return null
    return buildMap {
        stateEventTypeName(event.content)?.let {
            put(BridgeSchema.StateEvent.type, it)
        }
        event.roomId?.full?.let { put(BridgeSchema.StateEvent.roomId, it) }
        event.id?.full?.let { put(BridgeSchema.StateEvent.eventId, it) }
        put(BridgeSchema.StateEvent.sender, event.sender.full)
        put(BridgeSchema.StateEvent.stateKey, event.stateKey)
        put(BridgeSchema.StateEvent.content, normalizeStateEventContent(event.content))
        put(BridgeSchema.StateEvent.raw, event)
    }
}

internal fun normalizeContent(value: Any?): Map<Keyword, Any?>? =
    if (value == null) null
    else mapOf(
        BridgeSchema.content to value,
        BridgeSchema.raw to value,
    )

internal fun normalizeSpaceChildContent(content: ChildEventContent): Map<Keyword, Any?> =
    buildMap {
        put(BridgeSchema.via, content.via)
        content.order?.let { put(BridgeSchema.order, it) }
        put(BridgeSchema.suggested, content.suggested)
        content.externalUrl?.let { put(BridgeSchema.externalUrl, it) }
        put(BridgeSchema.raw, content)
    }

internal fun normalizeSpaceParentContent(content: ParentEventContent): Map<Keyword, Any?> =
    buildMap {
        put(BridgeSchema.via, content.via)
        put(BridgeSchema.canonical, content.canonical)
        content.externalUrl?.let { put(BridgeSchema.externalUrl, it) }
        put(BridgeSchema.raw, content)
    }

private fun normalizeStateEventContent(content: StateEventContent): Any =
    when (content) {
        is ChildEventContent -> normalizeSpaceChildContent(content)
        is ParentEventContent -> normalizeSpaceParentContent(content)
        is PowerLevelsEventContent -> normalizePowerLevelsContent(content)
        else -> content
    }

internal fun normalizePowerLevelsContent(content: PowerLevelsEventContent): Map<Keyword, Any?> {
    val power = BridgeSchema.PowerLevelsContent
    return buildMap {
        put(power.banLevel, content.ban)
        put(power.eventLevels, content.events.entries.associate { it.key.name to it.value })
        put(power.eventsDefaultLevel, content.eventsDefault)
        put(power.inviteLevel, content.invite)
        put(power.kickLevel, content.kick)
        put(power.redactLevel, content.redact)
        put(power.stateDefaultLevel, content.stateDefault)
        put(power.userLevels, content.users.entries.associate { it.key.full to it.value })
        put(power.usersDefaultLevel, content.usersDefault)
        content.notifications?.let { put(power.notificationLevels, it) }
        content.externalUrl?.let { put(power.externalUrl, it) }
        put(power.raw, content)
    }
}

internal fun normalizeHierarchyResponse(response: GetHierarchy.Response): Map<Keyword, Any?> =
    buildMap {
        response.nextBatch?.let { put(BridgeSchema.nextBatch, it) }
        put(BridgeSchema.rooms, response.rooms.map(::normalizeHierarchyRoom))
        put(BridgeSchema.raw, response)
    }

private fun normalizeHierarchyRoom(
    room: GetHierarchy.Response.SpaceHierarchyRoomsChunk,
): Map<Keyword, Any?> =
    buildMap {
        room.allowedRoomIds?.let { roomIds ->
            put(BridgeSchema.allowedRoomIds, roomIds.map { it.full }.toSet())
        }
        room.avatarUrl?.let { put(BridgeSchema.avatarUrl, it) }
        room.canonicalAlias?.let { put(BridgeSchema.canonicalAlias, it.full) }
        put(BridgeSchema.childrenState, room.childrenState.mapNotNull(::normalizeStateEvent))
        room.encryption?.let { put(BridgeSchema.encryption, it.name) }
        put(BridgeSchema.guestCanJoin, room.guestCanJoin)
        put(BridgeSchema.joinRule, room.joinRule.name)
        room.name?.let { put(BridgeSchema.name, it) }
        put(BridgeSchema.joinedMembersCount, room.joinedMembersCount)
        put(BridgeSchema.roomId, room.roomId.full)
        roomTypeName(room.roomType)?.let { put(BridgeSchema.roomType, it) }
        room.roomVersion?.let { put(BridgeSchema.roomVersion, it) }
        room.topic?.let { put(BridgeSchema.topic, it) }
        put(BridgeSchema.worldReadable, room.worldReadable)
        put(BridgeSchema.raw, room)
    }

internal fun normalizeFileTransferProgress(
    progress: FileTransferProgress?,
): Map<Keyword, Any?>? {
    if (progress == null) return null
    return buildMap {
        put(BridgeSchema.transferred, progress.transferred)
        progress.total?.let { put(BridgeSchema.total, it) }
    }
}

internal fun normalizeRoomOutboxMessage(
    message: RoomOutboxMessage<*>?,
): Map<Keyword, Any?>? {
    if (message == null) return null
    return buildMap {
        put(BridgeSchema.RoomOutboxMessage.roomId, message.roomId.full)
        put(BridgeSchema.RoomOutboxMessage.transactionId, message.transactionId)
        message.eventId?.full?.let { put(BridgeSchema.RoomOutboxMessage.eventId, it) }
        put(BridgeSchema.RoomOutboxMessage.content, message.content)
        put(BridgeSchema.RoomOutboxMessage.createdAt, message.createdAt.toString())
        message.sentAt?.let { put(BridgeSchema.RoomOutboxMessage.sentAt, it.toString()) }
        message.sendError?.let { put(BridgeSchema.RoomOutboxMessage.sendError, normalizedKind(it::class.simpleName)) }
        normalizeFileTransferProgress(message.mediaUploadProgress.value)?.let {
            put(BridgeSchema.RoomOutboxMessage.mediaUploadProgress, it)
        }
        put(BridgeSchema.RoomOutboxMessage.raw, message)
    }
}

internal fun normalizeTimelineEventRelation(
    relation: TimelineEventRelation?,
): Map<Keyword, Any?>? {
    if (relation == null) return null
    return mapOf(
        BridgeSchema.roomId to relation.roomId.full,
        BridgeSchema.eventId to relation.eventId.full,
        BridgeSchema.Relation.type to relation.relationType.name,
        BridgeSchema.Relation.eventId to relation.relatedEventId.full,
        BridgeSchema.raw to relation,
    )
}

internal fun normalizeRoomUser(user: RoomUser?): Map<Keyword, Any?>? {
    if (user == null) return null
    return mapOf(
        BridgeSchema.RoomUser.roomId to user.roomId.full,
        BridgeSchema.RoomUser.userId to user.userId.full,
        BridgeSchema.RoomUser.name to user.name,
        BridgeSchema.RoomUser.raw to user,
    )
}

internal fun normalizeRoomUserReceipts(
    receipts: RoomUserReceipts?,
): Map<Keyword, Any?>? {
    if (receipts == null) return null
    return buildMap {
        put(BridgeSchema.RoomUserReceipts.roomId, receipts.roomId.full)
        put(BridgeSchema.RoomUserReceipts.userId, receipts.userId.full)
        put(
            BridgeSchema.RoomUserReceipts.receipts,
            receipts.receipts.map { (receiptType, receipt) ->
                mapOf(
                    BridgeSchema.RoomUserReceipt.receiptType to receiptType.name,
                    BridgeSchema.RoomUserReceipt.eventId to receipt.eventId.full,
                    BridgeSchema.RoomUserReceipt.raw to receipt,
                )
            },
        )
        put(BridgeSchema.RoomUserReceipts.raw, receipts)
    }
}

internal fun normalizePowerLevel(level: PowerLevel?): Map<Keyword, Any?>? =
    when (level) {
        null -> null
        is PowerLevel.Creator -> mapOf(
            BridgeSchema.PowerLevel.kind to "creator",
            BridgeSchema.PowerLevel.raw to level,
        )

        is PowerLevel.User -> mapOf(
            BridgeSchema.PowerLevel.kind to "user",
            BridgeSchema.PowerLevel.level to level.level,
            BridgeSchema.PowerLevel.raw to level,
        )
    }

internal fun normalizeUserPresence(presence: UserPresence?): Map<Keyword, Any?>? {
    if (presence == null) return null
    return buildMap {
        put(BridgeSchema.UserPresence.presence, presence.presence.name.lowercase())
        put(BridgeSchema.UserPresence.lastUpdate, presence.lastUpdate.toString())
        presence.lastActive?.let { put(BridgeSchema.UserPresence.lastActive, it.toString()) }
        presence.isCurrentlyActive?.let { put(BridgeSchema.UserPresence.currentlyActive, it) }
        presence.statusMessage?.let { put(BridgeSchema.UserPresence.statusMessage, it) }
        put(BridgeSchema.UserPresence.raw, presence)
    }
}

private fun normalizeActions(actions: Set<*>): Set<String> =
    actions.map { it.toString() }.toSet()

internal suspend fun normalizeNotification(
    client: de.connect2x.trixnity.client.MatrixClient,
    notification: Notification?,
): Map<Keyword, Any?>? {
    if (notification == null) return null
    return buildMap {
        put(BridgeSchema.Notification.id, notification.id)
        put(BridgeSchema.Notification.sortKey, notification.sortKey)
        put(BridgeSchema.Notification.actions, normalizeActions(notification.actions))
        put(BridgeSchema.Notification.dismissed, notification.dismissed)
        when (notification) {
            is Notification.Message -> {
                put(BridgeSchema.Notification.kind, "message")
                put(
                    BridgeSchema.Notification.timelineEvent,
                    normalizeTimelineEvent(client, notification.timelineEvent),
                )
            }

            is Notification.State -> {
                put(BridgeSchema.Notification.kind, "state")
                put(
                    BridgeSchema.Notification.stateEvent,
                    normalizeStateEvent(notification.stateEvent),
                )
            }
        }
        put(BridgeSchema.Notification.raw, notification)
    }
}

private suspend fun normalizeNotificationUpdateContent(
    client: de.connect2x.trixnity.client.MatrixClient,
    content: NotificationUpdate.Content,
): Map<Keyword, Any?> =
    when (content) {
        is NotificationUpdate.Content.Message -> mapOf(
            BridgeSchema.Notification.timelineEvent to
                normalizeTimelineEvent(client, content.timelineEvent),
            BridgeSchema.raw to content,
        )

        is NotificationUpdate.Content.State -> mapOf(
            BridgeSchema.Notification.stateEvent to normalizeStateEvent(content.stateEvent),
            BridgeSchema.raw to content,
        )
    }

internal suspend fun normalizeNotificationUpdate(
    client: de.connect2x.trixnity.client.MatrixClient,
    update: NotificationUpdate,
): Map<Keyword, Any?> =
    buildMap {
        put(BridgeSchema.NotificationUpdate.id, update.id)
        put(BridgeSchema.NotificationUpdate.sortKey, update.sortKey)
        put(BridgeSchema.NotificationUpdate.raw, update)
        when (update) {
            is NotificationUpdate.New -> {
                put(BridgeSchema.NotificationUpdate.kind, "new")
                put(BridgeSchema.NotificationUpdate.actions, normalizeActions(update.actions))
                put(
                    BridgeSchema.NotificationUpdate.content,
                    normalizeNotificationUpdateContent(client, update.content),
                )
            }

            is NotificationUpdate.Update -> {
                put(BridgeSchema.NotificationUpdate.kind, "update")
                put(BridgeSchema.NotificationUpdate.actions, normalizeActions(update.actions))
                put(
                    BridgeSchema.NotificationUpdate.content,
                    normalizeNotificationUpdateContent(client, update.content),
                )
            }

            is NotificationUpdate.Remove -> {
                put(BridgeSchema.NotificationUpdate.kind, "remove")
            }
        }
    }

private val sasEmojiDescriptions = mapOf(
    0 to "Dog", 1 to "Cat", 2 to "Lion", 3 to "Horse",
    4 to "Unicorn", 5 to "Pig", 6 to "Elephant", 7 to "Rabbit",
    8 to "Panda", 9 to "Rooster", 10 to "Penguin", 11 to "Turtle",
    12 to "Fish", 13 to "Octopus", 14 to "Butterfly", 15 to "Flower",
    16 to "Tree", 17 to "Cactus", 18 to "Mushroom", 19 to "Globe",
    20 to "Moon", 21 to "Cloud", 22 to "Fire", 23 to "Banana",
    24 to "Apple", 25 to "Strawberry", 26 to "Corn", 27 to "Pizza",
    28 to "Cake", 29 to "Heart", 30 to "Smiley", 31 to "Robot",
    32 to "Hat", 33 to "Glasses", 34 to "Spanner", 35 to "Santa",
    36 to "Thumbs Up", 37 to "Umbrella", 38 to "Hourglass",
    39 to "Clock", 40 to "Gift", 41 to "Light Bulb", 42 to "Book",
    43 to "Pencil", 44 to "Paperclip", 45 to "Scissors",
    46 to "Lock", 47 to "Key", 48 to "Hammer", 49 to "Telephone",
    50 to "Flag", 51 to "Train", 52 to "Bicycle", 53 to "Aeroplane",
    54 to "Rocket", 55 to "Trophy", 56 to "Ball", 57 to "Guitar",
    58 to "Trumpet", 59 to "Bell", 60 to "Anchor",
    61 to "Headphones", 62 to "Folder", 63 to "Pin",
)

internal fun activeVerificationId(verification: ActiveVerification): String =
    when (verification) {
        is ActiveUserVerification ->
            "user:${verification.roomId.full}:${verification.requestEventId.full}"

        is ActiveDeviceVerification ->
            "device:${verification.theirUserId.full}:${verification.transactionId}"

        else -> "verification:${verification.theirUserId.full}:${verification.timestamp}"
    }

private fun normalizeVerificationMethod(method: VerificationMethod): String =
    when (method) {
        VerificationMethod.Sas -> VerificationMethod.Sas.value
        is VerificationMethod.Unknown -> method.value
    }

private fun normalizeSasEmojis(
    emojis: List<Pair<Int, String>>,
): List<Map<Keyword, Any?>> =
    emojis.map { (index, emoji) ->
        buildMap {
            put(BridgeSchema.SasEmoji.index, index)
            put(BridgeSchema.SasEmoji.emoji, emoji)
            sasEmojiDescriptions[index]?.let { put(BridgeSchema.SasEmoji.description, it) }
        }
    }

private fun normalizeSasVerificationStateValue(
    state: ActiveSasVerificationState?,
): Map<Keyword, Any?>? =
    if (state == null) null
    else buildMap {
        put(BridgeSchema.SasVerificationState.kind, normalizedKind(state::class.simpleName))
        when (state) {
            is ActiveSasVerificationState.ComparisonByUser -> {
                put(BridgeSchema.SasVerificationState.sasDecimal, state.decimal)
                put(BridgeSchema.SasVerificationState.sasEmojis, normalizeSasEmojis(state.emojis))
            }

            else -> Unit
        }
        put(BridgeSchema.SasVerificationState.raw, state)
    }

private fun normalizeVerificationStateValue(
    state: ActiveVerificationState?,
    ownUserId: String? = null,
    ownDeviceId: String? = null,
): Map<Keyword, Any?>? =
    if (state == null) null
    else buildMap {
        put(BridgeSchema.VerificationState.kind, normalizedKind(state::class.simpleName))
        when (state) {
            is ActiveVerificationState.OwnRequest -> {
                put(BridgeSchema.VerificationState.verificationDirection, "outgoing")
                put(
                    BridgeSchema.VerificationState.methods,
                    state.content.methods.map(::normalizeVerificationMethod).toSet(),
                )
            }

            is ActiveVerificationState.TheirRequest -> {
                put(BridgeSchema.VerificationState.verificationDirection, "incoming")
                put(
                    BridgeSchema.VerificationState.methods,
                    state.content.methods.map(::normalizeVerificationMethod).toSet(),
                )
            }

            is ActiveVerificationState.Ready -> {
                put(
                    BridgeSchema.VerificationState.methods,
                    state.methods.map(::normalizeVerificationMethod).toSet(),
                )
            }

            is ActiveVerificationState.Start -> {
                val method = state.method
                if (method is ActiveSasVerificationMethod) {
                    put(BridgeSchema.VerificationState.verificationMethod, VerificationMethod.Sas.value)
                    put(
                        BridgeSchema.VerificationState.sasState,
                        normalizeSasVerificationStateValue(method.state.value),
                    )
                }
                put(BridgeSchema.VerificationState.senderUserId, state.senderUserId.full)
                put(BridgeSchema.VerificationState.senderDeviceId, state.senderDeviceId)
                if (ownUserId != null && ownDeviceId != null) {
                    val direction =
                        if (state.senderUserId.full == ownUserId && state.senderDeviceId == ownDeviceId)
                            "outgoing"
                        else "incoming"
                    put(BridgeSchema.VerificationState.verificationDirection, direction)
                }
            }

            is ActiveVerificationState.WaitForDone -> {
                put(BridgeSchema.VerificationState.isOurOwn, state.isOurOwn)
            }

            is ActiveVerificationState.Cancel -> {
                put(BridgeSchema.VerificationState.cancelCode, state.content.code.value)
                put(BridgeSchema.VerificationState.reason, state.content.reason)
                put(BridgeSchema.VerificationState.isOurOwn, state.isOurOwn)
            }

            ActiveVerificationState.AcceptedByOtherDevice,
            ActiveVerificationState.Done,
            ActiveVerificationState.Undefined,
            -> Unit
        }
        put(BridgeSchema.VerificationState.raw, state)
    }

internal fun normalizeActiveVerification(
    verification: ActiveVerification?,
    ownUserId: String? = null,
    ownDeviceId: String? = null,
): Map<Keyword, Any?>? {
    if (verification == null) return null
    return buildMap {
        put(BridgeSchema.ActiveVerification.verificationId, activeVerificationId(verification))
        put(
            BridgeSchema.ActiveVerification.verificationKind,
            when (verification) {
                is ActiveUserVerification -> "user"
                is ActiveDeviceVerification -> "device"
                else -> "unknown"
            },
        )
        put(BridgeSchema.ActiveVerification.theirUserId, verification.theirUserId.full)
        verification.theirDeviceId?.let { put(BridgeSchema.ActiveVerification.theirDeviceId, it) }
        verification.transactionId?.let { put(BridgeSchema.ActiveVerification.transactionId, it) }
        put(BridgeSchema.ActiveVerification.timestamp, verification.timestamp)
        val normalizedState = normalizeVerificationStateValue(
            verification.state.value,
            ownUserId,
            ownDeviceId,
        )
        normalizedState?.get(BridgeSchema.VerificationState.verificationDirection)?.let {
            put(BridgeSchema.ActiveVerification.verificationDirection, it)
        }
        put(BridgeSchema.ActiveVerification.verificationState, normalizedState)
        when (verification) {
            is ActiveUserVerification -> {
                put(BridgeSchema.ActiveVerification.requestEventId, verification.requestEventId.full)
                put(BridgeSchema.ActiveVerification.roomId, verification.roomId.full)
            }

            is ActiveDeviceVerification -> Unit
        }
        put(BridgeSchema.ActiveVerification.raw, verification)
    }
}

internal fun normalizeSelfVerificationMethods(
    methods: VerificationService.SelfVerificationMethods,
): Map<Keyword, Any?> =
    buildMap {
        put(BridgeSchema.SelfVerificationMethods.raw, methods)
        when (methods) {
            is VerificationService.SelfVerificationMethods.PreconditionsNotMet -> {
                put(BridgeSchema.SelfVerificationMethods.kind, "preconditions-not-met")
                put(
                    BridgeSchema.SelfVerificationMethods.reasons,
                    methods.reasons.mapNotNull { normalizedKind(it::class.simpleName) }.toSet(),
                )
            }

            VerificationService.SelfVerificationMethods.NoCrossSigningEnabled -> {
                put(BridgeSchema.SelfVerificationMethods.kind, "no-cross-signing-enabled")
            }

            VerificationService.SelfVerificationMethods.AlreadyCrossSigned -> {
                put(BridgeSchema.SelfVerificationMethods.kind, "already-cross-signed")
            }

            is VerificationService.SelfVerificationMethods.CrossSigningEnabled -> {
                put(BridgeSchema.SelfVerificationMethods.kind, "cross-signing-enabled")
                put(
                    BridgeSchema.SelfVerificationMethods.methods,
                    methods.methods.mapNotNull { normalizedKind(it::class.simpleName) }.toSet(),
                )
            }
        }
    }

internal fun normalizeDeviceTrustLevel(
    trustLevel: DeviceTrustLevel?,
): Map<Keyword, Any?>? =
    if (trustLevel == null) null
    else buildMap {
        put(BridgeSchema.TrustLevel.kind, normalizedKind(trustLevel::class.simpleName))
        when (trustLevel) {
            is DeviceTrustLevel.Valid -> put(BridgeSchema.TrustLevel.verified, trustLevel.verified)
            is DeviceTrustLevel.CrossSigned -> put(BridgeSchema.TrustLevel.verified, trustLevel.verified)
            is DeviceTrustLevel.Invalid -> put(BridgeSchema.TrustLevel.reason, trustLevel.reason)
            else -> Unit
        }
        put(BridgeSchema.TrustLevel.raw, trustLevel)
    }

internal fun normalizeUserTrustLevel(
    trustLevel: UserTrustLevel?,
): Map<Keyword, Any?>? =
    if (trustLevel == null) null
    else buildMap {
        put(BridgeSchema.TrustLevel.kind, normalizedKind(trustLevel::class.simpleName))
        when (trustLevel) {
            is UserTrustLevel.CrossSigned -> put(BridgeSchema.TrustLevel.verified, trustLevel.verified)
            is UserTrustLevel.NotAllDevicesCrossSigned -> put(BridgeSchema.TrustLevel.verified, trustLevel.verified)
            is UserTrustLevel.Invalid -> put(BridgeSchema.TrustLevel.reason, trustLevel.reason)
            else -> Unit
        }
        put(BridgeSchema.TrustLevel.raw, trustLevel)
    }

internal fun normalizeBackupVersion(
    version: GetRoomKeysBackupVersionResponse.V1?,
): Map<Keyword, Any?>? {
    if (version == null) return null
    return mapOf(
        BridgeSchema.BackupVersion.version to version.version,
        BridgeSchema.BackupVersion.algorithm to version.algorithm.name,
        BridgeSchema.BackupVersion.auth to version.authData,
        BridgeSchema.BackupVersion.raw to version,
    )
}

@Suppress("UNCHECKED_CAST")
internal fun <T : Any> javaClassToKClass(javaClass: Class<*>): KClass<T> =
    javaClass.kotlin as KClass<T>
