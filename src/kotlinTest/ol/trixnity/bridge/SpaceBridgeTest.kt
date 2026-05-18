package ol.trixnity.bridge

import de.connect2x.trixnity.clientserverapi.model.room.GetHierarchy
import de.connect2x.trixnity.core.model.RoomAliasId
import de.connect2x.trixnity.core.model.RoomId
import de.connect2x.trixnity.core.model.UserId
import de.connect2x.trixnity.core.model.events.ClientEvent
import de.connect2x.trixnity.core.model.events.UnknownEventContent
import de.connect2x.trixnity.core.model.events.m.room.CreateEventContent
import de.connect2x.trixnity.core.model.events.m.room.JoinRulesEventContent
import de.connect2x.trixnity.core.model.events.m.space.ChildEventContent
import de.connect2x.trixnity.core.model.events.m.space.ParentEventContent
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class SpaceBridgeTest {
    @Test
    fun spaceChildAndParentStateEventMapsBuildExpectedUpstreamContent() {
        val child = requireStateEventSpec(
            mapOf(
                BridgeSchema.SendStateEventRequest.stateEvent to mapOf(
                    BridgeSchema.type to "m.space.child",
                    BridgeSchema.stateKey to "!child:example.org",
                    BridgeSchema.via to setOf("example.org", "backup.example.org"),
                    BridgeSchema.order to "a",
                    BridgeSchema.suggested to true,
                    BridgeSchema.externalUrl to "https://example.org/child",
                ),
            ),
            BridgeSchema.SendStateEventRequest.stateEvent,
        )
        val parent = requireStateEventSpec(
            mapOf(
                BridgeSchema.SendStateEventRequest.stateEvent to mapOf(
                    BridgeSchema.type to "m.space.parent",
                    BridgeSchema.stateKey to "!parent:example.org",
                    BridgeSchema.via to setOf("example.org"),
                    BridgeSchema.canonical to true,
                ),
            ),
            BridgeSchema.SendStateEventRequest.stateEvent,
        )

        assertEquals("!child:example.org", child.stateKey)
        val childContent = assertIs<ChildEventContent>(child.toEventContent())
        assertEquals(setOf("example.org", "backup.example.org"), childContent.via)
        assertEquals("a", childContent.order)
        assertEquals(true, childContent.suggested)
        assertEquals("https://example.org/child", childContent.externalUrl)

        assertEquals("!parent:example.org", parent.stateKey)
        val parentContent = assertIs<ParentEventContent>(parent.toEventContent())
        assertEquals(setOf("example.org"), parentContent.via)
        assertEquals(true, parentContent.canonical)
    }

    @Test
    fun relationRemovalUsesEmptyUnknownStateContent() {
        val child = emptyStateEventContent("m.space.child")
        val parent = emptyStateEventContent("m.space.parent")

        assertIs<UnknownEventContent>(child)
        assertEquals("m.space.child", child.eventType)
        assertEquals(0, child.raw.size)
        assertEquals(0, child.blocks.size)

        assertIs<UnknownEventContent>(parent)
        assertEquals("m.space.parent", parent.eventType)
        assertEquals(0, parent.raw.size)
        assertEquals(0, parent.blocks.size)
    }

    @Test
    fun normalizeSpaceRelationContentReturnsNamespacedKeywordMaps() {
        val child = normalizeSpaceChildContent(
            ChildEventContent(
                via = setOf("example.org"),
                order = "a",
                suggested = true,
                externalUrl = "https://example.org/child",
            ),
        )
        val parent = normalizeSpaceParentContent(
            ParentEventContent(
                via = setOf("example.org"),
                canonical = true,
            ),
        )

        assertEquals(
            mapOf(
                BridgeSchema.via to setOf("example.org"),
                BridgeSchema.order to "a",
                BridgeSchema.suggested to true,
                BridgeSchema.externalUrl to "https://example.org/child",
                BridgeSchema.raw to child[BridgeSchema.raw],
            ),
            child,
        )
        assertEquals(
            mapOf(
                BridgeSchema.via to setOf("example.org"),
                BridgeSchema.canonical to true,
                BridgeSchema.raw to parent[BridgeSchema.raw],
            ),
            parent,
        )
    }

    @Test
    fun normalizeHierarchyResponseReturnsPaginatedSpaceRooms() {
        val childState = ClientEvent.StrippedStateEvent(
            content = ChildEventContent(via = setOf("example.org"), suggested = true),
            sender = UserId("@alice:example.org"),
            stateKey = "!child:example.org",
        )
        val response = GetHierarchy.Response(
            nextBatch = "batch-2",
            rooms = listOf(
                GetHierarchy.Response.SpaceHierarchyRoomsChunk(
                    allowedRoomIds = setOf(RoomId("!allowed:example.org")),
                    avatarUrl = "mxc://example.org/avatar",
                    canonicalAlias = RoomAliasId("#project:example.org"),
                    childrenState = setOf(childState),
                    guestCanJoin = false,
                    joinRule = JoinRulesEventContent.JoinRule.Public,
                    name = "Project",
                    joinedMembersCount = 3,
                    roomId = RoomId("!space:example.org"),
                    roomType = CreateEventContent.RoomType.Space,
                    roomVersion = "11",
                    topic = "Coordination",
                    worldReadable = true,
                ),
            ),
        )

        val normalized = normalizeHierarchyResponse(response)

        assertEquals("batch-2", normalized[BridgeSchema.nextBatch])
        val rooms = assertIs<List<*>>(normalized[BridgeSchema.rooms])
        val room = assertIs<Map<*, *>>(rooms.single())
        assertEquals(
            mapOf(
                BridgeSchema.allowedRoomIds to setOf("!allowed:example.org"),
                BridgeSchema.avatarUrl to "mxc://example.org/avatar",
                BridgeSchema.canonicalAlias to "#project:example.org",
                BridgeSchema.childrenState to room[BridgeSchema.childrenState],
                BridgeSchema.guestCanJoin to false,
                BridgeSchema.joinRule to "public",
                BridgeSchema.name to "Project",
                BridgeSchema.joinedMembersCount to 3L,
                BridgeSchema.roomId to "!space:example.org",
                BridgeSchema.roomType to "m.space",
                BridgeSchema.roomVersion to "11",
                BridgeSchema.topic to "Coordination",
                BridgeSchema.worldReadable to true,
                BridgeSchema.raw to room[BridgeSchema.raw],
            ),
            room,
        )
        val children = assertIs<List<*>>(room[BridgeSchema.childrenState])
        val child = assertIs<Map<*, *>>(children.single())
        assertEquals("m.space.child", child[BridgeSchema.type])
        assertEquals("!child:example.org", child[BridgeSchema.stateKey])
        assertEquals(
            mapOf(
                BridgeSchema.via to setOf("example.org"),
                BridgeSchema.suggested to true,
                BridgeSchema.raw to (child[BridgeSchema.content] as Map<*, *>)[BridgeSchema.raw],
            ),
            child[BridgeSchema.content],
        )
    }
}
