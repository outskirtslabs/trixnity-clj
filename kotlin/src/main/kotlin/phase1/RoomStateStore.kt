package phase1

import net.folivo.trixnity.core.model.RoomId
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.readText
import kotlin.io.path.writeText

object RoomStateStore {
    fun load(path: Path): RoomId? {
        if (!Files.exists(path)) return null
        val raw = path.readText().trim()
        if (raw.isBlank()) return null
        return runCatching { RoomId(raw) }.getOrNull()
    }

    fun save(path: Path, roomId: RoomId) {
        val parent = path.parent
        if (parent != null) Files.createDirectories(parent)
        path.writeText(roomId.full)
    }
}
