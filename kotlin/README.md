# Phase 1 Kotlin Reference Bot

This directory is a standalone Kotlin/JVM Gradle project for Phase 1.

## Behavior

- Connects to a Matrix homeserver.
- Tries to register the bot user first (unless disabled).
- Falls back to password login when registration fails.
- Creates a room with encryption enabled (`m.room.encryption`).
- Prints the room name and room id.
- Mirrors new text messages in ALL CAPS as replies.
- Duplicates emoji reactions.
- Ignores bot-authored events to avoid loops.

## Environment variables

- `MATRIX_HOMESERVER_URL` (default: `http://localhost:8008`)
- `MATRIX_BOT_USERNAME` (default: `trixnitycljbot`)
- `MATRIX_BOT_PASSWORD` (default: `password!123`)
- `MATRIX_REGISTRATION_SHARED_SECRET` (optional; if set, uses Synapse admin shared-secret registration API)
- `MATRIX_BOT_ADMIN` (optional; set `true` to create admin user in shared-secret mode)
- `MATRIX_ROOM_NAME` (default: `trixnity-clj-bot-room-<timestamp>`)
- `MATRIX_ROOM_ID_FILE` (default: `./kotlin/.bot-state/room-id.txt`; reused across restarts to avoid new room creation)
- `MATRIX_INVITE_USER` (optional, full user id like `@alice:example.org`)
- `MATRIX_TRY_REGISTER` (`false` to skip register flow; default is enabled)
- `MATRIX_MEDIA_PATH` (default: `./kotlin/.bot-media`)
- `MATRIX_DB_PATH` (default: `./kotlin/.bot-state/trixnity`)

## Run

```bash
nix develop -c bash -lc 'cd kotlin && gradle test --no-daemon'
nix develop -c bash -lc 'cd kotlin && gradle run --no-daemon'
```

## Example

```bash
MATRIX_HOMESERVER_URL="https://your-homeserver" \
MATRIX_BOT_USERNAME="your-bot" \
MATRIX_BOT_PASSWORD="your-password" \
nix develop -c bash -lc 'cd kotlin && gradle run --no-daemon'
```

Shared-secret mode (for Synapse with public registration disabled):

```bash
MATRIX_HOMESERVER_URL="https://your-homeserver" \
MATRIX_BOT_USERNAME="your-bot" \
MATRIX_BOT_PASSWORD="your-password" \
MATRIX_REGISTRATION_SHARED_SECRET="your-shared-secret" \
nix develop -c bash -lc 'cd kotlin && gradle run --no-daemon'
```
