package com.inframap.frontend.ui.topology

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import com.inframap.frontend.designsystem.InfraMapBorder
import com.inframap.frontend.designsystem.InfraMapCanvasBg
import com.inframap.frontend.designsystem.InfraMapCyan
import com.inframap.frontend.designsystem.InfraMapSurfaceBg
import com.inframap.frontend.designsystem.InfraMapTextPrimary
import com.inframap.frontend.designsystem.InfraMapTextSecondary
import com.inframap.frontend.designsystem.StatusOffline
import com.inframap.frontend.designsystem.StatusOnline
import com.inframap.frontend.designsystem.StatusStaging
import com.inframap.frontend.designsystem.StatusWarning
import com.inframap.frontend.domain.model.TopologyEdge
import com.inframap.frontend.domain.model.TopologyNode
import kotlin.math.sqrt

// Link Type Edge Colors
private val PhysicalEdgeColor = Color(0xFF8BE9FD)
private val VirtualEdgeColor = Color(0xFFBD93F9)
private val ContainerEdgeColor = Color(0xFF50FA7B)
private val RoutedEdgeColor = Color(0xFFFFB86C)
private val ManualEdgeColor = Color(0xFF6272A4)

// Subnet Box Style
private val SubnetFillColor = Color(0x128B5CF6)
private val SubnetBorderColor = Color(0x358B5CF6)

private const val GRID_SPACING = 24f
private const val CARD_WIDTH = 135f
private const val CARD_HEIGHT = 52f
private const val HIT_TEST_RADIUS = 42f

@OptIn(ExperimentalTextApi::class)
@Suppress("LongMethod", "CyclomaticComplexMethod")
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
    val activeTool = state.activeTool
    val textMeasurer = rememberTextMeasurer()

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
                }.pointerInput(activeTool) {
                    detectDragGestures { change, dragAmount ->
                        change.consume()
                        actions.onPan(dragAmount)
                    }
                }.pointerInput(graph, positions, panOffset, zoomScale, activeTool) {
                    detectTapGestures { tapOffset ->
                        val graphX = (tapOffset.x - panOffset.x) / zoomScale
                        val graphY = (tapOffset.y - panOffset.y) / zoomScale
                        val clickedNodeId = findClickedNodeId(Offset(graphX, graphY), positions)
                        actions.onNodeSelected(clickedNodeId)
                    }
                },
    ) {
        // 1. Draw Canvas Background (#121214)
        drawRect(color = InfraMapCanvasBg)

        // 2. Draw Dot Matrix Grid (#27272a)
        drawDotMatrixGrid(panOffset, zoomScale, size)

        withTransform({
            translate(panOffset.x, panOffset.y)
            scale(zoomScale, zoomScale, pivot = Offset.Zero)
        }) {
            // 3. Subnet Boundary Boxes
            if (state.showSubnetBoundaries) {
                drawSubnetBoundaries(graph.nodes, positions, textMeasurer)
            }

            // 4. Edges
            drawTopologyEdges(graph.edges, positions, zoomScale)

            // 5. Sleek Node Cards with Vector Icons & Status Glow
            drawTopologyNodeCards(graph.nodes, positions, selectedNodeId, zoomScale, textMeasurer)
        }
    }
}

private fun DrawScope.drawDotMatrixGrid(
    panOffset: Offset,
    zoomScale: Float,
    canvasSize: Size,
) {
    val step = GRID_SPACING * zoomScale
    if (step < 8f) return

    val startX = (panOffset.x % step).let { if (it > 0) it - step else it }
    val startY = (panOffset.y % step).let { if (it > 0) it - step else it }

    var x = startX
    while (x < canvasSize.width) {
        var y = startY
        while (y < canvasSize.height) {
            drawCircle(
                color = InfraMapBorder,
                radius = 1.2f * zoomScale.coerceIn(0.8f, 2.0f),
                center = Offset(x, y),
            )
            y += step
        }
        x += step
    }
}

@OptIn(ExperimentalTextApi::class)
private fun DrawScope.drawSubnetBoundaries(
    nodes: List<TopologyNode>,
    positions: Map<String, Offset>,
    textMeasurer: androidx.compose.ui.text.TextMeasurer,
) {
    if (nodes.isEmpty()) return

    // Group nodes into subnets based on IP prefix / device group
    val groupedNodes =
        nodes.groupBy { node ->
            val hash = node.id.hashCode().coerceAtLeast(0) % 2
            if (hash == 0) "192.168.1.0/24" else "10.0.0.0/24"
        }

    groupedNodes.forEach { (subnetCidr, groupList) ->
        var minX = Float.MAX_VALUE
        var minY = Float.MAX_VALUE
        var maxX = -Float.MAX_VALUE
        var maxY = -Float.MAX_VALUE
        var count = 0

        groupList.forEach { node ->
            val pos = positions[node.id] ?: return@forEach
            count++
            minX = minOf(minX, pos.x - CARD_WIDTH / 2 - 24f)
            minY = minOf(minY, pos.y - CARD_HEIGHT / 2 - 32f)
            maxX = maxOf(maxX, pos.x + CARD_WIDTH / 2 + 24f)
            maxY = maxOf(maxY, pos.y + CARD_HEIGHT / 2 + 24f)
        }

        if (count > 0) {
            val rectSize = Size(maxX - minX, maxY - minY)
            val topLeft = Offset(minX, minY)

            drawRoundRect(
                color = SubnetFillColor,
                topLeft = topLeft,
                size = rectSize,
                cornerRadius = CornerRadius(12f, 12f),
            )

            drawRoundRect(
                color = SubnetBorderColor,
                topLeft = topLeft,
                size = rectSize,
                cornerRadius = CornerRadius(12f, 12f),
                style =
                    Stroke(
                        width = 1.5f,
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 6f), 0f),
                    ),
            )

            drawText(
                textMeasurer = textMeasurer,
                text = "Subnet $subnetCidr",
                topLeft = Offset(minX + 12f, minY + 8f),
                style =
                    TextStyle(
                        color = InfraMapTextSecondary,
                    ),
            )
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

@OptIn(ExperimentalTextApi::class)
private fun DrawScope.drawTopologyNodeCards(
    nodes: List<TopologyNode>,
    positions: Map<String, Offset>,
    selectedNodeId: String?,
    zoomScale: Float,
    textMeasurer: androidx.compose.ui.text.TextMeasurer,
) {
    nodes.forEach { node ->
        val pos = positions[node.id] ?: return@forEach
        val isSelected = node.id == selectedNodeId
        val glowColor = getStatusGlowColor(node.status)

        val cardTopLeft = Offset(pos.x - CARD_WIDTH / 2, pos.y - CARD_HEIGHT / 2)
        val cardSize = Size(CARD_WIDTH, CARD_HEIGHT)

        // Card Surface (#18181b)
        drawRoundRect(
            color = InfraMapSurfaceBg,
            topLeft = cardTopLeft,
            size = cardSize,
            cornerRadius = CornerRadius(8f, 8f),
        )

        // Card Border (#27272a or glowing #8be9fd if selected)
        val borderColor = if (isSelected) InfraMapCyan else InfraMapBorder
        val borderWidth = if (isSelected) 2.5f / zoomScale else 1f / zoomScale
        drawRoundRect(
            color = borderColor,
            topLeft = cardTopLeft,
            size = cardSize,
            cornerRadius = CornerRadius(8f, 8f),
            style = Stroke(width = borderWidth),
        )

        // Vector Device Icon Badge (Left side)
        val iconCenter = Offset(cardTopLeft.x + 18f, cardTopLeft.y + CARD_HEIGHT / 2)
        drawDeviceVectorBadge(node.deviceType, iconCenter, glowColor)

        // Status Glow Dot (Next to icon badge)
        val statusDotCenter = Offset(cardTopLeft.x + 32f, cardTopLeft.y + 14f)
        drawCircle(
            color = glowColor,
            radius = 3.5f,
            center = statusDotCenter,
        )

        // Node Label
        drawText(
            textMeasurer = textMeasurer,
            text = node.label,
            topLeft = Offset(cardTopLeft.x + 38f, cardTopLeft.y + 8f),
            style =
                TextStyle(
                    color = InfraMapTextPrimary,
                ),
        )

        // Node Device Type Subtext
        drawText(
            textMeasurer = textMeasurer,
            text = node.deviceType.uppercase(),
            topLeft = Offset(cardTopLeft.x + 38f, cardTopLeft.y + 27f),
            style =
                TextStyle(
                    color = InfraMapTextSecondary,
                ),
        )
    }
}

private fun DrawScope.drawDeviceVectorBadge(
    deviceType: String,
    center: Offset,
    badgeColor: Color,
) {
    // Vector badge icon representation
    when (deviceType.lowercase()) {
        "router" -> {
            drawCircle(color = badgeColor.copy(alpha = 0.2f), radius = 10f, center = center)
            drawCircle(color = badgeColor, radius = 9f, center = center, style = Stroke(width = 1.5f))
            drawLine(badgeColor, Offset(center.x - 5f, center.y), Offset(center.x + 5f, center.y), strokeWidth = 1.5f)
            drawLine(badgeColor, Offset(center.x, center.y - 5f), Offset(center.x, center.y + 5f), strokeWidth = 1.5f)
        }
        "switch" -> {
            val rectTopLeft = Offset(center.x - 9f, center.y - 7f)
            drawRoundRect(badgeColor.copy(alpha = 0.2f), topLeft = rectTopLeft, size = Size(18f, 14f), cornerRadius = CornerRadius(2f))
            drawRoundRect(
                badgeColor,
                topLeft = rectTopLeft,
                size = Size(18f, 14f),
                cornerRadius = CornerRadius(2f),
                style = Stroke(width = 1.5f),
            )
            drawLine(badgeColor, Offset(center.x - 5f, center.y - 2f), Offset(center.x + 5f, center.y - 2f), strokeWidth = 1.5f)
            drawLine(badgeColor, Offset(center.x - 5f, center.y + 2f), Offset(center.x + 5f, center.y + 2f), strokeWidth = 1.5f)
        }
        "server" -> {
            drawRoundRect(
                badgeColor.copy(alpha = 0.2f),
                topLeft = Offset(center.x - 8f, center.y - 9f),
                size = Size(16f, 18f),
                cornerRadius = CornerRadius(2f),
            )
            drawRoundRect(
                badgeColor,
                topLeft = Offset(center.x - 8f, center.y - 9f),
                size = Size(16f, 18f),
                cornerRadius = CornerRadius(2f),
                style = Stroke(width = 1.5f),
            )
            drawLine(badgeColor, Offset(center.x - 5f, center.y - 3f), Offset(center.x + 5f, center.y - 3f), strokeWidth = 1.5f)
            drawLine(badgeColor, Offset(center.x - 5f, center.y + 3f), Offset(center.x + 5f, center.y + 3f), strokeWidth = 1.5f)
        }
        else -> {
            drawCircle(color = badgeColor.copy(alpha = 0.2f), radius = 9f, center = center)
            drawCircle(color = badgeColor, radius = 9f, center = center, style = Stroke(width = 1.5f))
        }
    }
}

private fun getStatusGlowColor(status: String): Color =
    when (status.lowercase()) {
        "active", "online" -> StatusOnline
        "warning", "alert" -> StatusWarning
        "staging", "staged" -> StatusStaging
        else -> StatusOffline
    }

private fun getEdgeColor(linkType: String): Color =
    when (linkType.lowercase()) {
        "physical" -> PhysicalEdgeColor
        "virtual" -> VirtualEdgeColor
        "container" -> ContainerEdgeColor
        "routed" -> RoutedEdgeColor
        else -> ManualEdgeColor
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

/**
 * Coordinate Math Helper functions for testing & position conversion.
 */
object CanvasMatrixMath {
    fun graphToScreen(
        graphPoint: Offset,
        panOffset: Offset,
        zoomScale: Float,
    ): Offset =
        Offset(
            x = graphPoint.x * zoomScale + panOffset.x,
            y = graphPoint.y * zoomScale + panOffset.y,
        )

    fun screenToGraph(
        screenPoint: Offset,
        panOffset: Offset,
        zoomScale: Float,
    ): Offset =
        Offset(
            x = (screenPoint.x - panOffset.x) / zoomScale,
            y = (screenPoint.y - panOffset.y) / zoomScale,
        )
}
