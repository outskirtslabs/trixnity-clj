package ol.trixnity.bridge

import net.folivo.trixnity.client.MatrixClient
import net.folivo.trixnity.client.media.okio.OkioMediaStore
import net.folivo.trixnity.client.store.repository.exposed.createExposedRepositoriesModule
import okio.Path.Companion.toPath
import org.jetbrains.exposed.sql.Database
import java.nio.file.Files
import java.nio.file.Path

internal fun requireKeywordString(payload: KeywordMap, key: clojure.lang.Keyword): String =
    payload[key]?.toString()?.takeIf { it.isNotBlank() }
        ?: throw IllegalArgumentException("request payload is missing required key $key")

internal fun requireString(payload: Map<String, Any?>, key: String): String =
    payload[key]?.toString()?.takeIf { it.isNotBlank() }
        ?: throw IllegalArgumentException("request payload is missing required key :$key")

internal fun requireClient(payload: Map<String, Any?>): MatrixClient =
    payload["client"] as? MatrixClient
        ?: throw IllegalArgumentException("request payload is missing MatrixClient under :client")

internal suspend fun createRepositoriesModule(storePath: String): org.koin.core.module.Module {
    val dbPath = Path.of(storePath).toAbsolutePath()
    dbPath.parent?.let { Files.createDirectories(it) }
    val database = Database.connect("jdbc:h2:file:${dbPath};DB_CLOSE_DELAY=-1;")
    return createExposedRepositoriesModule(database)
}

internal fun createMediaStore(mediaPath: String): OkioMediaStore {
    val path = Path.of(mediaPath).toAbsolutePath()
    Files.createDirectories(path)
    return OkioMediaStore(path.toString().toPath())
}
