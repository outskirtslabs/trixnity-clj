package ol.trixnity.bridge

import clojure.java.api.Clojure
import clojure.lang.IFn
import clojure.lang.IPersistentMap
import clojure.lang.Keyword
import de.connect2x.trixnity.client.store.repository.*
import de.connect2x.trixnity.core.serialization.events.EventContentSerializerMappings
import kotlinx.serialization.json.Json

internal object Sqlite4cljClojure {
    private val requireFn: IFn = Clojure.`var`("clojure.core", "require")
    private val repoNs = Clojure.read("ol.trixnity.repo")
    private val commonNs = Clojure.read("ol.trixnity.repo.common")

    private val openHandleFn: IFn by lazy { requireVar("ol.trixnity.repo", "open-handle!") }
    private val ensureSchemaFn: IFn by lazy { requireVar("ol.trixnity.repo.common", "ensure-schema!") }
    private val createRepositoriesFn: IFn by lazy { requireVar("ol.trixnity.repo", "create-repositories") }
    private val borrowReaderFn: IFn by lazy { requireVar("ol.trixnity.repo.common", "borrow-reader-conn!") }
    private val borrowWriterFn: IFn by lazy { requireVar("ol.trixnity.repo.common", "borrow-writer-conn!") }
    private val releaseReaderFn: IFn by lazy { requireVar("ol.trixnity.repo.common", "release-reader-conn!") }
    private val releaseWriterFn: IFn by lazy { requireVar("ol.trixnity.repo.common", "release-writer-conn!") }
    private val beginDeferredFn: IFn by lazy { requireVar("ol.trixnity.repo.common", "begin-deferred!") }
    private val beginImmediateFn: IFn by lazy { requireVar("ol.trixnity.repo.common", "begin-immediate!") }
    private val commitFn: IFn by lazy { requireVar("ol.trixnity.repo.common", "commit!") }
    private val rollbackFn: IFn by lazy { requireVar("ol.trixnity.repo.common", "rollback!") }

    private fun requireVar(ns: String, name: String): IFn {
        requireFn.invoke(if (ns == "ol.trixnity.repo") repoNs else commonNs)
        return Clojure.`var`(ns, name)
    }

    fun openHandle(path: String, json: Json): Sqlite4cljRepositoryHandle =
        openHandleFn.invoke(path, json) as Sqlite4cljRepositoryHandle

    fun ensureSchema(handle: Sqlite4cljRepositoryHandle) {
        ensureSchemaFn.invoke(handle)
    }

    fun createRepositories(
        handle: Sqlite4cljRepositoryHandle,
        mappings: EventContentSerializerMappings,
    ): IPersistentMap = createRepositoriesFn.invoke(handle, mappings) as IPersistentMap

    fun borrowReaderConnection(handle: Sqlite4cljRepositoryHandle): Any = borrowReaderFn.invoke(handle)

    fun borrowWriterConnection(handle: Sqlite4cljRepositoryHandle): Any = borrowWriterFn.invoke(handle)

    fun releaseReaderConnection(handle: Sqlite4cljRepositoryHandle, conn: Any) {
        releaseReaderFn.invoke(handle, conn)
    }

    fun releaseWriterConnection(handle: Sqlite4cljRepositoryHandle, conn: Any) {
        releaseWriterFn.invoke(handle, conn)
    }

    fun beginDeferred(conn: Any) {
        beginDeferredFn.invoke(conn)
    }

    fun beginImmediate(conn: Any) {
        beginImmediateFn.invoke(conn)
    }

    fun commit(conn: Any) {
        commitFn.invoke(conn)
    }

    fun rollback(conn: Any) {
        rollbackFn.invoke(conn)
    }

    @Suppress("UNCHECKED_CAST")
    fun <T> repository(repositories: IPersistentMap, key: String): T =
        repositories.valAt(Keyword.intern(null, key)) as T
}
