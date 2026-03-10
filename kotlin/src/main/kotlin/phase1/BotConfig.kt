package phase1

import io.ktor.http.Url
import net.folivo.trixnity.core.model.UserId
import java.nio.file.Path
import kotlin.io.path.Path

data class BotConfig(
    val homeserverUrl: Url,
    val username: String,
    val password: String,
    val registrationSharedSecret: String?,
    val botAdmin: Boolean,
    val roomName: String,
    val roomIdFile: Path,
    val mediaPath: Path,
    val databasePath: Path,
    val inviteUser: UserId?,
    val tryRegister: Boolean,
)

object BotConfigLoader {
    fun load(env: Map<String, String> = System.getenv()): BotConfig {
        val timestamp = System.currentTimeMillis()
        val homeserver = env["MATRIX_HOMESERVER_URL"] ?: "http://localhost:8008"
        val username = env["MATRIX_BOT_USERNAME"] ?: "trixnitycljbot"
        val password = env["MATRIX_BOT_PASSWORD"] ?: "password!123"
        val registrationSharedSecret = env["MATRIX_REGISTRATION_SHARED_SECRET"]?.takeIf { it.isNotBlank() }
        val botAdmin = env["MATRIX_BOT_ADMIN"]?.lowercase() == "true"
        val roomName = env["MATRIX_ROOM_NAME"] ?: "trixnity-clj-bot-room-$timestamp"
        val roomIdFile = Path(env["MATRIX_ROOM_ID_FILE"] ?: "./kotlin/.bot-state/room-id.txt")
        val mediaPath = Path(env["MATRIX_MEDIA_PATH"] ?: "./kotlin/.bot-media")
        val databasePath = Path(env["MATRIX_DB_PATH"] ?: "./kotlin/.bot-state/trixnity")
        val inviteUser = env["MATRIX_INVITE_USER"]?.takeIf { it.isNotBlank() }?.let(::UserId)
        val tryRegister = env["MATRIX_TRY_REGISTER"]?.lowercase() != "false"

        return BotConfig(
            homeserverUrl = Url(homeserver),
            username = username,
            password = password,
            registrationSharedSecret = registrationSharedSecret,
            botAdmin = botAdmin,
            roomName = roomName,
            roomIdFile = roomIdFile,
            mediaPath = mediaPath,
            databasePath = databasePath,
            inviteUser = inviteUser,
            tryRegister = tryRegister,
        )
    }
}
