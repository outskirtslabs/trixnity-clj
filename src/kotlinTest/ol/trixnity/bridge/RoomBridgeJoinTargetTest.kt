package ol.trixnity.bridge

import de.connect2x.trixnity.core.model.RoomAliasId
import de.connect2x.trixnity.core.model.RoomId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class RoomBridgeJoinTargetTest {
    @Test
    fun roomAliasTargetsUseRoomAliasIds() {
        val target = parseJoinRoomTarget("#ops:example.org")

        val alias = assertIs<RoomAliasId>(target)
        assertEquals("#ops:example.org", alias.full)
    }

    @Test
    fun roomIdTargetsUseRoomIds() {
        val target = parseJoinRoomTarget("!room:example.org")

        val roomId = assertIs<RoomId>(target)
        assertEquals("!room:example.org", roomId.full)
    }
}
