package ol.trixnity.bridge

import clojure.lang.Keyword
import de.connect2x.trixnity.client.flatten
import de.connect2x.trixnity.client.notification
import de.connect2x.trixnity.clientserverapi.model.sync.Sync
import de.connect2x.trixnity.core.model.EventId
import de.connect2x.trixnity.core.model.RoomId
import de.connect2x.trixnity.core.model.events.m.MarkedUnreadEventContent
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.io.Closeable
import kotlin.time.Duration.Companion.milliseconds

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
    @Deprecated("use getAll/getById/getAllUpdates instead")
    fun notifications(
        client: de.connect2x.trixnity.client.MatrixClient,
        decryptionTimeoutMs: Long?,
        syncResponseBufferSize: Int?,
    ): Flow<Map<Keyword, Any?>> =
        when {
            decryptionTimeoutMs != null && syncResponseBufferSize != null ->
                client.notification.getNotifications(
                    decryptionTimeout = decryptionTimeoutMs.milliseconds,
                    syncResponseBufferSize = syncResponseBufferSize,
                )

            decryptionTimeoutMs != null ->
                client.notification.getNotifications(
                    decryptionTimeout = decryptionTimeoutMs.milliseconds,
                )

            syncResponseBufferSize != null ->
                client.notification.getNotifications(syncResponseBufferSize = syncResponseBufferSize)

            else -> client.notification.getNotifications()
        }.map { notification ->
            normalizeNotificationUpdate(
                client,
                de.connect2x.trixnity.client.notification.NotificationUpdate.New(
                    id = "",
                    sortKey = "",
                    actions = notification.actions,
                    content = when (val event = notification.event) {
                        is de.connect2x.trixnity.core.model.events.ClientEvent.StateBaseEvent<*> ->
                            de.connect2x.trixnity.client.notification.NotificationUpdate.Content.State(event)

                        is de.connect2x.trixnity.core.model.events.ClientEvent.RoomEvent<*> ->
                            de.connect2x.trixnity.client.notification.NotificationUpdate.Content.Message(
                                de.connect2x.trixnity.client.store.TimelineEvent(event),
                            )

                        else -> throw IllegalArgumentException("unsupported deprecated notification event: $event")
                    },
                ),
            )
        }

    @JvmStatic
    @Deprecated("use getAll/getById/getAllUpdates instead")
    fun notificationsFromResponse(
        client: de.connect2x.trixnity.client.MatrixClient,
        response: Sync.Response,
        decryptionTimeoutMs: Long?,
    ): Flow<Map<Keyword, Any?>> =
        if (decryptionTimeoutMs != null) {
            client.notification.getNotifications(
                response = response,
                decryptionTimeout = decryptionTimeoutMs.milliseconds,
            )
        } else {
            client.notification.getNotifications(response = response)
        }.map { notification ->
            normalizeNotificationUpdate(
                client,
                de.connect2x.trixnity.client.notification.NotificationUpdate.New(
                    id = "",
                    sortKey = "",
                    actions = notification.actions,
                    content = when (val event = notification.event) {
                        is de.connect2x.trixnity.core.model.events.ClientEvent.StateBaseEvent<*> ->
                            de.connect2x.trixnity.client.notification.NotificationUpdate.Content.State(event)

                        is de.connect2x.trixnity.core.model.events.ClientEvent.RoomEvent<*> ->
                            de.connect2x.trixnity.client.notification.NotificationUpdate.Content.Message(
                                de.connect2x.trixnity.client.store.TimelineEvent(event),
                            )

                        else -> throw IllegalArgumentException("unsupported deprecated notification event: $event")
                    },
                ),
            )
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
