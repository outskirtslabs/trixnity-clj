package phase1

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class BotConfigLoaderTest {
    @Test
    fun `loads shared secret registration settings`() {
        val config = BotConfigLoader.load(
            mapOf(
                "MATRIX_HOMESERVER_URL" to "https://matrix.example.org",
                "MATRIX_BOT_USERNAME" to "bot",
                "MATRIX_BOT_PASSWORD" to "pw",
                "MATRIX_REGISTRATION_SHARED_SECRET" to "secret-123",
                "MATRIX_BOT_ADMIN" to "true",
            ),
        )

        assertEquals("secret-123", config.registrationSharedSecret)
        assertTrue(config.botAdmin)
    }
}
