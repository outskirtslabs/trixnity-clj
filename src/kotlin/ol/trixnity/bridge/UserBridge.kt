package ol.trixnity.bridge

import clojure.lang.Keyword
import de.connect2x.trixnity.client.user
import de.connect2x.trixnity.core.model.EventId
import de.connect2x.trixnity.core.model.RoomId
import de.connect2x.trixnity.core.model.UserId
import de.connect2x.trixnity.core.model.events.GlobalAccountDataEventContent
import de.connect2x.trixnity.core.model.events.RoomEventContent
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

object UserBridge {
    @JvmStatic
    fun all(
        client: de.connect2x.trixnity.client.MatrixClient,
        roomId: String,
    ): Flow<Map<String, Flow<Map<Keyword, Any?>?>>> =
        client.user.getAll(RoomId(roomId)).map { users ->
            users.entries.associate { (userId, userFlow) ->
                userId.full to userFlow.map(::normalizeRoomUser)
            }
        }

    @JvmStatic
    fun byId(
        client: de.connect2x.trixnity.client.MatrixClient,
        roomId: String,
        userId: String,
    ): Flow<Map<Keyword, Any?>?> =
        client.user.getById(RoomId(roomId), UserId(userId)).map(::normalizeRoomUser)

    @JvmStatic
    fun allReceipts(
        client: de.connect2x.trixnity.client.MatrixClient,
        roomId: String,
    ): Flow<Map<String, Flow<Map<Keyword, Any?>?>>> =
        client.user.getAllReceipts(RoomId(roomId)).map { receipts ->
            receipts.entries.associate { (userId, receiptFlow) ->
                userId.full to receiptFlow.map(::normalizeRoomUserReceipts)
            }
        }

    @JvmStatic
    fun receiptsById(
        client: de.connect2x.trixnity.client.MatrixClient,
        roomId: String,
        userId: String,
    ): Flow<Map<Keyword, Any?>?> =
        client.user.getReceiptsById(RoomId(roomId), UserId(userId)).map(::normalizeRoomUserReceipts)

    @JvmStatic
    fun powerLevel(
        client: de.connect2x.trixnity.client.MatrixClient,
        roomId: String,
        userId: String,
    ): Flow<Map<Keyword, Any?>?> =
        client.user.getPowerLevel(RoomId(roomId), UserId(userId)).map(::normalizePowerLevel)

    @JvmStatic
    fun canKickUser(
        client: de.connect2x.trixnity.client.MatrixClient,
        roomId: String,
        userId: String,
    ): Flow<Boolean> =
        client.user.canKickUser(RoomId(roomId), UserId(userId))

    @JvmStatic
    fun canBanUser(
        client: de.connect2x.trixnity.client.MatrixClient,
        roomId: String,
        userId: String,
    ): Flow<Boolean> =
        client.user.canBanUser(RoomId(roomId), UserId(userId))

    @JvmStatic
    fun canUnbanUser(
        client: de.connect2x.trixnity.client.MatrixClient,
        roomId: String,
        userId: String,
    ): Flow<Boolean> =
        client.user.canUnbanUser(RoomId(roomId), UserId(userId))

    @JvmStatic
    fun canInviteUser(
        client: de.connect2x.trixnity.client.MatrixClient,
        roomId: String,
        userId: String,
    ): Flow<Boolean> =
        client.user.canInviteUser(RoomId(roomId), UserId(userId))

    @JvmStatic
    fun canInvite(
        client: de.connect2x.trixnity.client.MatrixClient,
        roomId: String,
    ): Flow<Boolean> =
        client.user.canInvite(RoomId(roomId))

    @JvmStatic
    fun canRedactEvent(
        client: de.connect2x.trixnity.client.MatrixClient,
        roomId: String,
        eventId: String,
    ): Flow<Boolean> =
        client.user.canRedactEvent(RoomId(roomId), EventId(eventId))

    @JvmStatic
    fun canSetPowerLevelToMax(
        client: de.connect2x.trixnity.client.MatrixClient,
        roomId: String,
        userId: String,
    ): Flow<Map<Keyword, Any?>?> =
        client.user.canSetPowerLevelToMax(RoomId(roomId), UserId(userId)).map(::normalizePowerLevel)

    @JvmStatic
    fun canSendEventByClass(
        client: de.connect2x.trixnity.client.MatrixClient,
        roomId: String,
        eventContentClass: Class<*>,
    ): Flow<Boolean> =
        client.user.canSendEvent(RoomId(roomId), javaClassToKClass<RoomEventContent>(eventContentClass))

    @JvmStatic
    fun canSendEventByContent(
        client: de.connect2x.trixnity.client.MatrixClient,
        roomId: String,
        eventContent: Any,
    ): Flow<Boolean> =
        client.user.canSendEvent(RoomId(roomId), eventContent as RoomEventContent)

    @JvmStatic
    fun presence(
        client: de.connect2x.trixnity.client.MatrixClient,
        userId: String,
    ): Flow<Map<Keyword, Any?>?> =
        client.user.getPresence(UserId(userId)).map(::normalizeUserPresence)

    @JvmStatic
    fun accountData(
        client: de.connect2x.trixnity.client.MatrixClient,
        eventContentClass: Class<*>,
        key: String,
    ): Flow<Map<Keyword, Any?>?> =
        client.user.getAccountData(
            javaClassToKClass<GlobalAccountDataEventContent>(eventContentClass),
            key,
        ).map(::normalizeContent)
}
