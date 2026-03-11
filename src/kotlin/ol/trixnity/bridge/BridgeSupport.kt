package ol.trixnity.bridge

import clojure.lang.Keyword
import de.connect2x.trixnity.client.MatrixClient
import de.connect2x.trixnity.client.MediaStoreModule
import de.connect2x.trixnity.client.RepositoriesModule
import de.connect2x.trixnity.client.media.okio.okio
import de.connect2x.trixnity.core.model.EventId
import de.connect2x.trixnity.core.model.events.m.RelatesTo
import okio.Path.Companion.toPath
import java.nio.file.Files
import java.nio.file.Path
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

internal data class MessageSpec(
    val kind: String,
    val body: String,
    val format: String? = null,
    val formattedBody: String? = null,
    val replyTo: ReplyTarget? = null,
)

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

internal fun requireMessageSpec(payload: KeywordMap, key: Keyword): MessageSpec {
    val raw = requireKeywordValue(payload, key) as? Map<*, *>
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

    return MessageSpec(
        kind = requireKeywordString(raw, BridgeSchema.MessageSpec.kind),
        body = requireKeywordString(raw, BridgeSchema.MessageSpec.body),
        format = optionalKeywordString(raw, BridgeSchema.MessageSpec.format),
        formattedBody = optionalKeywordString(raw, BridgeSchema.MessageSpec.formattedBody),
        replyTo = replyTo,
    )
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

internal fun invokeCallback(callback: Any, event: Map<Keyword, Any?>) {
    when (callback) {
        is Function1<*, *> -> (callback as Function1<Any?, Any?>).invoke(event)
        else -> {
            val invokeMethod = callback.javaClass.methods.firstOrNull {
                it.name == "invoke" && it.parameterCount == 1
            } ?: throw IllegalArgumentException(
                "on-event callback ${callback.javaClass.name} does not expose invoke(arg)",
            )
            invokeMethod.invoke(callback, event)
        }
    }
}

internal fun createRepositoriesModule(databasePath: String): RepositoriesModule =
    RepositoriesModule.sqlite4clj(Path.of(databasePath).toAbsolutePath())

internal fun createMediaStoreModule(mediaPath: String): MediaStoreModule {
    val path = Path.of(mediaPath).toAbsolutePath()
    Files.createDirectories(path)
    return MediaStoreModule.okio(path.toString().toPath())
}
