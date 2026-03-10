package ol.trixnity.bridge

import clojure.lang.Keyword
import de.connect2x.trixnity.client.MatrixClient
import de.connect2x.trixnity.client.MediaStoreModule
import de.connect2x.trixnity.client.RepositoriesModule
import de.connect2x.trixnity.client.media.okio.okio
import okio.Path.Companion.toPath
import java.nio.file.Files
import java.nio.file.Path

internal fun requireKeywordString(payload: KeywordMap, key: clojure.lang.Keyword): String =
    payload[key]?.toString()?.takeIf { it.isNotBlank() }
        ?: throw IllegalArgumentException("request payload is missing required key $key")

internal fun requireKeywordClient(payload: KeywordMap, key: Keyword): MatrixClient =
    payload[key] as? MatrixClient
        ?: throw IllegalArgumentException("request payload is missing MatrixClient under $key")

internal fun requireKeywordValue(payload: KeywordMap, key: Keyword): Any =
    payload[key] ?: throw IllegalArgumentException("request payload is missing required key $key")

internal fun createRepositoriesModule(databasePath: String): RepositoriesModule =
    RepositoriesModule.sqlite4clj(Path.of(databasePath).toAbsolutePath())

internal fun createMediaStoreModule(mediaPath: String): MediaStoreModule {
    val path = Path.of(mediaPath).toAbsolutePath()
    Files.createDirectories(path)
    return MediaStoreModule.okio(path.toString().toPath())
}
