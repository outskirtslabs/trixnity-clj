package phase1

import net.folivo.trixnity.core.model.EventId
import net.folivo.trixnity.core.model.UserId
import net.folivo.trixnity.core.model.events.m.ReactionEventContent
import net.folivo.trixnity.core.model.events.m.RelatesTo
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class BotLogicTest {
    @Test
    fun `mirroredBody uppercases text`() {
        assertEquals("HELLO MATRIX", BotLogic.mirroredBody("Hello Matrix"))
    }

    @Test
    fun `shouldHandleSender ignores bot's own events`() {
        val botUser = UserId("@bot:example.org")
        assertFalse(BotLogic.shouldHandleSender(botUser, botUser))
        assertTrue(BotLogic.shouldHandleSender(UserId("@alice:example.org"), botUser))
    }

    @Test
    fun `reactionToMirror returns target event id and emoji key`() {
        val content = ReactionEventContent(
            relatesTo = RelatesTo.Annotation(
                eventId = EventId("\$event:example.org"),
                key = "👍",
            ),
        )

        val mirrored = BotLogic.reactionToMirror(content)
        assertNotNull(mirrored)
        assertEquals(EventId("\$event:example.org"), mirrored.eventId)
        assertEquals("👍", mirrored.key)
    }

    @Test
    fun `reactionToMirror returns null when reaction has no annotation relation`() {
        val content = ReactionEventContent(relatesTo = null)
        assertNull(BotLogic.reactionToMirror(content))
    }
}
