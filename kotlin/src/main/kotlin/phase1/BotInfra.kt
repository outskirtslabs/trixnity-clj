package phase1

import net.folivo.trixnity.client.media.okio.OkioMediaStore
import net.folivo.trixnity.client.media.MediaStore
import net.folivo.trixnity.client.store.repository.exposed.createExposedRepositoriesModule
import okio.Path.Companion.toPath
import org.jetbrains.exposed.sql.Database
import org.koin.core.module.Module

suspend fun createRepositoriesModule(databasePath: String): Module = createExposedRepositoriesModule(
    database = Database.connect("jdbc:h2:file:$databasePath;DB_CLOSE_DELAY=-1;"),
)

suspend fun createMediaStore(path: String): MediaStore = OkioMediaStore(path.toPath())
