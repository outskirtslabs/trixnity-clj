package phase1

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class InviteErrorTest {
    @Test
    fun `already-in-room errors are treated as expected`() {
        val error = RuntimeException("statusCode=403 Forbidden errorResponse=Forbidden(error=@ramblurr:outskirtslabs.com is already in the room.)")
        assertTrue(isAlreadyInRoomInviteFailure(error))
    }

    @Test
    fun `other errors are not treated as expected`() {
        val error = RuntimeException("statusCode=500 Internal Server Error")
        assertFalse(isAlreadyInRoomInviteFailure(error))
    }
}
