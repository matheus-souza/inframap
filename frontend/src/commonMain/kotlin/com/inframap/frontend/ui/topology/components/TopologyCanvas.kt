package com.inframap.frontend.ui.topology.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.input.pointer.pointerInput
import com.inframap.frontend.domain.model.TopologyEdge
import com.inframap.frontend.domain.model.TopologyNode
import com.inframap.frontend.ui.topology.TopologyActions
import com.inframap.frontend.ui.topology.TopologyState
import kotlin.math.sqrt

private val PhysicalColor = Color(0xFF8be9fd)
private val VirtualColor = Color(0xFFbd93f9)
private val ContainerColor = Color(0xFF50fa7b)
private val RoutedColor = Color(0xFFffb86c)
private val ManualColor = Color(0xFF6272a4)

private val RouterColor = Color(0xFFbd93f9)
private val SwitchColor = Color(0xFF50fa7b)
private val ServerColor = Color(0xFFffb86c)
private val FirewallColor = Color(0xFFff5555)
private val DefaultNodeColor = Color(0xFF8be9fd)

private const val NODE_RADIUS = 24f
private const val HIT_TEST_RADIUS = 32f

@Composable
fun TopologyCanvas(
    state: TopologyState,
    actions: TopologyActions,
    modifier: Modifier = Modifier,
) {
    val graph = state.graph ?: return
    val positions = state.nodePositions
    val panOffset = state.panOffset
    val zoomScale = state.zoomScale
    val selectedNodeId = state.selectedNode?.id

    Canvas(
        modifier =
            modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    detectTransformGestures { _, pan, zoom, _ ->
                        if (zoom != 1.0f) {
                            actions.onZoom(zoom)
                        }
                        if (pan != Offset.Zero) {
                            actions.onPan(pan)
                        }
                    }
                }.pointerInput(Unit) {
                    detectDragGestures { change, dragAmount ->
                        change.consume()
                        actions.onPan(dragAmount)
                    }
                }.pointerInput(graph, positions, panOffset, zoomScale) {
                    detectTapGestures { tapOffset ->
                        val graphX = (tapOffset.x - panOffset.x) / zoomScale
                        val graphY = (tapOffset.y - panOffset.y) / zoomScale
                        val clickedNodeId = findClickedNodeId(Offset(graphX, graphY), positions)
                        actions.onNodeSelected(clickedNodeId)
                    }
                },
    ) {
        withTransform({
            translate(panOffset.x, panOffset.y)
            scale(zoomScale, zoomScale, pivot = Offset.Zero)
        }) {
            drawTopologyEdges(graph.edges, positions, zoomScale)
            drawTopologyNodes(graph.nodes, positions, selectedNodeId, zoomScale)
        }
    }
}

private fun DrawScope.drawTopologyEdges(
    edges: List<TopologyEdge>,
    positions: Map<String, Offset>,
    zoomScale: Float,
) {
    edges.forEach { edge ->
        val sourcePos = positions[edge.source]
        val targetPos = positions[edge.target]
        if (sourcePos != null && targetPos != null) {
            val edgeColor = getEdgeColor(edge.linkType)
            drawLine(
                color = edgeColor,
                start = sourcePos,
                end = targetPos,
                strokeWidth = 2f / zoomScale,
            )
        }
    }
}

private fun DrawScope.drawTopologyNodes(
    nodes: List<TopologyNode>,
    positions: Map<String, Offset>,
    selectedNodeId: String?,
    zoomScale: Float,
) {
    nodes.forEach { node ->
        val pos = positions[node.id] ?: return@forEach
        val isSelected = node.id == selectedNodeId
        val nodeColor = getNodeColor(node.deviceType)

        if (isSelected) {
            drawCircle(
                color = Color.White,
                radius = (NODE_RADIUS + 6f),
                center = pos,
                style = Stroke(width = 3f / zoomScale),
            )
        }

        drawCircle(
            color = nodeColor,
            radius = NODE_RADIUS,
            center = pos,
        )
    }
}

private fun getEdgeColor(linkType: String): Color =
    when (linkType.lowercase()) {
        "physical" -> PhysicalColor
        "virtual" -> VirtualColor
        "container" -> ContainerColor
        "routed" -> RoutedColor
        "manual" -> ManualColor
        else -> ManualColor
    }

private fun getNodeColor(deviceType: String): Color =
    when (deviceType.lowercase()) {
        "router" -> RouterColor
        "switch" -> SwitchColor
        "server" -> ServerColor
        "firewall" -> FirewallColor
        else -> DefaultNodeColor
    }

private fun findClickedNodeId(
    clickPoint: Offset,
    positions: Map<String, Offset>,
): String? {
    var clickedNodeId: String? = null
    var minDistance = Float.MAX_VALUE

    positions.forEach { (nodeId, pos) ->
        val dx = pos.x - clickPoint.x
        val dy = pos.y - clickPoint.y
        val dist = sqrt(dx * dx + dy * dy)
        if (dist <= HIT_TEST_RADIUS && dist < minDistance) {
            minDistance = dist
            clickedNodeId = nodeId
        }
    }
    return clickedNodeId
}
