package ol.trixnity.bridge

import clojure.lang.Keyword
import net.folivo.trixnity.client.MatrixClient
import net.folivo.trixnity.client.media.okio.createOkioMediaStoreModule
import net.folivo.trixnity.client.store.repository.exposed.createExposedRepositoriesModule
import okio.Path.Companion.toPath
import org.koin.core.module.Module
import org.jetbrains.exposed.sql.Database
import java.nio.file.Files
import java.nio.file.Path

internal fun requireKeywordString(payload: KeywordMap, key: clojure.lang.Keyword): String =
    payload[key]?.toString()?.takeIf { it.isNotBlank() }
        ?: throw IllegalArgumentException("request payload is missing required key $key")

internal fun requireKeywordClient(payload: KeywordMap, key: Keyword): MatrixClient =
    payload[key] as? MatrixClient
        ?: throw IllegalArgumentException("request payload is missing MatrixClient under $key")

internal fun requireKeywordDatabase(payload: KeywordMap, key: Keyword): Database =
    payload[key] as? Database
        ?: throw IllegalArgumentException("request payload is missing Exposed Database under $key")

internal fun requireKeywordValue(payload: KeywordMap, key: Keyword): Any =
    payload[key] ?: throw IllegalArgumentException("request payload is missing required key $key")

internal suspend fun createRepositoriesModule(database: Database): org.koin.core.module.Module {
    return createExposedRepositoriesModule(database)
}

internal fun createMediaStoreModule(mediaPath: String): Module {
    val path = Path.of(mediaPath).toAbsolutePath()
    Files.createDirectories(path)
    return createOkioMediaStoreModule(path.toString().toPath())
}
