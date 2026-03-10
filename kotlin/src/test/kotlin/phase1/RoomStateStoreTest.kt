package phase1

import net.folivo.trixnity.core.model.RoomId
import java.nio.file.Files
import kotlin.io.path.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class RoomStateStoreTest {
    @Test
    fun `load returns null when file missing`() {
        val path = Path("./kotlin/build/test-room-state-missing.txt")
        Files.deleteIfExists(path)

        assertNull(RoomStateStore.load(path))
    }

    @Test
    fun `save and load round trip room id`() {
        val path = Path("./kotlin/build/test-room-state-roundtrip.txt")
        Files.deleteIfExists(path)

        val roomId = RoomId("!abc123:example.org")
        RoomStateStore.save(path, roomId)

        assertEquals(roomId, RoomStateStore.load(path))
    }
}
