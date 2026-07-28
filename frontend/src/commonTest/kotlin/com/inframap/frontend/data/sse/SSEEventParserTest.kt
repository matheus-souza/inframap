package com.inframap.frontend.data.sse

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class SSEEventParserTest {
    @Test
    fun discoveryProgressParsesCorrectly() {
        val event = SSEEventParser.parse("discovery.progress", "evt-1", """{"percent":50}""")
        assertIs<SSEEvent.DiscoveryProgress>(event)
        assertEquals("evt-1", event.id)
        assertEquals("""{"percent":50}""", event.data)
    }

    @Test
    fun topologyUpdatedParsesCorrectly() {
        val event = SSEEventParser.parse("topology.updated", "evt-2", """{"nodes":10}""")
        assertIs<SSEEvent.TopologyUpdated>(event)
        assertEquals("evt-2", event.id)
        assertEquals("""{"nodes":10}""", event.data)
    }

    @Test
    fun systemNotificationParsesCorrectly() {
        val event = SSEEventParser.parse("system.notification", "evt-3", """{"msg":"hello"}""")
        assertIs<SSEEvent.SystemNotification>(event)
        assertEquals("evt-3", event.id)
        assertEquals("""{"msg":"hello"}""", event.data)
    }

    @Test
    fun deviceCreatedParsesCorrectly() {
        val event = SSEEventParser.parse("device.created", "evt-4", """{"hostname":"srv1"}""")
        assertIs<SSEEvent.DeviceCreated>(event)
        assertEquals("evt-4", event.id)
        assertEquals("""{"hostname":"srv1"}""", event.data)
    }

    @Test
    fun deviceUpdatedParsesCorrectly() {
        val event = SSEEventParser.parse("device.updated", "evt-5", """{"hostname":"srv1"}""")
        assertIs<SSEEvent.DeviceUpdated>(event)
        assertEquals("evt-5", event.id)
        assertEquals("""{"hostname":"srv1"}""", event.data)
    }

    @Test
    fun unknownEventTypeFallsToUnknown() {
        val event = SSEEventParser.parse("custom.event", "evt-6", """{"key":"val"}""")
        assertIs<SSEEvent.Unknown>(event)
        assertEquals("evt-6", event.id)
        assertEquals("custom.event", event.type)
        assertEquals("""{"key":"val"}""", event.data)
    }

    @Test
    fun emptyEventIdIsPreserved() {
        val event = SSEEventParser.parse("device.created", "", """{}""")
        assertIs<SSEEvent.DeviceCreated>(event)
        assertEquals("", event.id)
    }

    @Test
    fun emptyDataIsPreserved() {
        val event = SSEEventParser.parse("topology.updated", "evt-7", "")
        assertIs<SSEEvent.TopologyUpdated>(event)
        assertEquals("", event.data)
    }

    @Test
    fun connectedEventHasDefaultEmptyId() {
        val event = SSEEvent.Connected()
        assertEquals("", event.id)
    }

    @Test
    fun disconnectedEventHasDefaultEmptyId() {
        val event = SSEEvent.Disconnected()
        assertEquals("", event.id)
    }

    @Test
    fun connectedEventWithCustomId() {
        val event = SSEEvent.Connected(id = "custom-id")
        assertEquals("custom-id", event.id)
    }

    @Test
    fun disconnectedEventWithCustomId() {
        val event = SSEEvent.Disconnected(id = "custom-id")
        assertEquals("custom-id", event.id)
    }

    @Test
    fun emptyTypeStringFallsToUnknown() {
        val event = SSEEventParser.parse("", "evt-8", """{"data":true}""")
        assertIs<SSEEvent.Unknown>(event)
        assertEquals("", event.type)
    }

    @Test
    fun allKnownEventTypesAreCovered() {
        val knownTypes =
            mapOf(
                "discovery.progress" to SSEEvent.DiscoveryProgress::class,
                "topology.updated" to SSEEvent.TopologyUpdated::class,
                "system.notification" to SSEEvent.SystemNotification::class,
                "device.created" to SSEEvent.DeviceCreated::class,
                "device.updated" to SSEEvent.DeviceUpdated::class,
            )

        knownTypes.forEach { (type, expectedClass) ->
            val event = SSEEventParser.parse(type, "id", "data")
            assertEquals(
                expectedClass,
                event::class,
                "Expected $expectedClass for type '$type' but got ${event::class}",
            )
        }
    }
}
