package ol.trixnity.bridge

import de.connect2x.trixnity.client.verification.ActiveDeviceVerification
import de.connect2x.trixnity.client.verification.ActiveUserVerification
import de.connect2x.trixnity.client.verification.ActiveVerificationState
import de.connect2x.trixnity.core.model.EventId
import de.connect2x.trixnity.core.model.RoomId
import de.connect2x.trixnity.core.model.UserId
import de.connect2x.trixnity.core.model.events.m.RelatesTo
import de.connect2x.trixnity.core.model.events.m.key.verification.VerificationMethod
import de.connect2x.trixnity.core.model.events.m.room.RoomMessageEventContent
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlin.test.Test
import kotlin.test.assertEquals

class VerificationBridgeNormalizationTest {
    private class FakeDeviceVerification(
        override val theirUserId: UserId,
        override val timestamp: Long,
        override val transactionId: String?,
        override val theirDeviceId: String?,
        override val state: StateFlow<ActiveVerificationState>,
    ) : ActiveDeviceVerification {
        override val relatesTo: RelatesTo.Reference? = null

        override suspend fun cancel(message: String) {}
    }

    private class FakeUserVerification(
        override val theirUserId: UserId,
        override val timestamp: Long,
        override val requestEventId: EventId,
        override val roomId: RoomId,
        override val theirDeviceId: String?,
        override val state: StateFlow<ActiveVerificationState>,
    ) : ActiveUserVerification {
        override val relatesTo: RelatesTo.Reference = RelatesTo.Reference(requestEventId)
        override val transactionId: String? = null

        override suspend fun cancel(message: String) {}
    }

    @Test
    fun activeDeviceVerificationSnapshotsIncludeStableIdAndReadyMethods() {
        val state = MutableStateFlow<ActiveVerificationState>(
            ActiveVerificationState.Ready(
                ownDeviceId = "BOTDEVICE",
                methods = setOf(VerificationMethod.Sas),
                relatesTo = null,
                transactionId = "txn1",
            ) {},
        )
        val verification = FakeDeviceVerification(
            theirUserId = UserId("@alice:example.org"),
            timestamp = 1,
            transactionId = "txn1",
            theirDeviceId = "ALICEDEVICE",
            state = state,
        )

        val snapshot = normalizeActiveVerification(
            verification,
            ownUserId = "@bot:example.org",
            ownDeviceId = "BOTDEVICE",
        )!!
        val verificationState = snapshot[BridgeSchema.ActiveVerification.verificationState] as Map<*, *>

        assertEquals("device:@alice:example.org:txn1", snapshot[BridgeSchema.ActiveVerification.verificationId])
        assertEquals("device", snapshot[BridgeSchema.ActiveVerification.verificationKind])
        assertEquals("@alice:example.org", snapshot[BridgeSchema.ActiveVerification.theirUserId])
        assertEquals("ALICEDEVICE", snapshot[BridgeSchema.ActiveVerification.theirDeviceId])
        assertEquals("ready", verificationState[BridgeSchema.VerificationState.kind])
        assertEquals(setOf("m.sas.v1"), verificationState[BridgeSchema.VerificationState.methods])
    }

    @Test
    fun activeUserVerificationSnapshotsIncludeRoomRequestAndIncomingDirection() {
        val eventId = EventId("\$event")
        val roomId = RoomId("!room:example.org")
        val request = RoomMessageEventContent.VerificationRequest(
            fromDevice = "ALICEDEVICE",
            to = UserId("@bot:example.org"),
            methods = setOf(VerificationMethod.Sas),
        )
        val state = MutableStateFlow<ActiveVerificationState>(
            ActiveVerificationState.TheirRequest(
                content = request,
                ownDeviceId = "BOTDEVICE",
                supportedMethods = setOf(VerificationMethod.Sas),
                relatesTo = RelatesTo.Reference(eventId),
                transactionId = null,
            ) {},
        )
        val verification = FakeUserVerification(
            theirUserId = UserId("@alice:example.org"),
            timestamp = 2,
            requestEventId = eventId,
            roomId = roomId,
            theirDeviceId = "ALICEDEVICE",
            state = state,
        )

        val snapshot = normalizeActiveVerification(
            verification,
            ownUserId = "@bot:example.org",
            ownDeviceId = "BOTDEVICE",
        )!!
        val verificationState = snapshot[BridgeSchema.ActiveVerification.verificationState] as Map<*, *>

        assertEquals("user:!room:example.org:\$event", snapshot[BridgeSchema.ActiveVerification.verificationId])
        assertEquals("user", snapshot[BridgeSchema.ActiveVerification.verificationKind])
        assertEquals("!room:example.org", snapshot[BridgeSchema.ActiveVerification.roomId])
        assertEquals("\$event", snapshot[BridgeSchema.ActiveVerification.requestEventId])
        assertEquals("incoming", snapshot[BridgeSchema.ActiveVerification.verificationDirection])
        assertEquals("their-request", verificationState[BridgeSchema.VerificationState.kind])
        assertEquals("incoming", verificationState[BridgeSchema.VerificationState.verificationDirection])
        assertEquals(setOf("m.sas.v1"), verificationState[BridgeSchema.VerificationState.methods])
    }
}
