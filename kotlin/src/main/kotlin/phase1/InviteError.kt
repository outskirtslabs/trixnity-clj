package phase1

fun isAlreadyInRoomInviteFailure(error: Throwable): Boolean =
    error.message?.contains("already in the room", ignoreCase = true) == true
