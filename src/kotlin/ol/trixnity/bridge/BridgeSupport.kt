package ol.trixnity.bridge

import clojure.lang.IFn
import clojure.lang.Keyword
import de.connect2x.trixnity.client.MatrixClient
import de.connect2x.trixnity.client.MediaStoreModule
import de.connect2x.trixnity.client.RepositoriesModule
import de.connect2x.trixnity.client.media.okio.okio
import de.connect2x.trixnity.core.model.EventId
import de.connect2x.trixnity.core.model.events.m.RelatesTo
import de.connect2x.trixnity.utils.ByteArrayFlow
import de.connect2x.trixnity.utils.byteArrayFlowFromInputStream
import io.ktor.http.ContentType
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import okio.Path.Companion.toPath
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

internal fun optionalKeywordDuration(payload: KeywordMap, key: Keyword): Duration? =
    payload[key] as? Duration

internal fun optionalKeywordLong(payload: Map<*, *>, key: Keyword): Long? =
    (payload[key] as? Number)?.toLong()

internal fun optionalKeywordInt(payload: Map<*, *>, key: Keyword): Int? =
    (payload[key] as? Number)?.toInt()

internal fun parseContentType(value: String, key: Keyword): ContentType =
    try {
        ContentType.parse(value)
    } catch (error: Throwable) {
        throw IllegalArgumentException("invalid MIME type for $key: $value", error)
    }

internal fun optionalKeywordContentType(payload: Map<*, *>, key: Keyword): ContentType? =
    optionalKeywordString(payload, key)?.let { parseContentType(it, key) }

internal fun requireReadablePath(payload: Map<*, *>, key: Keyword): Path {
    val path = try {
        Paths.get(requireKeywordString(payload, key)).toAbsolutePath().normalize()
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

internal fun requireMessageSpec(payload: KeywordMap, key: Keyword): MessageSpec {
    val raw = requireKeywordValue(payload, key) as? KeywordMap
        ?: throw IllegalArgumentException("request payload is missing message map under $key")

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

internal fun invokeCallback(callback: Any, value: Any?) {
    when (callback) {
        is IFn -> callback.invoke(value)
        is Function1<*, *> -> (callback as Function1<Any?, Any?>).invoke(value)
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
