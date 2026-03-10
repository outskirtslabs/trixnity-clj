package ol.trixnity.bridge

import de.connect2x.trixnity.client.store.KeyChainLink
import de.connect2x.trixnity.client.store.StoredNotification
import de.connect2x.trixnity.client.store.StoredNotificationUpdate
import de.connect2x.trixnity.client.store.repository.InboundMegolmMessageIndexRepositoryKey
import de.connect2x.trixnity.client.store.repository.InboundMegolmSessionRepositoryKey
import de.connect2x.trixnity.client.store.repository.RoomAccountDataRepositoryKey
import de.connect2x.trixnity.client.store.repository.RoomOutboxMessageRepositoryKey
import de.connect2x.trixnity.client.store.repository.RoomStateRepositoryKey
import de.connect2x.trixnity.client.store.repository.TimelineEventKey
import de.connect2x.trixnity.client.store.repository.TimelineEventRelationKey
import de.connect2x.trixnity.core.model.EventId
import de.connect2x.trixnity.core.model.RoomId
import de.connect2x.trixnity.core.model.UserId
import de.connect2x.trixnity.core.model.keys.Key
import de.connect2x.trixnity.core.model.keys.KeyValue.Curve25519KeyValue

object Sqlite4cljModelBridge {
    @JvmStatic fun boxedRoomId(full: String): Any = RoomId(full)
    @JvmStatic fun boxedUserId(full: String): Any = UserId(full)
    @JvmStatic fun boxedEventId(full: String): Any = EventId(full)
    @JvmStatic fun curve25519KeyValue(value: String): Curve25519KeyValue = Curve25519KeyValue(value)

    @JvmStatic fun inboundMegolmMessageIndexRoomId(key: InboundMegolmMessageIndexRepositoryKey): String = key.roomId.full
    @JvmStatic fun inboundMegolmSessionRoomId(key: InboundMegolmSessionRepositoryKey): String = key.roomId.full
    @JvmStatic fun roomStateRoomId(key: RoomStateRepositoryKey): String = key.roomId.full
    @JvmStatic fun timelineEventRoomId(key: TimelineEventKey): String = key.roomId.full
    @JvmStatic fun timelineEventEventId(key: TimelineEventKey): String = key.eventId.full
    @JvmStatic fun timelineEventRelationRoomId(key: TimelineEventRelationKey): String = key.roomId.full
    @JvmStatic fun timelineEventRelationRelatedEventId(key: TimelineEventRelationKey): String = key.relatedEventId.full
    @JvmStatic fun roomAccountDataRoomId(key: RoomAccountDataRepositoryKey): String = key.roomId.full
    @JvmStatic fun roomOutboxMessageRoomId(key: RoomOutboxMessageRepositoryKey): String = key.roomId.full

    @JvmStatic fun notificationRoomId(value: StoredNotification): String = value.roomId.full
    @JvmStatic fun notificationUpdateRoomId(value: StoredNotificationUpdate): String = value.roomId.full

    @JvmStatic fun keyChainLinkSigningUserId(value: KeyChainLink): String = value.signingUserId.full
    @JvmStatic fun keyChainLinkSignedUserId(value: KeyChainLink): String = value.signedUserId.full

    @JvmStatic fun ed25519Key(id: String?, value: String): Key.Ed25519Key = Key.Ed25519Key(id, value)
    @JvmStatic fun ed25519KeyId(value: Key.Ed25519Key): String? = value.id
    @JvmStatic fun ed25519KeyValue(value: Key.Ed25519Key): String = value.value.value

    @JvmStatic
    fun keyChainLink(
        signingUserId: String,
        signingKey: Key.Ed25519Key,
        signedUserId: String,
        signedKey: Key.Ed25519Key,
    ): KeyChainLink = KeyChainLink(UserId(signingUserId), signingKey, UserId(signedUserId), signedKey)
}
