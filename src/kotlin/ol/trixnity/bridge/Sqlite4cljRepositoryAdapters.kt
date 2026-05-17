package ol.trixnity.bridge

import de.connect2x.trixnity.client.store.KeyChainLink
import de.connect2x.trixnity.client.store.RoomOutboxMessage
import de.connect2x.trixnity.client.store.RoomUser
import de.connect2x.trixnity.client.store.RoomUserReceipts
import de.connect2x.trixnity.client.store.StoredNotification
import de.connect2x.trixnity.client.store.StoredNotificationUpdate
import de.connect2x.trixnity.client.store.StoredStickyEvent
import de.connect2x.trixnity.client.store.TimelineEvent
import de.connect2x.trixnity.client.store.TimelineEventRelation
import de.connect2x.trixnity.client.store.repository.FullRepository
import de.connect2x.trixnity.client.store.repository.KeyChainLinkRepository
import de.connect2x.trixnity.client.store.repository.MapRepository
import de.connect2x.trixnity.client.store.repository.MinimalRepository
import de.connect2x.trixnity.client.store.repository.NotificationRepository
import de.connect2x.trixnity.client.store.repository.NotificationUpdateRepository
import de.connect2x.trixnity.client.store.repository.RoomAccountDataRepository
import de.connect2x.trixnity.client.store.repository.RoomAccountDataRepositoryKey
import de.connect2x.trixnity.client.store.repository.RoomOutboxMessageRepository
import de.connect2x.trixnity.client.store.repository.RoomOutboxMessageRepositoryKey
import de.connect2x.trixnity.client.store.repository.RoomStateRepository
import de.connect2x.trixnity.client.store.repository.RoomStateRepositoryKey
import de.connect2x.trixnity.client.store.repository.RoomUserReceiptsRepository
import de.connect2x.trixnity.client.store.repository.RoomUserRepository
import de.connect2x.trixnity.client.store.repository.StickyEventRepository
import de.connect2x.trixnity.client.store.repository.StickyEventRepositoryFirstKey
import de.connect2x.trixnity.client.store.repository.StickyEventRepositorySecondKey
import de.connect2x.trixnity.client.store.repository.TimelineEventKey
import de.connect2x.trixnity.client.store.repository.TimelineEventRelationKey
import de.connect2x.trixnity.client.store.repository.TimelineEventRelationRepository
import de.connect2x.trixnity.client.store.repository.TimelineEventRepository
import de.connect2x.trixnity.core.MSC4354
import de.connect2x.trixnity.core.model.EventId
import de.connect2x.trixnity.core.model.RoomId
import de.connect2x.trixnity.core.model.UserId
import de.connect2x.trixnity.core.model.events.ClientEvent.RoomAccountDataEvent
import de.connect2x.trixnity.core.model.events.ClientEvent.StateBaseEvent
import de.connect2x.trixnity.core.model.events.StickyEventContent
import de.connect2x.trixnity.core.model.keys.Key
import kotlin.time.Instant

interface DeleteByRoomIdStringOps {
    suspend fun deleteByRoomId(roomId: String)
}

interface RoomUserRepositoryOps : MapRepository<RoomId, UserId, RoomUser>, DeleteByRoomIdStringOps

interface RoomUserReceiptsRepositoryOps : MapRepository<RoomId, UserId, RoomUserReceipts>, DeleteByRoomIdStringOps

interface RoomStateRepositoryOps :
    MapRepository<RoomStateRepositoryKey, String, StateBaseEvent<*>>,
    DeleteByRoomIdStringOps {
    suspend fun getByRooms(roomIds: Set<RoomId>, type: String, stateKey: String): List<StateBaseEvent<*>>
}

interface TimelineEventRepositoryOps : MinimalRepository<TimelineEventKey, TimelineEvent>, DeleteByRoomIdStringOps

interface TimelineEventRelationRepositoryOps :
    MapRepository<TimelineEventRelationKey, EventId, TimelineEventRelation>,
    DeleteByRoomIdStringOps

interface RoomAccountDataRepositoryOps :
    MapRepository<RoomAccountDataRepositoryKey, String, RoomAccountDataEvent<*>>,
    DeleteByRoomIdStringOps

interface RoomOutboxMessageRepositoryOps :
    FullRepository<RoomOutboxMessageRepositoryKey, RoomOutboxMessage<*>>,
    DeleteByRoomIdStringOps

interface NotificationRepositoryOps : FullRepository<String, StoredNotification>, DeleteByRoomIdStringOps

interface NotificationUpdateRepositoryOps : FullRepository<String, StoredNotificationUpdate>, DeleteByRoomIdStringOps

@OptIn(MSC4354::class)
interface StickyEventRepositoryOps :
    MapRepository<StickyEventRepositoryFirstKey, StickyEventRepositorySecondKey, StoredStickyEvent<StickyEventContent>>,
    DeleteByRoomIdStringOps {
    suspend fun getByEndTimeBefore(before: Instant): Set<Pair<StickyEventRepositoryFirstKey, StickyEventRepositorySecondKey>>
    suspend fun getByEventId(
        roomId: String,
        eventId: String,
    ): Pair<StickyEventRepositoryFirstKey, StickyEventRepositorySecondKey>?
}

interface KeyChainLinkRepositoryOps {
    suspend fun save(keyChainLink: KeyChainLink)
    suspend fun getBySigningKey(signingUserId: String, signingKey: Key.Ed25519Key): Set<KeyChainLink>
    suspend fun deleteBySignedKey(signedUserId: String, signedKey: Key.Ed25519Key)
    suspend fun deleteAll()
}

class Sqlite4cljRoomUserRepository(
    private val ops: RoomUserRepositoryOps,
) : RoomUserRepository, MapRepository<RoomId, UserId, RoomUser> by ops {
    override fun serializeKey(firstKey: RoomId, secondKey: UserId): String = ops.serializeKey(firstKey, secondKey)
    override suspend fun deleteByRoomId(roomId: RoomId) = ops.deleteByRoomId(roomId.full)
}

class Sqlite4cljRoomUserReceiptsRepository(
    private val ops: RoomUserReceiptsRepositoryOps,
) : RoomUserReceiptsRepository, MapRepository<RoomId, UserId, RoomUserReceipts> by ops {
    override fun serializeKey(firstKey: RoomId, secondKey: UserId): String = ops.serializeKey(firstKey, secondKey)
    override suspend fun deleteByRoomId(roomId: RoomId) = ops.deleteByRoomId(roomId.full)
}

class Sqlite4cljRoomStateRepository(
    private val ops: RoomStateRepositoryOps,
) : RoomStateRepository, MapRepository<RoomStateRepositoryKey, String, StateBaseEvent<*>> by ops {
    override fun serializeKey(firstKey: RoomStateRepositoryKey, secondKey: String): String =
        ops.serializeKey(firstKey, secondKey)

    override suspend fun getByRooms(
        roomIds: Set<RoomId>,
        type: String,
        stateKey: String,
    ): List<StateBaseEvent<*>> = ops.getByRooms(roomIds, type, stateKey)

    override suspend fun deleteByRoomId(roomId: RoomId) = ops.deleteByRoomId(roomId.full)
}

class Sqlite4cljTimelineEventRepository(
    private val ops: TimelineEventRepositoryOps,
) : TimelineEventRepository, MinimalRepository<TimelineEventKey, TimelineEvent> by ops {
    override fun serializeKey(key: TimelineEventKey): String = ops.serializeKey(key)
    override suspend fun deleteByRoomId(roomId: RoomId) = ops.deleteByRoomId(roomId.full)
}

class Sqlite4cljTimelineEventRelationRepository(
    private val ops: TimelineEventRelationRepositoryOps,
) : TimelineEventRelationRepository, MapRepository<TimelineEventRelationKey, EventId, TimelineEventRelation> by ops {
    override fun serializeKey(firstKey: TimelineEventRelationKey, secondKey: EventId): String =
        ops.serializeKey(firstKey, secondKey)

    override suspend fun deleteByRoomId(roomId: RoomId) = ops.deleteByRoomId(roomId.full)
}

class Sqlite4cljRoomAccountDataRepository(
    private val ops: RoomAccountDataRepositoryOps,
) : RoomAccountDataRepository, MapRepository<RoomAccountDataRepositoryKey, String, RoomAccountDataEvent<*>> by ops {
    override fun serializeKey(firstKey: RoomAccountDataRepositoryKey, secondKey: String): String =
        ops.serializeKey(firstKey, secondKey)

    override suspend fun deleteByRoomId(roomId: RoomId) = ops.deleteByRoomId(roomId.full)
}

class Sqlite4cljRoomOutboxMessageRepository(
    private val ops: RoomOutboxMessageRepositoryOps,
) : RoomOutboxMessageRepository, FullRepository<RoomOutboxMessageRepositoryKey, RoomOutboxMessage<*>> by ops {
    override fun serializeKey(key: RoomOutboxMessageRepositoryKey): String = ops.serializeKey(key)
    override suspend fun deleteByRoomId(roomId: RoomId) = ops.deleteByRoomId(roomId.full)
}

class Sqlite4cljNotificationRepository(
    private val ops: NotificationRepositoryOps,
) : NotificationRepository, FullRepository<String, StoredNotification> by ops {
    override fun serializeKey(key: String): String = ops.serializeKey(key)
    override suspend fun deleteByRoomId(roomId: RoomId) = ops.deleteByRoomId(roomId.full)
}

class Sqlite4cljNotificationUpdateRepository(
    private val ops: NotificationUpdateRepositoryOps,
) : NotificationUpdateRepository, FullRepository<String, StoredNotificationUpdate> by ops {
    override fun serializeKey(key: String): String = ops.serializeKey(key)
    override suspend fun deleteByRoomId(roomId: RoomId) = ops.deleteByRoomId(roomId.full)
}

@OptIn(MSC4354::class)
class Sqlite4cljStickyEventRepository(
    private val ops: StickyEventRepositoryOps,
) : StickyEventRepository,
    MapRepository<StickyEventRepositoryFirstKey, StickyEventRepositorySecondKey, StoredStickyEvent<StickyEventContent>> by ops {
    override fun serializeKey(
        firstKey: StickyEventRepositoryFirstKey,
        secondKey: StickyEventRepositorySecondKey,
    ): String = ops.serializeKey(firstKey, secondKey)

    override suspend fun getByEndTimeBefore(before: Instant): Set<Pair<StickyEventRepositoryFirstKey, StickyEventRepositorySecondKey>> =
        ops.getByEndTimeBefore(before)

    override suspend fun getByEventId(
        roomId: RoomId,
        eventId: EventId,
    ): Pair<StickyEventRepositoryFirstKey, StickyEventRepositorySecondKey>? = ops.getByEventId(roomId.full, eventId.full)

    override suspend fun deleteByRoomId(roomId: RoomId) = ops.deleteByRoomId(roomId.full)
}

class Sqlite4cljKeyChainLinkRepository(
    private val ops: KeyChainLinkRepositoryOps,
) : KeyChainLinkRepository {
    override suspend fun save(keyChainLink: KeyChainLink) = ops.save(keyChainLink)

    override suspend fun getBySigningKey(signingUserId: UserId, signingKey: Key.Ed25519Key): Set<KeyChainLink> =
        ops.getBySigningKey(signingUserId.full, signingKey)

    override suspend fun deleteBySignedKey(signedUserId: UserId, signedKey: Key.Ed25519Key) =
        ops.deleteBySignedKey(signedUserId.full, signedKey)

    override suspend fun deleteAll() = ops.deleteAll()
}
