@file:OptIn(
    kotlinx.coroutines.ExperimentalCoroutinesApi::class,
    kotlinx.coroutines.ExperimentalForInheritanceCoroutinesApi::class,
)

package ol.trixnity.bridge

import clojure.lang.IFn
import clojure.lang.Keyword
import de.connect2x.trixnity.client.MatrixClient
import de.connect2x.trixnity.client.MediaStoreModule
import de.connect2x.trixnity.client.media.MediaService
import de.connect2x.trixnity.client.media.PlatformMedia
import de.connect2x.trixnity.client.RepositoriesModule
import de.connect2x.trixnity.client.media.okio.OkioPlatformMedia
import de.connect2x.trixnity.client.media.okio.okio
import de.connect2x.trixnity.clientserverapi.model.media.FileTransferProgress
import de.connect2x.trixnity.clientserverapi.model.media.ThumbnailResizingMethod
import de.connect2x.trixnity.core.model.EventId
import de.connect2x.trixnity.core.model.events.StateEventContent
import de.connect2x.trixnity.core.model.events.m.RelatesTo
import de.connect2x.trixnity.core.model.events.m.room.AvatarEventContent
import de.connect2x.trixnity.core.model.events.m.room.EncryptedFile
import de.connect2x.trixnity.core.model.events.m.room.NameEventContent
import de.connect2x.trixnity.core.model.events.m.room.TopicEventContent
import de.connect2x.trixnity.utils.ByteArrayFlow
import de.connect2x.trixnity.utils.byteArrayFlowFromInputStream
import io.ktor.http.ContentType
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.flow.MutableStateFlow
import okio.Path.Companion.toPath
import java.io.FilterInputStream
import java.io.IOException
import java.io.InputStream
import java.io.PipedInputStream
import java.io.PipedOutputStream
import java.io.Closeable
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.time.Duration

internal data class RelationSpec(
    val type: String,
    val eventId: String,
    val key: String? = null,
    val replyToEventId: String? = null,
    val isFallingBack: Boolean? = null,
)

internal data class ReplyTarget(
    val eventId: String,
    val relatesTo: RelationSpec? = null,
)

internal sealed interface MessageSpec {
    val body: String
    val format: String?
    val formattedBody: String?
    val replyTo: ReplyTarget?
}

internal data class TextMessageSpec(
    override val body: String,
    override val format: String? = null,
    override val formattedBody: String? = null,
    override val replyTo: ReplyTarget? = null,
) : MessageSpec

internal data class EmoteMessageSpec(
    override val body: String,
    override val format: String? = null,
    override val formattedBody: String? = null,
    override val replyTo: ReplyTarget? = null,
) : MessageSpec

internal sealed interface AttachmentMessageSpec : MessageSpec {
    val sourcePath: Path
    val fileName: String?
    val mimeType: ContentType?
    val sizeBytes: Long?
}

internal data class AudioMessageSpec(
    override val body: String,
    override val sourcePath: Path,
    override val fileName: String? = null,
    override val mimeType: ContentType? = null,
    override val sizeBytes: Long? = null,
    val durationMillis: Long? = null,
    override val format: String? = null,
    override val formattedBody: String? = null,
    override val replyTo: ReplyTarget? = null,
) : AttachmentMessageSpec

internal data class ImageMessageSpec(
    override val body: String,
    override val sourcePath: Path,
    override val fileName: String? = null,
    override val mimeType: ContentType? = null,
    override val sizeBytes: Long? = null,
    val height: Int? = null,
    val width: Int? = null,
    override val format: String? = null,
    override val formattedBody: String? = null,
    override val replyTo: ReplyTarget? = null,
) : AttachmentMessageSpec

internal data class FileMessageSpec(
    override val body: String,
    override val sourcePath: Path,
    override val fileName: String? = null,
    override val mimeType: ContentType? = null,
    override val sizeBytes: Long? = null,
    override val format: String? = null,
    override val formattedBody: String? = null,
    override val replyTo: ReplyTarget? = null,
) : AttachmentMessageSpec

internal sealed interface StateEventSpec {
    val stateKey: String

    fun toEventContent(): StateEventContent
}

internal data class RoomNameStateEventSpec(
    val name: String,
    override val stateKey: String = "",
) : StateEventSpec {
    override fun toEventContent(): StateEventContent = NameEventContent(name)
}

internal data class RoomTopicStateEventSpec(
    val topic: String,
    override val stateKey: String = "",
) : StateEventSpec {
    override fun toEventContent(): StateEventContent = TopicEventContent(topic)
}

internal data class RoomAvatarStateEventSpec(
    val url: String,
    override val stateKey: String = "",
) : StateEventSpec {
    override fun toEventContent(): StateEventContent = AvatarEventContent(url = url)
}

internal fun requireKeywordString(payload: Map<*, *>, key: Keyword): String =
    payload[key]?.toString()?.takeIf { it.isNotBlank() }
        ?: throw IllegalArgumentException("request payload is missing required key $key")

internal fun optionalKeywordString(payload: Map<*, *>, key: Keyword): String? =
    payload[key]?.toString()?.takeIf { it.isNotBlank() }

internal fun requireKeywordClient(payload: KeywordMap, key: Keyword): MatrixClient =
    payload[key] as? MatrixClient
        ?: throw IllegalArgumentException("request payload is missing MatrixClient under $key")

internal fun requireKeywordValue(payload: Map<*, *>, key: Keyword): Any =
    payload[key] ?: throw IllegalArgumentException("request payload is missing required key $key")

internal fun requireKeywordMap(payload: Map<*, *>, key: Keyword): Map<*, *> =
    payload[key] as? Map<*, *>
        ?: throw IllegalArgumentException("request payload is missing map under $key")

internal fun optionalKeywordDuration(payload: Map<*, *>, key: Keyword): Duration? =
    payload[key] as? Duration

internal fun optionalKeywordLong(payload: Map<*, *>, key: Keyword): Long? =
    (payload[key] as? Number)?.toLong()

internal fun optionalKeywordInt(payload: Map<*, *>, key: Keyword): Int? =
    (payload[key] as? Number)?.toInt()

internal fun optionalKeywordBoolean(payload: Map<*, *>, key: Keyword): Boolean? =
    payload[key] as? Boolean

internal fun requireKeywordStringMap(payload: Map<*, *>, key: Keyword): Map<String, String> =
    requireKeywordMap(payload, key).mapKeys { (k, _) -> k.toString() }.mapValues { (_, v) -> v.toString() }

internal fun parseContentType(value: String, key: Keyword): ContentType =
    try {
        ContentType.parse(value)
    } catch (error: Throwable) {
        throw IllegalArgumentException("invalid MIME type for $key: $value", error)
    }

internal fun parseThumbnailMethod(value: String?, key: Keyword): ThumbnailResizingMethod =
    when (value?.lowercase()) {
        null, "crop" -> ThumbnailResizingMethod.CROP
        "scale" -> ThumbnailResizingMethod.SCALE
        else -> throw IllegalArgumentException("invalid thumbnail method for $key: $value")
    }

internal fun optionalKeywordContentType(payload: Map<*, *>, key: Keyword): ContentType? =
    optionalKeywordString(payload, key)?.let { parseContentType(it, key) }

internal fun requireReadablePath(payload: Map<*, *>, key: Keyword): Path {
    return requireReadablePath(requireKeywordString(payload, key), key)
}

internal fun requireReadablePath(pathValue: String, key: Keyword): Path {
    val path = try {
        Paths.get(pathValue).toAbsolutePath().normalize()
    } catch (error: Throwable) {
        throw IllegalArgumentException("message source path for $key is invalid", error)
    }
    if (!Files.isReadable(path) || !Files.isRegularFile(path)) {
        throw IllegalArgumentException("message source path is not readable: $path")
    }
    return path
}

internal fun byteArrayFlowFromPath(path: Path): ByteArrayFlow =
    byteArrayFlowFromInputStream { Files.newInputStream(path) }

internal fun requireEncryptedFile(payload: Map<*, *>, key: Keyword): EncryptedFile {
    val raw = requireKeywordMap(payload, key)
    val jwk = requireKeywordMap(raw, BridgeSchema.jwk)
    val keyOperations =
        (jwk[BridgeSchema.keyOperations] as? Collection<*>)?.map { it.toString() }?.toSet()
            ?: setOf("encrypt", "decrypt")

    return EncryptedFile(
        url = requireKeywordString(raw, BridgeSchema.url),
        key = EncryptedFile.JWK(
            key = requireKeywordString(jwk, BridgeSchema.jwkKey),
            keyType = optionalKeywordString(jwk, BridgeSchema.keyType) ?: "oct",
            keyOperations = keyOperations,
            algorithm = optionalKeywordString(jwk, BridgeSchema.algorithm) ?: "A256CTR",
            extractable = optionalKeywordBoolean(jwk, BridgeSchema.extractable) ?: true,
        ),
        initialisationVector = requireKeywordString(raw, BridgeSchema.initializationVector),
        hashes = requireKeywordStringMap(raw, BridgeSchema.hashes),
        version = optionalKeywordString(raw, BridgeSchema.version) ?: "v2",
    )
}

internal fun normalizeEncryptedFile(encryptedFile: EncryptedFile): Map<Keyword, Any?> =
    mapOf(
        BridgeSchema.url to encryptedFile.url,
        BridgeSchema.jwk to
            mapOf(
                BridgeSchema.jwkKey to encryptedFile.key.key,
                BridgeSchema.keyType to encryptedFile.key.keyType,
                BridgeSchema.keyOperations to encryptedFile.key.keyOperations,
                BridgeSchema.algorithm to encryptedFile.key.algorithm,
                BridgeSchema.extractable to encryptedFile.key.extractable,
            ),
        BridgeSchema.initializationVector to encryptedFile.initialisationVector,
        BridgeSchema.hashes to encryptedFile.hashes,
        BridgeSchema.version to encryptedFile.version,
    )

internal fun platformMediaInputStream(
    scope: CoroutineScope,
    platformMedia: PlatformMedia,
): InputStream {
    val input = PipedInputStream(64 * 1024)
    val output = PipedOutputStream(input)
    lateinit var writer: Job
    writer = scope.launch(Dispatchers.IO) {
        try {
            platformMedia.collect { chunk ->
                output.write(chunk)
            }
        } catch (_: IOException) {
            // Reader closed early.
        } finally {
            runCatching { output.close() }
        }
    }
    return object : FilterInputStream(input) {
        override fun close() {
            runCatching { super.close() }
            writer.cancel()
        }
    }
}

internal fun normalizeMediaHandle(
    scope: CoroutineScope,
    platformMedia: PlatformMedia,
): Map<Keyword, Any?> =
    mapOf(
        BridgeSchema.inputStream to platformMediaInputStream(scope, platformMedia),
        BridgeSchema.raw to platformMedia,
    )

internal suspend fun getMedia(
    scope: CoroutineScope,
    mediaService: MediaService,
    uri: String,
): Map<Keyword, Any?> =
    normalizeMediaHandle(scope, mediaService.getMedia(uri).getOrThrow())

internal suspend fun getEncryptedMedia(
    scope: CoroutineScope,
    mediaService: MediaService,
    encryptedFile: EncryptedFile,
): Map<Keyword, Any?> =
    normalizeMediaHandle(scope, mediaService.getEncryptedMedia(encryptedFile).getOrThrow())

internal suspend fun getThumbnail(
    scope: CoroutineScope,
    mediaService: MediaService,
    uri: String,
    width: Long,
    height: Long,
    method: ThumbnailResizingMethod,
    animated: Boolean,
): Map<Keyword, Any?> =
    normalizeMediaHandle(
        scope,
        mediaService.getThumbnail(uri, width, height, method, animated).getOrThrow(),
    )

internal suspend fun temporaryMediaFile(
    platformMedia: PlatformMedia,
): Map<Keyword, Any?> {
    val temporaryFile =
        (platformMedia as? OkioPlatformMedia)
            ?.getTemporaryFile()
            ?.getOrThrow()
            ?: throw IllegalStateException("temporary-file requires okio-backed platform media")

    return mapOf(
        BridgeSchema.path to Paths.get(temporaryFile.path.toString()).toAbsolutePath(),
        BridgeSchema.raw to temporaryFile,
    )
}

internal fun deleteTemporaryMediaFile(temporaryFile: Any) {
    val okioTemporaryFile = temporaryFile as? OkioPlatformMedia.TemporaryFile
        ?: throw IllegalArgumentException("unsupported temporary-file value: ${temporaryFile::class.qualifiedName}")
    runBlocking { okioTemporaryFile.delete() }
}

internal fun requireMessageSpec(payload: KeywordMap, key: Keyword): MessageSpec {
    val raw = requireKeywordMap(payload, key)

    val replyTo = (raw[BridgeSchema.MessageSpec.replyTo] as? Map<*, *>)?.let {
        ReplyTarget(
            eventId = requireKeywordString(it, BridgeSchema.Event.eventId),
            relatesTo = (it[BridgeSchema.Event.relatesTo] as? Map<*, *>)?.let { relation ->
                RelationSpec(
                    type = requireKeywordString(relation, BridgeSchema.Relation.type),
                    eventId = requireKeywordString(relation, BridgeSchema.Relation.eventId),
                    key = optionalKeywordString(relation, BridgeSchema.Relation.key),
                    replyToEventId = optionalKeywordString(
                        relation,
                        BridgeSchema.Relation.replyToEventId,
                    ),
                    isFallingBack = relation[BridgeSchema.Relation.isFallingBack] as? Boolean,
                )
            },
        )
    }

    val kind = requireKeywordString(raw, BridgeSchema.MessageSpec.kind).removePrefix(":")
    val body = requireKeywordString(raw, BridgeSchema.MessageSpec.body)
    val format = optionalKeywordString(raw, BridgeSchema.MessageSpec.format)
    val formattedBody = optionalKeywordString(raw, BridgeSchema.MessageSpec.formattedBody)

    return when (kind) {
        "text" -> TextMessageSpec(
            body = body,
            format = format,
            formattedBody = formattedBody,
            replyTo = replyTo,
        )

        "emote" -> EmoteMessageSpec(
            body = body,
            format = format,
            formattedBody = formattedBody,
            replyTo = replyTo,
        )

        "audio" -> AudioMessageSpec(
            body = body,
            sourcePath = requireReadablePath(raw, BridgeSchema.MessageSpec.sourcePath),
            fileName = optionalKeywordString(raw, BridgeSchema.MessageSpec.fileName),
            mimeType = optionalKeywordContentType(raw, BridgeSchema.MessageSpec.mimeType),
            sizeBytes = optionalKeywordLong(raw, BridgeSchema.MessageSpec.sizeBytes),
            durationMillis = optionalKeywordDuration(raw, BridgeSchema.MessageSpec.duration)?.toMillis(),
            format = format,
            formattedBody = formattedBody,
            replyTo = replyTo,
        )

        "image" -> ImageMessageSpec(
            body = body,
            sourcePath = requireReadablePath(raw, BridgeSchema.MessageSpec.sourcePath),
            fileName = optionalKeywordString(raw, BridgeSchema.MessageSpec.fileName),
            mimeType = optionalKeywordContentType(raw, BridgeSchema.MessageSpec.mimeType),
            sizeBytes = optionalKeywordLong(raw, BridgeSchema.MessageSpec.sizeBytes),
            height = optionalKeywordInt(raw, BridgeSchema.MessageSpec.height),
            width = optionalKeywordInt(raw, BridgeSchema.MessageSpec.width),
            format = format,
            formattedBody = formattedBody,
            replyTo = replyTo,
        )

        "file" -> FileMessageSpec(
            body = body,
            sourcePath = requireReadablePath(raw, BridgeSchema.MessageSpec.sourcePath),
            fileName = optionalKeywordString(raw, BridgeSchema.MessageSpec.fileName),
            mimeType = optionalKeywordContentType(raw, BridgeSchema.MessageSpec.mimeType),
            sizeBytes = optionalKeywordLong(raw, BridgeSchema.MessageSpec.sizeBytes),
            format = format,
            formattedBody = formattedBody,
            replyTo = replyTo,
        )

        else -> error("unsupported message kind: $kind")
    }
}

internal fun requireStateEventSpec(payload: KeywordMap, key: Keyword): StateEventSpec {
    val raw = requireKeywordMap(payload, key)

    val type = requireKeywordString(raw, BridgeSchema.type)
    val stateKey = optionalKeywordString(raw, BridgeSchema.stateKey) ?: ""

    return when (type) {
        "m.room.name" -> RoomNameStateEventSpec(
            name = requireKeywordString(raw, BridgeSchema.name),
            stateKey = stateKey,
        )

        "m.room.topic" -> RoomTopicStateEventSpec(
            topic = requireKeywordString(raw, BridgeSchema.topic),
            stateKey = stateKey,
        )

        "m.room.avatar" -> RoomAvatarStateEventSpec(
            url = requireKeywordString(raw, BridgeSchema.url),
            stateKey = stateKey,
        )

        else -> throw IllegalArgumentException("unsupported state-event type: $type")
    }
}

internal suspend fun prepareUploadMedia(
    mediaService: MediaService,
    sourcePath: Path,
    mimeType: ContentType?,
): String =
    mediaService.prepareUploadMedia(byteArrayFlowFromPath(sourcePath), mimeType)

internal suspend fun uploadPreparedMedia(
    mediaService: MediaService,
    cacheUri: String,
    keepInCache: Boolean,
): String =
    mediaService.uploadMedia(cacheUri, keepMediaInCache = keepInCache).getOrThrow()

@ExperimentalCoroutinesApi
private class ProgressCallbackStateFlow(
    private val onProgress: (FileTransferProgress) -> Unit,
) : MutableStateFlow<FileTransferProgress?> {
    private val delegate = MutableStateFlow<FileTransferProgress?>(null)

    override var value: FileTransferProgress?
        get() = delegate.value
        set(value) {
            delegate.value = value
            value?.let(onProgress)
        }

    override val replayCache: List<FileTransferProgress?>
        get() = delegate.replayCache

    override val subscriptionCount
        get() = delegate.subscriptionCount

    override suspend fun collect(collector: kotlinx.coroutines.flow.FlowCollector<FileTransferProgress?>): Nothing =
        delegate.collect(collector)

    override suspend fun emit(value: FileTransferProgress?) {
        this.value = value
    }

    override fun compareAndSet(expect: FileTransferProgress?, update: FileTransferProgress?): Boolean {
        val changed = delegate.compareAndSet(expect, update)
        if (changed) update?.let(onProgress)
        return changed
    }

    override fun resetReplayCache() {
        delegate.resetReplayCache()
    }

    override fun tryEmit(value: FileTransferProgress?): Boolean {
        this.value = value
        return true
    }
}

internal suspend fun uploadPreparedMediaWithProgress(
    mediaService: MediaService,
    cacheUri: String,
    keepInCache: Boolean,
    onProgress: Any,
): String =
    mediaService.uploadMedia(
        cacheUri,
        progress = ProgressCallbackStateFlow { snapshot ->
            normalizeFileTransferProgress(snapshot)?.let {
                invokeCallbackSafely(onProgress, it)
            }
        },
        keepMediaInCache = keepInCache,
    ).getOrThrow()

internal fun relationFrom(spec: RelationSpec?): RelatesTo? =
    when (spec?.type) {
        null -> null
        "m.thread" -> RelatesTo.Thread(
            eventId = EventId(spec.eventId),
            replyTo = spec.replyToEventId?.let { RelatesTo.ReplyTo(EventId(it)) },
            isFallingBack = spec.isFallingBack,
        )

        "m.in_reply_to" -> RelatesTo.Reply(RelatesTo.ReplyTo(EventId(spec.eventId)))
        "m.reference" -> RelatesTo.Reference(EventId(spec.eventId))
        "m.annotation" -> RelatesTo.Annotation(EventId(spec.eventId), spec.key)
        else -> null
    }

@Suppress("UNCHECKED_CAST")
private fun invokeUntypedFunction1(callback: Function1<*, *>, value: Any?) {
    (callback as Function1<Any?, Any?>).invoke(value)
}

internal fun invokeCallback(callback: Any, value: Any?) {
    when (callback) {
        is IFn -> callback.invoke(value)
        is Function1<*, *> -> invokeUntypedFunction1(callback, value)
        else -> {
            val invokeMethod = callback.javaClass.methods.firstOrNull {
                it.name == "invoke" && it.parameterCount == 1
            } ?: throw IllegalArgumentException(
                "on-event callback ${callback.javaClass.name} does not expose invoke(arg)",
            )
            invokeMethod.invoke(callback, value)
        }
    }
}

internal fun invokeCallbackSafely(callback: Any, value: Any?) {
    try {
        invokeCallback(callback, value)
    } catch (_: Throwable) {
        // Bridge callback delivery is terminal; callback failures do not cascade.
    }
}

internal fun <T> submitBridgeTask(
    scope: CoroutineScope,
    onSuccess: Any,
    onFailure: Any,
    timeout: Duration? = null,
    block: suspend CoroutineScope.() -> T,
): Closeable {
    val job: Job = scope.launch {
        try {
            val result =
                if (timeout != null) withTimeout(timeout.toMillis()) { block() }
                else block()
            invokeCallbackSafely(onSuccess, result)
        } catch (error: TimeoutCancellationException) {
            invokeCallbackSafely(onFailure, error)
        } catch (error: CancellationException) {
            throw error
        } catch (error: Throwable) {
            invokeCallbackSafely(onFailure, error)
        }
    }
    return Closeable { job.cancel() }
}

internal fun createRepositoriesModule(databasePath: String): RepositoriesModule =
    RepositoriesModule.sqlite4clj(Path.of(databasePath).toAbsolutePath())

internal fun createMediaStoreModule(mediaPath: String): MediaStoreModule {
    val path = Path.of(mediaPath).toAbsolutePath()
    Files.createDirectories(path)
    return MediaStoreModule.okio(path.toString().toPath())
}
