package com.inframap.frontend.ui.topology.layout

import androidx.compose.ui.geometry.Offset
import com.inframap.frontend.domain.model.TopologyEdge
import com.inframap.frontend.domain.model.TopologyGraph
import com.inframap.frontend.domain.model.TopologyNode
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

data class LayoutConfig(
    val width: Float = 1000f,
    val height: Float = 800f,
    val iterations: Int = 100,
    val margin: Float = 50f,
)

object ForceDirectedLayout {
    private const val MIN_DISTANCE = 1f

    fun calculatePositions(
        graph: TopologyGraph,
        config: LayoutConfig = LayoutConfig(),
    ): Map<String, Offset> {
        val nodes = graph.nodes
        return when {
            nodes.isEmpty() -> emptyMap()
            nodes.size == 1 -> mapOf(nodes.first().id to Offset(config.width / 2f, config.height / 2f))
            else -> computeMultiNodePositions(graph, nodes, config)
        }
    }

    private fun computeMultiNodePositions(
        graph: TopologyGraph,
        nodes: List<TopologyNode>,
        config: LayoutConfig,
    ): Map<String, Offset> {
        val width = config.width
        val height = config.height
        val margin = config.margin
        val nodeCount = nodes.size

        val area = (width - 2 * margin) * (height - 2 * margin)
        val k = sqrt(area / nodeCount)

        val positions = initializeCircularPositions(nodes, width, height)
        var temp = width / 10f

        repeat(config.iterations) {
            val displacements = mutableMapOf<String, Offset>()
            nodes.forEach { node -> displacements[node.id] = Offset.Zero }

            calculateRepulsion(nodes, positions, displacements, k)
            calculateAttraction(graph.edges, positions, displacements, k)
            applyDisplacements(nodes, positions, displacements, temp, config)

            temp *= 0.95f
        }

        return positions
    }

    private fun initializeCircularPositions(
        nodes: List<TopologyNode>,
        width: Float,
        height: Float,
    ): MutableMap<String, Offset> {
        val centerX = width / 2f
        val centerY = height / 2f
        val radius = min(width, height) / 3f
        val positions = mutableMapOf<String, Offset>()

        nodes.forEachIndexed { index, node ->
            val angle = 2.0 * Math.PI * index / nodes.size
            val x = centerX + (radius * cos(angle)).toFloat()
            val y = centerY + (radius * sin(angle)).toFloat()
            positions[node.id] = Offset(x, y)
        }
        return positions
    }

    private fun calculateRepulsion(
        nodes: List<TopologyNode>,
        positions: Map<String, Offset>,
        displacements: MutableMap<String, Offset>,
        k: Float,
    ) {
        val nodeCount = nodes.size
        for (i in 0 until nodeCount) {
            val posA = positions[nodes[i].id] ?: continue
            for (j in i + 1 until nodeCount) {
                val posB = positions[nodes[j].id] ?: continue

                var deltaX = posA.x - posB.x
                var deltaY = posA.y - posB.y
                var dist = sqrt(deltaX * deltaX + deltaY * deltaY)
                if (dist < MIN_DISTANCE) {
                    deltaX = MIN_DISTANCE
                    deltaY = MIN_DISTANCE
                    dist = sqrt(2f)
                }

                val force = (k * k) / dist
                val dispX = (deltaX / dist) * force
                val dispY = (deltaY / dist) * force

                displacements[nodes[i].id] = displacements[nodes[i].id]!! + Offset(dispX, dispY)
                displacements[nodes[j].id] = displacements[nodes[j].id]!! - Offset(dispX, dispY)
            }
        }
    }

    private fun calculateAttraction(
        edges: List<TopologyEdge>,
        positions: Map<String, Offset>,
        displacements: MutableMap<String, Offset>,
        k: Float,
    ) {
        edges.forEach { edge ->
            val posSource = positions[edge.source]
            val posTarget = positions[edge.target]
            if (posSource != null && posTarget != null && edge.source != edge.target) {
                var deltaX = posSource.x - posTarget.x
                var deltaY = posSource.y - posTarget.y
                var dist = sqrt(deltaX * deltaX + deltaY * deltaY)
                if (dist < MIN_DISTANCE) {
                    deltaX = MIN_DISTANCE
                    deltaY = MIN_DISTANCE
                    dist = sqrt(2f)
                }

                val force = (dist * dist) / k
                val dispX = (deltaX / dist) * force
                val dispY = (deltaY / dist) * force

                displacements[edge.source] = displacements[edge.source]!! - Offset(dispX, dispY)
                displacements[edge.target] = displacements[edge.target]!! + Offset(dispX, dispY)
            }
        }
    }

    private fun applyDisplacements(
        nodes: List<TopologyNode>,
        positions: MutableMap<String, Offset>,
        displacements: Map<String, Offset>,
        temp: Float,
        config: LayoutConfig,
    ) {
        nodes.forEach { node ->
            val pos = positions[node.id]!!
            val disp = displacements[node.id]!!
            val dispLen = sqrt(disp.x * disp.x + disp.y * disp.y)
            if (dispLen > 0.001f) {
                val limitedDispLen = min(dispLen, temp)
                val deltaX = (disp.x / dispLen) * limitedDispLen
                val deltaY = (disp.y / dispLen) * limitedDispLen
                val newX = (pos.x + deltaX).coerceIn(config.margin, config.width - config.margin)
                val newY = (pos.y + deltaY).coerceIn(config.margin, config.height - config.margin)
                positions[node.id] = Offset(newX, newY)
            }
        }
    }
}
