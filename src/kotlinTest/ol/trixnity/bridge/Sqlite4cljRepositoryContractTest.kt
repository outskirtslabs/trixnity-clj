package ol.trixnity.bridge

import de.connect2x.trixnity.client.RepositoriesModule
import de.connect2x.trixnity.client.store.repository.test.RepositoryTestSuite
import java.nio.file.Files

class Sqlite4cljRepositoryContractTest : RepositoryTestSuite(
    repositoriesModule = RepositoriesModule.sqlite4clj(
        Files.createTempFile("trixnity-clj-repository-", ".sqlite").toAbsolutePath()
    )
)
