package com.inframap.frontend.ui.topology

import androidx.compose.ui.geometry.Offset
import com.inframap.frontend.data.api.ApiResult
import com.inframap.frontend.data.sse.SSEClient
import com.inframap.frontend.data.sse.SSEEvent
import com.inframap.frontend.domain.model.toSummary
import com.inframap.frontend.domain.usecase.subnet.ListSubnetsUseCase
import com.inframap.frontend.domain.usecase.topology.GetTopologyGraphUseCase
import com.inframap.frontend.generated.resources.Res
import com.inframap.frontend.generated.resources.topology_error_load
import com.inframap.frontend.ui.base.BaseViewModel
import com.inframap.frontend.ui.topology.layout.ForceDirectedLayout
import com.inframap.frontend.ui.util.UiText
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.takeWhile
import kotlinx.coroutines.isActive

@Suppress("TooManyFunctions")
class TopologyViewModel(
    private val getTopologyGraphUseCase: GetTopologyGraphUseCase,
    private val sseClient: SSEClient? = null,
    private val listSubnetsUseCase: ListSubnetsUseCase? = null,
    scope: CoroutineScope? = null,
) : BaseViewModel<TopologyState>(TopologyState(), scope) {
    init {
        loadGraph()
        setupSseListening()
    }

    /**
     * The registered Subnets the canvas outlines. A failure leaves the nodes without
     * boundaries; the graph itself is unaffected.
     */
    private fun loadSubnets() {
        val useCase = listSubnetsUseCase ?: return
        launchJob("loadSubnets") {
            val result = useCase()
            if (result is ApiResult.Success) {
                updateState { it.copy(subnets = result.data.items.map { subnet -> subnet.toSummary() }) }
            }
        }
    }

    fun loadGraph() {
        loadSubnets()
        updateState { it.copy(isLoading = true, errorMessage = null) }
        launchJob("loadGraph") {
            val result = getTopologyGraphUseCase()
            when (result) {
                is ApiResult.Success -> {
                    val positions = ForceDirectedLayout.calculatePositions(result.data)
                    val updatedSelectedNode =
                        state.value.selectedNode?.let { current ->
                            result.data.nodes.find { it.id == current.id }
                        }
                    updateState {
                        it.copy(
                            isLoading = false,
                            graph = result.data,
                            nodePositions = positions,
                            selectedNode = updatedSelectedNode,
                            errorMessage = null,
                        )
                    }
                }

                else -> {
                    val mappedError = mapError(result, UiText.Resource(Res.string.topology_error_load))
                    updateState {
                        it.copy(
                            isLoading = false,
                            errorMessage = mappedError,
                        )
                    }
                }
            }
        }
    }

    fun refresh() {
        loadGraph()
    }

    fun selectNode(nodeId: String?) {
        val targetNode =
            state.value.graph
                ?.nodes
                ?.find { it.id == nodeId }
        updateState { it.copy(selectedNode = targetNode) }
    }

    fun dismissNodeDetails() {
        updateState { it.copy(selectedNode = null) }
    }

    fun onPan(dragAmount: Offset) {
        updateState { it.copy(panOffset = it.panOffset + dragAmount) }
    }

    fun onZoom(zoomFactor: Float) {
        updateState {
            val newScale = (it.zoomScale * zoomFactor).coerceIn(MIN_ZOOM, MAX_ZOOM)
            it.copy(zoomScale = newScale)
        }
    }

    fun resetViewport() {
        updateState { it.copy(panOffset = Offset.Zero, zoomScale = 1.0f) }
    }

    fun selectTool(tool: CanvasTool) {
        updateState { it.copy(activeTool = tool) }
    }

    fun runAutoLayout() {
        val currentGraph = state.value.graph ?: return
        val newPositions = ForceDirectedLayout.calculatePositions(currentGraph)
        updateState { it.copy(nodePositions = newPositions) }
    }

    fun toggleSubnetBoundaries() {
        updateState { it.copy(showSubnetBoundaries = !it.showSubnetBoundaries) }
    }

    private fun setupSseListening() {
        val client = sseClient ?: return
        launchJob("sse_listening") {
            // clear() cancels this job; the finally closes the browser's connection with it.
            try {
                while (isActive) {
                    client
                        .connect("/api/v1/events/stream")
                        .takeWhile { it !is SSEEvent.Disconnected }
                        .collect { event ->
                            if (event is SSEEvent.TopologyUpdated || event is SSEEvent.DeviceCreated) {
                                loadGraph()
                            }
                        }
                    client.disconnect()
                    delay(5000L)
                }
            } finally {
                client.disconnect()
            }
        }
    }

    companion object {
        private const val MIN_ZOOM = 0.1f
        private const val MAX_ZOOM = 5.0f
    }
}
