package com.inframap.frontend.ui.topology

import androidx.compose.ui.geometry.Offset
import com.inframap.frontend.data.api.ApiResult
import com.inframap.frontend.data.sse.SSEClient
import com.inframap.frontend.data.sse.SSEEvent
import com.inframap.frontend.domain.usecase.topology.GetTopologyGraphUseCase
import com.inframap.frontend.ui.base.BaseViewModel
import com.inframap.frontend.ui.topology.layout.ForceDirectedLayout
import kotlinx.coroutines.CoroutineScope

@Suppress("TooManyFunctions")
class TopologyViewModel(
    private val getTopologyGraphUseCase: GetTopologyGraphUseCase,
    private val sseClient: SSEClient? = null,
    scope: CoroutineScope? = null,
) : BaseViewModel<TopologyState>(TopologyState(), scope) {
    init {
        loadGraph()
        setupSseListening()
    }

    fun loadGraph() {
        updateState { it.copy(isLoading = true, errorMessage = null) }
        launchJob("loadGraph") {
            val result = getTopologyGraphUseCase()
            when (result) {
                is ApiResult.Success -> {
                    val positions = ForceDirectedLayout.calculatePositions(result.data)
                    updateState {
                        it.copy(
                            isLoading = false,
                            graph = result.data,
                            nodePositions = positions,
                            errorMessage = null,
                        )
                    }
                }

                else -> {
                    val mappedError = mapError(result)
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

    private fun setupSseListening() {
        val client = sseClient ?: return
        launchJob("sse_listening") {
            client.connect("/api/v1/events").collect { event ->
                if (event is SSEEvent.TopologyUpdated || event is SSEEvent.DeviceCreated) {
                    loadGraph()
                }
            }
        }
    }

    companion object {
        private const val MIN_ZOOM = 0.1f
        private const val MAX_ZOOM = 5.0f
    }
}
