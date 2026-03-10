package phase1

import net.folivo.trixnity.core.model.EventId
import net.folivo.trixnity.core.model.UserId
import net.folivo.trixnity.core.model.events.m.ReactionEventContent
import net.folivo.trixnity.core.model.events.m.RelatesTo

data class MirroredReaction(
    val eventId: EventId,
    val key: String,
)

object BotLogic {
    fun mirroredBody(original: String): String = original.uppercase()

    fun shouldHandleSender(sender: UserId, botUserId: UserId): Boolean = sender != botUserId

    fun reactionToMirror(content: ReactionEventContent): MirroredReaction? {
        val relatesTo = content.relatesTo
        if (relatesTo is RelatesTo.Annotation) {
            val key = relatesTo.key ?: return null
            return MirroredReaction(relatesTo.eventId, key)
        }
        return null
    }
}
