# Matrix Client Applications

This context names the Matrix concepts that `trixnity-clj` exposes to Clojure applications and bots. It distinguishes protocol identities, room structures, event history, delivery, media, and encryption workflows.

## Identity and connectivity

**Homeserver**:
A Matrix server that hosts user accounts and room data and participates in federation.
_Avoid_: Matrix server, backend

**Matrix user**:
A person, service, or bot identity on Matrix, identified by a user ID.
_Avoid_: Account, room user

**User ID**:
The globally qualified identifier for a Matrix user, such as `@bot:example.org`.
_Avoid_: UID, username

**Device**:
A separately identified login endpoint for a Matrix user, with its own device ID and encryption keys.
_Avoid_: Client, session

**Matrix client**:
An active Matrix endpoint logged in as one user and device and maintaining synchronized state with a homeserver.
_Avoid_: Bot, device

**Bot**:
An automated application acting through a Matrix user and Matrix client. A bot is an application role, not a separate Matrix identity type.
_Avoid_: Client, user

**Profile**:
A Matrix user's public display name and avatar metadata.
_Avoid_: Account, presence

**Presence**:
A Matrix user's advertised availability and recent activity state.
_Avoid_: Profile, membership

## Rooms and access

**Room**:
A persistent Matrix container for events and state. A room may represent a conversation, a space, or another collaborative context.
_Avoid_: Chat, channel

**Room ID**:
The stable, globally qualified identifier for a room, such as `!opaque:example.org`.
_Avoid_: Room alias, room name

**Room alias**:
A human-readable address, such as `#support:example.org`, that resolves to a room and may later be repointed.
_Avoid_: Room ID, room name

**Room member**:
A Matrix user viewed through that user's membership in a room.
_Avoid_: Room user, participant

**Membership**:
The Matrix state describing a user's relationship to a room, such as invite, join, leave, ban, or knock.
_Avoid_: Presence, permission

**Direct chat**:
A user's account-data association between another user and one or more rooms. It is not an intrinsic room type or a guarantee that only two users belong to the room.
_Avoid_: Direct room, private room

**Power level**:
A room-scoped integer that controls a member's authority and the thresholds for room actions and event types.
_Avoid_: Role, trust level, permission level

## Events and history

**Event**:
A Matrix data unit identified by a type and carrying type-specific content. Persisted room events are immutable and identify their sender.
_Avoid_: Message, notification

**Event ID**:
The homeserver-assigned identifier for a persisted room event.
_Avoid_: Transaction ID

**Room event**:
An event associated with a room, including message events and state events.
_Avoid_: Message

**Message**:
A room event that carries user-facing message content, such as text, an emote, or an attachment.
_Avoid_: Event, chat

**State event**:
A room event identified by its event type and state key whose latest value contributes to current room state.
_Avoid_: Status event, message

**State key**:
The second coordinate, alongside event type, that identifies one entry in room state. Many state events use the empty string; member and space relations use an identifier.
_Avoid_: Event ID, map key

**Room state**:
The current set of state events keyed by event type and state key.
_Avoid_: Room status, account data

**Timeline event**:
A room event presented in the client-visible history of a room. State events can also appear as timeline events.
_Avoid_: Message

**Timeline**:
A client's ordered view of room history, which may contain gaps when events are not available locally.
_Avoid_: Event stream, chat log

**Timeline gap**:
A missing interval in the locally available timeline that the client may fetch from a homeserver.
_Avoid_: Sync gap, missing event

**Event relation**:
Metadata connecting one event to another for semantics such as replies, reactions, replacements, or threads.
_Avoid_: Space relation, room relation

**Reply**:
A message relation that identifies the event being answered.
_Avoid_: Reaction, thread

**Reaction**:
An annotation relation whose key, often an emoji, is applied to another event.
_Avoid_: Reply, message

**Redaction**:
A Matrix operation that strips nonessential content from an existing event while retaining the event in room history.
_Avoid_: Delete, removal

**Account data**:
Private per-user data stored globally or for one room; it does not contribute to shared room state.
_Avoid_: Room state, profile

**Receipt**:
An ephemeral per-user signal that records a receipt type at a room event, such as a public or private read receipt.
_Avoid_: Read marker, notification

## Delivery and media

**Sync**:
The incremental exchange through which a Matrix client receives room, account, device, presence, and to-device updates from its homeserver.
_Avoid_: Poll, timeline

**Outbox entry**:
A locally queued room send that remains pending until it succeeds, fails, is cancelled, or is reconciled by sync.
_Avoid_: Event, draft

**Transaction ID**:
A client-generated identifier used to make a send idempotent and to track it before the homeserver assigns an event ID.
_Avoid_: Event ID

**Media**:
Binary content stored through Matrix media services and referenced independently from the event that uses it.
_Avoid_: Attachment, file path

**MXC URI**:
A `mxc://` content identifier for Matrix media. It identifies content but is not itself an HTTP download URL.
_Avoid_: URL, file path

**Attachment**:
A message whose content refers to media and carries presentation metadata such as a file name, MIME type, dimensions, or duration.
_Avoid_: Media, upload

**Encrypted-file metadata**:
The cryptographic key, initialization vector, hashes, and media reference needed to download and decrypt encrypted media.
_Avoid_: MXC URI, attachment

**Thumbnail**:
A reduced representation of media, either referenced by an event or generated by a homeserver for requested dimensions.
_Avoid_: Attachment, preview event

## Spaces

**Space**:
A room with the Matrix space type that organizes rooms and other spaces through explicit relations.
_Avoid_: Folder, room group

**Subspace**:
A space used as the child of another space. It is a relationship role, not a distinct Matrix room type.
_Avoid_: Child room, nested folder

**Space child relation**:
A state relation declared by a parent space for a child room or subspace. It is independent of any parent relation declared by the child.
_Avoid_: Parent relation, membership

**Space parent relation**:
A state relation declared by a room or subspace for a parent space. It is independent of any child relation declared by the parent.
_Avoid_: Child relation, membership

**Space hierarchy**:
The directed structure reachable through valid space relations, potentially spanning rooms and subspaces.
_Avoid_: Folder tree, room list

## Notifications and read state

**Notification**:
A client-visible alert derived from an event and the user's notification rules.
_Avoid_: Event, unread state

**Read marker**:
A private per-user room position stored as account data that records the fully read point.
_Avoid_: Receipt, unread flag

**Unread state**:
The state indicating that a room should be presented as unread, independently of whether individual notifications remain visible.
_Avoid_: Notification, membership

**Dismissal**:
Removal of a notification from the client's active notification set without changing the underlying event.
_Avoid_: Redaction, mark read

## Encryption and trust

**End-to-end encryption**:
Encryption that protects room-event content between participating devices so homeservers transport ciphertext rather than plaintext.
_Avoid_: Transport encryption, key backup

**Verification**:
An interactive process that establishes trust in device keys or another user's cross-signing identity.
_Avoid_: Authentication, login

**SAS verification**:
A verification method in which both sides compare a short authentication string, usually rendered as emoji or decimal numbers.
_Avoid_: Password verification, UIA

**Cross-signing**:
A Matrix trust structure in which user-controlled signing keys establish trust across a user's devices and between users.
_Avoid_: Device verification, key backup

**Trust level**:
A client's assessment of confidence in a user, device, or event sender's cryptographic identity.
_Avoid_: Power level, membership

**Key backup**:
An encrypted server-side backup of room decryption keys, organized under a backup version.
_Avoid_: Cross-signing, database backup

**Recovery key**:
Secret recovery material used to unlock encrypted Matrix account secrets or key backup data for the workflow that produced it.
_Avoid_: Password, device key

**User-interactive authentication (UIA)**:
A homeserver challenge that requires one or more authentication stages before a sensitive account operation can proceed.
_Avoid_: Verification, login state
