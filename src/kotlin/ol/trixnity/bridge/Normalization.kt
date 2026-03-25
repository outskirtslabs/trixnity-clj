package ol.trixnity.bridge

import clojure.lang.Keyword
import de.connect2x.trixnity.client.notification.Notification
import de.connect2x.trixnity.client.notification.NotificationUpdate
import de.connect2x.trixnity.client.user
import de.connect2x.trixnity.client.store.RoomOutboxMessage
import de.connect2x.trixnity.client.store.RoomUser
import de.connect2x.trixnity.client.store.RoomUserReceipts
import de.connect2x.trixnity.client.store.TimelineEvent
import de.connect2x.trixnity.client.store.TimelineEventRelation
import de.connect2x.trixnity.client.store.UserPresence
import de.connect2x.trixnity.client.user.PowerLevel
import de.connect2x.trixnity.client.verification.ActiveDeviceVerification
import de.connect2x.trixnity.client.verification.ActiveUserVerification
import de.connect2x.trixnity.client.verification.ActiveVerification
import de.connect2x.trixnity.client.verification.VerificationService
import de.connect2x.trixnity.clientserverapi.model.media.FileTransferProgress
import de.connect2x.trixnity.clientserverapi.model.key.GetRoomKeysBackupVersionResponse
import de.connect2x.trixnity.core.model.RoomId
import de.connect2x.trixnity.core.model.UserId
import de.connect2x.trixnity.core.model.events.ClientEvent
import de.connect2x.trixnity.core.model.events.MessageEventContent
import de.connect2x.trixnity.core.model.events.RoomEventContent
import de.connect2x.trixnity.core.model.events.UnknownEventContent
import de.connect2x.trixnity.core.model.events.m.RelatesTo
import de.connect2x.trixnity.core.model.events.m.ReactionEventContent
import de.connect2x.trixnity.core.model.events.m.room.RoomMessageEventContent
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

private fun eventTypeName(content: RoomEventContent?): String? =
    when (content) {
        is RoomMessageEventContent -> "m.room.message"
        is ReactionEventContent -> "m.reaction"
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
        event.content::class.simpleName?.let(::normalizedKind)?.let {
            put(BridgeSchema.StateEvent.type, it)
        }
        event.roomId?.full?.let { put(BridgeSchema.StateEvent.roomId, it) }
        event.id?.full?.let { put(BridgeSchema.StateEvent.eventId, it) }
        put(BridgeSchema.StateEvent.sender, event.sender.full)
        put(BridgeSchema.StateEvent.stateKey, event.stateKey)
        put(BridgeSchema.StateEvent.content, event.content)
        put(BridgeSchema.StateEvent.raw, event)
    }
}

internal fun normalizeContent(value: Any?): Map<Keyword, Any?>? =
    if (value == null) null
    else mapOf(
        BridgeSchema.content to value,
        BridgeSchema.raw to value,
    )

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

private fun normalizeVerificationStateValue(
    state: Any?,
): Map<Keyword, Any?>? =
    if (state == null) null
    else mapOf(
        BridgeSchema.VerificationState.kind to normalizedKind(state::class.simpleName),
        BridgeSchema.VerificationState.raw to state,
    )

internal fun normalizeActiveVerification(
    verification: ActiveVerification?,
): Map<Keyword, Any?>? {
    if (verification == null) return null
    return buildMap {
        put(BridgeSchema.ActiveVerification.theirUserId, verification.theirUserId.full)
        verification.theirDeviceId?.let { put(BridgeSchema.ActiveVerification.theirDeviceId, it) }
        verification.transactionId?.let { put(BridgeSchema.ActiveVerification.transactionId, it) }
        put(BridgeSchema.ActiveVerification.timestamp, verification.timestamp)
        put(
            BridgeSchema.ActiveVerification.verificationState,
            normalizeVerificationStateValue(verification.state.value),
        )
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
