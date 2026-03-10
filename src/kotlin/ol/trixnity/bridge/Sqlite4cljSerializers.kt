package ol.trixnity.bridge

import de.connect2x.trixnity.client.store.*
import de.connect2x.trixnity.client.store.repository.*
import de.connect2x.trixnity.core.model.UserId
import de.connect2x.trixnity.core.model.events.ClientEvent.GlobalAccountDataEvent
import de.connect2x.trixnity.core.model.events.ClientEvent.RoomAccountDataEvent
import de.connect2x.trixnity.core.model.events.ClientEvent.StateBaseEvent
import de.connect2x.trixnity.core.model.events.MessageEventContent
import de.connect2x.trixnity.core.model.keys.KeyValue.Curve25519KeyValue
import de.connect2x.trixnity.core.serialization.events.EventContentSerializerMappings
import de.connect2x.trixnity.crypto.SecretType
import de.connect2x.trixnity.crypto.olm.StoredInboundMegolmMessageIndex
import de.connect2x.trixnity.crypto.olm.StoredInboundMegolmSession
import de.connect2x.trixnity.crypto.olm.StoredOlmSession
import de.connect2x.trixnity.crypto.olm.StoredOutboundMegolmSession
import kotlinx.serialization.KSerializer
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.SetSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json

object Sqlite4cljSerializers {
    @JvmStatic fun account(): KSerializer<Account> = Account.serializer()
    @JvmStatic fun authentication(): KSerializer<Authentication> = Authentication.serializer()
    @JvmStatic fun serverData(): KSerializer<ServerData> = ServerData.serializer()
    @JvmStatic fun outdatedKeys(): KSerializer<Set<UserId>> = SetSerializer(UserId.serializer())
    @JvmStatic fun deviceKeys(): KSerializer<Map<String, StoredDeviceKeys>> =
        MapSerializer(String.serializer(), StoredDeviceKeys.serializer())
    @JvmStatic fun crossSigningKeys(): KSerializer<Set<StoredCrossSigningKeys>> =
        SetSerializer(StoredCrossSigningKeys.serializer())
    @JvmStatic fun keyVerificationState(): KSerializer<KeyVerificationState> = KeyVerificationState.serializer()
    @JvmStatic fun secrets(): KSerializer<Map<SecretType, StoredSecret>> =
        MapSerializer(SecretType.serializer(), StoredSecret.serializer())
    @JvmStatic fun string(): KSerializer<String> = String.serializer()
    @OptIn(kotlin.time.ExperimentalTime::class)
    @JvmStatic fun forgetFallbackKeyAfter() = kotlin.time.Instant.serializer()
    @JvmStatic fun olmSessions(): KSerializer<Set<StoredOlmSession>> = SetSerializer(StoredOlmSession.serializer())
    @JvmStatic fun inboundMegolmMessageIndex(): KSerializer<StoredInboundMegolmMessageIndex> =
        StoredInboundMegolmMessageIndex.serializer()
    @JvmStatic fun inboundMegolmSession(): KSerializer<StoredInboundMegolmSession> =
        StoredInboundMegolmSession.serializer()
    @JvmStatic fun outboundMegolmSession(): KSerializer<StoredOutboundMegolmSession> =
        StoredOutboundMegolmSession.serializer()
    @JvmStatic fun room(): KSerializer<Room> = Room.serializer()
    @JvmStatic fun roomUser(): KSerializer<RoomUser> = RoomUser.serializer()
    @JvmStatic fun roomUserReceipts(): KSerializer<RoomUserReceipts> = RoomUserReceipts.serializer()
    @JvmStatic fun roomState(json: Json): KSerializer<StateBaseEvent<*>> =
        requireNotNull(json.serializersModule.getContextual(StateBaseEvent::class))
    @JvmStatic fun timelineEvent(json: Json): KSerializer<TimelineEvent> =
        requireNotNull(json.serializersModule.getContextual(TimelineEvent::class))
    @JvmStatic fun timelineEventRelation(): KSerializer<TimelineEventRelation> = TimelineEventRelation.serializer()
    @JvmStatic fun mediaCacheMapping(): KSerializer<MediaCacheMapping> = MediaCacheMapping.serializer()
    @JvmStatic fun globalAccountData(json: Json): KSerializer<GlobalAccountDataEvent<*>> =
        requireNotNull(json.serializersModule.getContextual(GlobalAccountDataEvent::class))
    @JvmStatic fun userPresence(): KSerializer<UserPresence> = UserPresence.serializer()
    @JvmStatic fun roomAccountData(json: Json): KSerializer<RoomAccountDataEvent<*>> =
        requireNotNull(json.serializersModule.getContextual(RoomAccountDataEvent::class))
    @JvmStatic fun secretKeyRequest(): KSerializer<StoredSecretKeyRequest> = StoredSecretKeyRequest.serializer()
    @JvmStatic fun roomKeyRequest(): KSerializer<StoredRoomKeyRequest> = StoredRoomKeyRequest.serializer()
    @JvmStatic fun notification(): KSerializer<StoredNotification> = StoredNotification.serializer()
    @JvmStatic fun notificationUpdate(): KSerializer<StoredNotificationUpdate> = StoredNotificationUpdate.serializer()
    @JvmStatic fun notificationState(): KSerializer<StoredNotificationState> = StoredNotificationState.serializer()

    @JvmStatic
    fun roomOutboxContentType(mappings: EventContentSerializerMappings, value: RoomOutboxMessage<*>): String =
        requireNotNull(mappings.message.find { it.kClass.isInstance(value.content) }).type

    @JvmStatic
    fun roomOutboxSerializer(
        mappings: EventContentSerializerMappings,
        contentType: String,
    ): KSerializer<RoomOutboxMessage<MessageEventContent>> {
        val serializer = requireNotNull(mappings.message.find { it.type == contentType }).serializer
        @Suppress("UNCHECKED_CAST")
        return RoomOutboxMessage.Companion.serializer(serializer as KSerializer<MessageEventContent>)
    }
}
