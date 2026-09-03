package com.inframap.frontend.ui.topology.layout

import com.inframap.frontend.domain.model.TopologyEdge
import com.inframap.frontend.domain.model.TopologyGraph
import com.inframap.frontend.domain.model.TopologyNode
import kotlin.math.sqrt
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ForceDirectedLayoutTest {
    @Test
    fun emptyGraphReturnsEmptyMap() {
        val graph = TopologyGraph(nodes = emptyList(), edges = emptyList())
        val positions = ForceDirectedLayout.calculatePositions(graph)
        assertTrue(positions.isEmpty())
    }

    @Test
    fun singleNodeGraphReturnsCenteredOffset() {
        val graph =
            TopologyGraph(
                nodes = listOf(TopologyNode("n1", "r1", "router", "active")),
                edges = emptyList(),
            )
        val config = LayoutConfig(width = 1000f, height = 800f)
        val positions = ForceDirectedLayout.calculatePositions(graph, config)

        assertEquals(1, positions.size)
        val pos = positions["n1"]!!
        assertEquals(500f, pos.x)
        assertEquals(400f, pos.y)
    }

    @Test
    fun multiNodeGraphPositionsAllNodesWithinBounds() {
        val nodes =
            (1..5).map { i ->
                TopologyNode(id = "n$i", label = "dev-$i", deviceType = "switch", status = "active")
            }
        val edges =
            listOf(
                TopologyEdge("e1", "n1", "n2", "physical"),
                TopologyEdge("e2", "n2", "n3", "physical"),
                TopologyEdge("e3", "n3", "n4", "virtual"),
            )
        val graph = TopologyGraph(nodes = nodes, edges = edges)
        val config = LayoutConfig(width = 800f, height = 600f, margin = 40f)

        val positions = ForceDirectedLayout.calculatePositions(graph, config)

        assertEquals(5, positions.size)
        positions.forEach { (_, offset) ->
            assertTrue(offset.x >= 40f && offset.x <= 760f, "X ${offset.x} out of bounds")
            assertTrue(offset.y >= 40f && offset.y <= 560f, "Y ${offset.y} out of bounds")
        }
    }

    @Test
    fun nodeSeparationMaintainsMinimumDistanceBetweenNodes() {
        val graph =
            TopologyGraph(
                nodes =
                    listOf(
                        TopologyNode("n1", "r1", "router", "active"),
                        TopologyNode("n2", "r2", "router", "active"),
                    ),
                edges = emptyList(),
            )
        val positions = ForceDirectedLayout.calculatePositions(graph)

        val pos1 = positions["n1"]!!
        val pos2 = positions["n2"]!!
        val dx = pos1.x - pos2.x
        val dy = pos1.y - pos2.y
        val dist = sqrt(dx * dx + dy * dy)

        assertTrue(dist > 10f, "Nodes should be separated by repulsion force")
    }

    @Test
    fun containmentPullsAWorkloadCloserToItsHostThanANetworkLink() {
        // A workload does not merely connect to its host, it lives inside it, so the cluster
        // around a hypervisor has to be visibly tighter than a plain network link would make it.
        fun distanceFor(linkType: String): Float {
            val nodes =
                listOf(
                    TopologyNode(id = "host", label = "pve-node1", deviceType = "server", status = "active"),
                    TopologyNode(id = "workload", label = "vm-101", deviceType = "vm", status = "active"),
                    TopologyNode(id = "other", label = "printer", deviceType = "host", status = "active"),
                )
            val graph =
                TopologyGraph(
                    nodes = nodes,
                    edges = listOf(TopologyEdge(id = "e1", source = "host", target = "workload", linkType = linkType)),
                )
            val positions = ForceDirectedLayout.calculatePositions(graph)
            val host = positions.getValue("host")
            val workload = positions.getValue("workload")
            val dx = host.x - workload.x
            val dy = host.y - workload.y
            return sqrt(dx * dx + dy * dy)
        }

        assertTrue(
            distanceFor("hosted_on") < distanceFor("layer2_physical"),
            "a hosted_on edge must settle closer than a network link",
        )
    }
}
