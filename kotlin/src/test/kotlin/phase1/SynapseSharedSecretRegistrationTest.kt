package phase1

import kotlin.test.Test
import kotlin.test.assertEquals

class SynapseSharedSecretRegistrationTest {
    @Test
    fun `generateMac follows Synapse HMAC format for admin user`() {
        val mac = SynapseSharedSecretRegistration.generateMac(
            nonce = "thisisanonce",
            username = "pepper_roni",
            password = "pizza",
            admin = true,
            sharedSecret = "shared_secret",
            userType = null,
        )

        assertEquals("48715842ad67d5dc9a9ee938a3bda4fcfae8d7c7", mac)
    }

    @Test
    fun `generateMac appends user_type when provided`() {
        val mac = SynapseSharedSecretRegistration.generateMac(
            nonce = "n",
            username = "u",
            password = "p",
            admin = false,
            sharedSecret = "s",
            userType = "bot",
        )

        assertEquals("0b5f80dd2e57b2bc10ff27cfdb04303c72ca3d4c", mac)
    }
}
