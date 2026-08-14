package com.inframap.frontend.ui.topology

import androidx.compose.ui.geometry.Offset
import com.inframap.frontend.domain.model.TopologyGraph
import com.inframap.frontend.domain.model.TopologyNode
import com.inframap.frontend.ui.util.UiText

enum class CanvasTool {
    POINTER,
    HAND,
}

data class TopologyState(
    val isLoading: Boolean = true,
    val errorMessage: UiText? = null,
    val graph: TopologyGraph? = null,
    val nodePositions: Map<String, Offset> = emptyMap(),
    val selectedNode: TopologyNode? = null,
    val panOffset: Offset = Offset.Zero,
    val zoomScale: Float = 1.0f,
    val activeTool: CanvasTool = CanvasTool.POINTER,
    val showSubnetBoundaries: Boolean = true,
)

data class TopologyActions(
    val onRefresh: () -> Unit,
    val onNodeSelected: (String?) -> Unit,
    val onDismissNodeDetails: () -> Unit,
    val onPan: (Offset) -> Unit,
    val onZoom: (Float) -> Unit,
    val onResetViewport: () -> Unit,
    val onToolSelected: (CanvasTool) -> Unit = {},
    val onAutoLayout: () -> Unit = {},
    val onToggleSubnetBoundaries: () -> Unit = {},
    val onTriggerScan: (String) -> Unit = {},
    val onEditMetadata: (String) -> Unit = {},
    val onConfigureDiscovery: () -> Unit = {},
)
