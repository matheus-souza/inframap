package com.inframap.frontend.ui.topology

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class TopologyEdgeStyleTest {
    @Test
    fun everyBackendLinkTypeGetsItsOwnColour() {
        // These are the values the backend actually emits. They were previously matched
        // against short names no link type ever equals, so every edge fell through to the
        // manual colour and the legend was inert.
        val colours =
            listOf(
                "hosted_on",
                "layer2_physical",
                "virtual_hypervisor",
                "container_veth",
                "layer3_routed",
            ).map { getEdgeColor(it) }

        assertEquals(colours.size, colours.distinct().size, "each link type needs a distinct colour")

        val manual = getEdgeColor("manual")
        colours.forEach { assertNotEquals(manual, it, "no known link type may fall back to the manual colour") }
    }

    @Test
    fun linkTypeMatchingIsCaseInsensitive() {
        assertEquals(getEdgeColor("hosted_on"), getEdgeColor("HOSTED_ON"))
    }

    @Test
    fun unknownLinkTypesStillFallBackToManual() {
        assertEquals(getEdgeColor("manual"), getEdgeColor("something-new"))
    }

    @Test
    fun onlyContainmentIsDrawnDashed() {
        // Containment says a workload lives inside a host, not that packets flow between
        // them, so it must read differently from a network link.
        assertTrue(isContainmentLink("hosted_on"))
        assertTrue(isContainmentLink("Hosted_On"))

        listOf("layer2_physical", "layer3_routed", "virtual_hypervisor", "container_veth", "manual").forEach {
            assertFalse(isContainmentLink(it), "$it is a network link and must stay solid")
        }
    }
}
