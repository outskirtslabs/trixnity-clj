package ol.trixnity.bridge

import de.connect2x.trixnity.client.key
import de.connect2x.trixnity.client.key.KeyBackupService
import de.connect2x.trixnity.core.model.EventId
import de.connect2x.trixnity.core.model.RoomId
import de.connect2x.trixnity.core.model.UserId
import kotlinx.coroutines.flow.map
import org.koin.core.qualifier.named

private val de.connect2x.trixnity.client.MatrixClient.keyBackup: KeyBackupService
    get() = di.get(named<KeyBackupService>())

object KeyBridge {
    @JvmStatic
    fun currentBootstrapRunning(
        client: de.connect2x.trixnity.client.MatrixClient,
    ): Boolean = client.key.bootstrapRunning.value

    @JvmStatic
    fun bootstrapRunningFlow(
        client: de.connect2x.trixnity.client.MatrixClient,
    ) = client.key.bootstrapRunning

    @JvmStatic
    fun currentBackupVersion(
        client: de.connect2x.trixnity.client.MatrixClient,
    ) = normalizeBackupVersion(client.keyBackup.version.value)

    @JvmStatic
    fun backupVersionFlow(
        client: de.connect2x.trixnity.client.MatrixClient,
    ) = client.keyBackup.version.map(::normalizeBackupVersion)

    @JvmStatic
    fun trustLevel(
        client: de.connect2x.trixnity.client.MatrixClient,
        userId: String,
        deviceId: String,
    ) = client.key.getTrustLevel(UserId(userId), deviceId).map(::normalizeDeviceTrustLevel)

    @JvmStatic
    fun trustLevelByTimelineEvent(
        client: de.connect2x.trixnity.client.MatrixClient,
        roomId: String,
        eventId: String,
    ) = client.key.getTrustLevel(RoomId(roomId), EventId(eventId)).map(::normalizeDeviceTrustLevel)

    @JvmStatic
    fun trustLevel(
        client: de.connect2x.trixnity.client.MatrixClient,
        userId: String,
    ) = client.key.getTrustLevel(UserId(userId)).map(::normalizeUserTrustLevel)

    @JvmStatic
    fun deviceKeys(
        client: de.connect2x.trixnity.client.MatrixClient,
        userId: String,
    ) = client.key.getDeviceKeys(UserId(userId)).map { keys ->
        keys?.map(::normalizeContent)
    }

    @JvmStatic
    fun crossSigningKeys(
        client: de.connect2x.trixnity.client.MatrixClient,
        userId: String,
    ) = client.key.getCrossSigningKeys(UserId(userId)).map { keys ->
        keys?.map(::normalizeContent)
    }
}
