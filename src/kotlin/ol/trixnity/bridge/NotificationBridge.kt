package ol.trixnity.bridge

import clojure.lang.Keyword
import de.connect2x.trixnity.client.flatten
import de.connect2x.trixnity.client.notification
import de.connect2x.trixnity.core.model.EventId
import de.connect2x.trixnity.core.model.RoomId
import de.connect2x.trixnity.core.model.events.m.MarkedUnreadEventContent
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.io.Closeable

object NotificationBridge {
    @JvmStatic
    fun markRead(
        client: de.connect2x.trixnity.client.MatrixClient,
        roomId: String,
        eventId: String,
        onSuccess: Any,
        onFailure: Any,
    ): Closeable = submitBridgeTask(
        scope = BridgeAsync.clientScope(client),
        onSuccess = onSuccess,
        onFailure = onFailure,
    ) {
        val parsedRoomId = RoomId(roomId)
        val parsedEventId = EventId(eventId)
        client.api.room.setReadMarkers(
            roomId = parsedRoomId,
            fullyRead = parsedEventId,
            read = parsedEventId,
        ).getOrThrow()
        client.api.room.setAccountData(
            MarkedUnreadEventContent(false),
            parsedRoomId,
            client.userId,
        ).getOrThrow()
        null
    }

    @JvmStatic
    fun markUnread(
        client: de.connect2x.trixnity.client.MatrixClient,
        roomId: String,
        onSuccess: Any,
        onFailure: Any,
    ): Closeable = submitBridgeTask(
        scope = BridgeAsync.clientScope(client),
        onSuccess = onSuccess,
        onFailure = onFailure,
    ) {
        client.api.room.setAccountData(
            MarkedUnreadEventContent(true),
            RoomId(roomId),
            client.userId,
        ).getOrThrow()
        null
    }

    @JvmStatic
    fun dismiss(
        client: de.connect2x.trixnity.client.MatrixClient,
        id: String,
        onSuccess: Any,
        onFailure: Any,
    ): Closeable = submitBridgeTask(
        scope = BridgeAsync.clientScope(client),
        onSuccess = onSuccess,
        onFailure = onFailure,
    ) {
        client.notification.dismiss(id)
        null
    }

    @JvmStatic
    fun dismissAll(
        client: de.connect2x.trixnity.client.MatrixClient,
        onSuccess: Any,
        onFailure: Any,
    ): Closeable = submitBridgeTask(
        scope = BridgeAsync.clientScope(client),
        onSuccess = onSuccess,
        onFailure = onFailure,
    ) {
        client.notification.dismissAll()
        null
    }

    @JvmStatic
    fun all(
        client: de.connect2x.trixnity.client.MatrixClient,
    ): Flow<List<Flow<Map<Keyword, Any?>?>>> =
        client.notification.getAll().map { flows ->
            flows.map { flow ->
                flow.map { normalizeNotification(client, it) }
            }
        }

    @JvmStatic
    fun allFlat(
        client: de.connect2x.trixnity.client.MatrixClient,
    ): Flow<List<Map<Keyword, Any?>>> =
        client.notification.getAll().flatten().map { notifications ->
            notifications.map { normalizeNotification(client, it) }.filterNotNull()
        }

    @JvmStatic
    fun byId(
        client: de.connect2x.trixnity.client.MatrixClient,
        id: String,
    ): Flow<Map<Keyword, Any?>?> =
        client.notification.getById(id).map { normalizeNotification(client, it) }

    @JvmStatic
    fun count(
        client: de.connect2x.trixnity.client.MatrixClient,
    ): Flow<Int> =
        client.notification.getCount()

    @JvmStatic
    fun count(
        client: de.connect2x.trixnity.client.MatrixClient,
        roomId: String,
    ): Flow<Int> =
        client.notification.getCount(RoomId(roomId))

    @JvmStatic
    fun unread(
        client: de.connect2x.trixnity.client.MatrixClient,
        roomId: String,
    ): Flow<Boolean> =
        client.notification.isUnread(RoomId(roomId))

    @JvmStatic
    fun allUpdates(
        client: de.connect2x.trixnity.client.MatrixClient,
    ): Flow<Map<Keyword, Any?>> =
        client.notification.getAllUpdates().map { normalizeNotificationUpdate(client, it) }
}
