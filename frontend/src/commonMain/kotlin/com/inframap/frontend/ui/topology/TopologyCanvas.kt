package com.inframap.frontend.ui.topology

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
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
import androidx.compose.ui.text.TextMeasurer
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
import com.inframap.frontend.generated.resources.Res
import com.inframap.frontend.generated.resources.topology_preview_router
import com.inframap.frontend.generated.resources.topology_preview_server
import com.inframap.frontend.generated.resources.topology_preview_switch
import com.inframap.frontend.ui.topology.CanvasTool
import org.jetbrains.compose.resources.stringResource
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
    val graph = state.graph
    val positions = state.nodePositions
    val panOffset = state.panOffset
    val zoomScale = state.zoomScale
    val selectedNodeId = state.selectedNode?.id
    val activeTool = state.activeTool
    val textMeasurer = rememberTextMeasurer()

    val routerLabel = stringResource(Res.string.topology_preview_router)
    val switchLabel = stringResource(Res.string.topology_preview_switch)
    val serverLabel = stringResource(Res.string.topology_preview_server)

    Canvas(
        modifier =
            modifier
                .fillMaxSize()
                .background(InfraMapCanvasBg)
                .pointerInput(activeTool) {
                    detectTransformGestures { _, pan, zoom, _ ->
                        if (activeTool == CanvasTool.HAND) {
                            actions.onZoom(zoom)
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
            if (graph == null || graph.nodes.isEmpty()) {
                // Clean canvas node preview when empty
                drawEmptyCanvasNodePreview(textMeasurer, routerLabel, switchLabel, serverLabel)
            } else {
                // 3. Subnet Boundary Boxes
                if (state.showSubnetBoundaries) {
                    drawSubnetBoundaries(graph.nodes, positions, textMeasurer)
                }

                // 4. Edges
                drawTopologyEdges(graph.edges, positions, zoomScale)

                // 5. Sleek Node Cards with Vector Icons & Status Glow & Selection Indicator
                drawTopologyNodeCards(graph.nodes, positions, selectedNodeId, zoomScale, textMeasurer)
            }
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

@Suppress("LongMethod")
@OptIn(ExperimentalTextApi::class)
private fun DrawScope.drawEmptyCanvasNodePreview(
    textMeasurer: TextMeasurer,
    routerLabel: String,
    switchLabel: String,
    serverLabel: String,
) {
    val center = Offset(size.width / 2f, size.height / 2f)
    val ghostNodes =
        listOf(
            Triple(center + Offset(0f, -70f), routerLabel, "router"),
            Triple(center + Offset(-130f, 60f), switchLabel, "switch"),
            Triple(center + Offset(130f, 60f), serverLabel, "server"),
        )

    val dashedStroke =
        Stroke(
            width = 1.5f,
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 6f), 0f),
        )

    val node1Pos = ghostNodes[0].first
    val node2Pos = ghostNodes[1].first
    val node3Pos = ghostNodes[2].first

    drawLine(
        color = PhysicalEdgeColor.copy(alpha = 0.3f),
        start = node1Pos,
        end = node2Pos,
        strokeWidth = 1.5f,
        pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 6f), 0f),
    )
    drawLine(
        color = VirtualEdgeColor.copy(alpha = 0.3f),
        start = node1Pos,
        end = node3Pos,
        strokeWidth = 1.5f,
        pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 6f), 0f),
    )

    ghostNodes.forEach { (pos, label, deviceType) ->
        val cardTopLeft = Offset(pos.x - CARD_WIDTH / 2, pos.y - CARD_HEIGHT / 2)
        val cardSize = Size(CARD_WIDTH, CARD_HEIGHT)
        val ghostAlphaColor = InfraMapCyan.copy(alpha = 0.3f)

        drawRoundRect(
            color = InfraMapSurfaceBg.copy(alpha = 0.35f),
            topLeft = cardTopLeft,
            size = cardSize,
            cornerRadius = CornerRadius(8f, 8f),
        )

        drawRoundRect(
            color = InfraMapBorder.copy(alpha = 0.4f),
            topLeft = cardTopLeft,
            size = cardSize,
            cornerRadius = CornerRadius(8f, 8f),
            style = dashedStroke,
        )

        val iconCenter = Offset(cardTopLeft.x + 18f, cardTopLeft.y + CARD_HEIGHT / 2)
        drawDeviceVectorBadge(deviceType, iconCenter, ghostAlphaColor)

        drawText(
            textMeasurer = textMeasurer,
            text = label,
            topLeft = Offset(cardTopLeft.x + 38f, cardTopLeft.y + 16f),
            style =
                TextStyle(
                    color = InfraMapTextSecondary.copy(alpha = 0.45f),
                ),
        )
    }
}

@OptIn(ExperimentalTextApi::class)
private fun DrawScope.drawSubnetBoundaries(
    nodes: List<TopologyNode>,
    positions: Map<String, Offset>,
    textMeasurer: TextMeasurer,
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

private fun DrawScope.drawNodeSelectionGlow(
    cardTopLeft: Offset,
    zoomScale: Float,
) {
    val glowMargin = 5f / zoomScale
    drawRoundRect(
        color = InfraMapCyan.copy(alpha = 0.25f),
        topLeft = Offset(cardTopLeft.x - glowMargin, cardTopLeft.y - glowMargin),
        size = Size(CARD_WIDTH + glowMargin * 2, CARD_HEIGHT + glowMargin * 2),
        cornerRadius = CornerRadius(11f, 11f),
    )
}

private fun DrawScope.drawNodeSelectionTopBar(
    cardTopLeft: Offset,
    zoomScale: Float,
) {
    drawRoundRect(
        color = InfraMapCyan,
        topLeft = cardTopLeft,
        size = Size(CARD_WIDTH, 3.5f / zoomScale),
        cornerRadius = CornerRadius(8f, 8f),
    )
}

@Suppress("LongMethod")
@OptIn(ExperimentalTextApi::class)
private fun DrawScope.drawTopologyNodeCards(
    nodes: List<TopologyNode>,
    positions: Map<String, Offset>,
    selectedNodeId: String?,
    zoomScale: Float,
    textMeasurer: TextMeasurer,
) {
    nodes.forEach { node ->
        val pos = positions[node.id] ?: return@forEach
        val isSelected = node.id == selectedNodeId
        val glowColor = getStatusGlowColor(node.status)

        val cardTopLeft = Offset(pos.x - CARD_WIDTH / 2, pos.y - CARD_HEIGHT / 2)
        val cardSize = Size(CARD_WIDTH, CARD_HEIGHT)

        // 1. Draw outer selection glow BEFORE surface fill
        if (isSelected) {
            drawNodeSelectionGlow(cardTopLeft, zoomScale)
        }

        // 2. Card Surface (#18181b or elevated dark surface when selected)
        val surfaceColor = if (isSelected) Color(0xFF27272A) else InfraMapSurfaceBg
        drawRoundRect(
            color = surfaceColor,
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

        // 4. Draw cyan accent top bar AFTER surface fill so it remains visible
        if (isSelected) {
            drawNodeSelectionTopBar(cardTopLeft, zoomScale)
        }

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

@Suppress("LongMethod")
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
            drawLine(
                badgeColor,
                Offset(center.x - 5f, center.y),
                Offset(center.x + 5f, center.y),
                strokeWidth = 1.5f,
            )
            drawLine(
                badgeColor,
                Offset(center.x, center.y - 5f),
                Offset(center.x, center.y + 5f),
                strokeWidth = 1.5f,
            )
        }

        "switch" -> {
            val rectTopLeft = Offset(center.x - 9f, center.y - 7f)
            drawRoundRect(
                badgeColor.copy(alpha = 0.2f),
                topLeft = rectTopLeft,
                size = Size(18f, 14f),
                cornerRadius = CornerRadius(2f),
            )
            drawRoundRect(
                badgeColor,
                topLeft = rectTopLeft,
                size = Size(18f, 14f),
                cornerRadius = CornerRadius(2f),
                style = Stroke(width = 1.5f),
            )
            drawLine(
                badgeColor,
                Offset(center.x - 5f, center.y - 2f),
                Offset(center.x + 5f, center.y - 2f),
                strokeWidth = 1.5f,
            )
            drawLine(
                badgeColor,
                Offset(center.x - 5f, center.y + 2f),
                Offset(center.x + 5f, center.y + 2f),
                strokeWidth = 1.5f,
            )
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
            drawLine(
                badgeColor,
                Offset(center.x - 5f, center.y - 3f),
                Offset(center.x + 5f, center.y - 3f),
                strokeWidth = 1.5f,
            )
            drawLine(
                badgeColor,
                Offset(center.x - 5f, center.y + 3f),
                Offset(center.x + 5f, center.y + 3f),
                strokeWidth = 1.5f,
            )
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
