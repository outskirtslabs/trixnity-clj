package ol.trixnity.bridge

import clojure.lang.Keyword
import de.connect2x.trixnity.client.flatten
import de.connect2x.trixnity.client.notification
import de.connect2x.trixnity.clientserverapi.model.sync.Sync
import de.connect2x.trixnity.core.model.RoomId
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlin.time.Duration.Companion.milliseconds

object NotificationBridge {
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
            flows.map { it.map(::normalizeNotification) }
        }

    @JvmStatic
    fun allFlat(
        client: de.connect2x.trixnity.client.MatrixClient,
    ): Flow<List<Map<Keyword, Any?>>> =
        client.notification.getAll().flatten().map { notifications ->
            notifications.map(::normalizeNotification).filterNotNull()
        }

    @JvmStatic
    fun byId(
        client: de.connect2x.trixnity.client.MatrixClient,
        id: String,
    ): Flow<Map<Keyword, Any?>?> =
        client.notification.getById(id).map(::normalizeNotification)

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
        client.notification.getAllUpdates().map(::normalizeNotificationUpdate)
}
