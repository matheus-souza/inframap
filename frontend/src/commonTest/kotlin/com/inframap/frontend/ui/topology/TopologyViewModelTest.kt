package com.inframap.frontend.ui.topology

import androidx.compose.ui.geometry.Offset
import app.cash.turbine.test
import com.inframap.frontend.data.api.ApiResult
import com.inframap.frontend.data.sse.SSEClient
import com.inframap.frontend.data.sse.SSEEvent
import com.inframap.frontend.domain.model.TopologyEdge
import com.inframap.frontend.domain.model.TopologyGraph
import com.inframap.frontend.domain.model.TopologyNode
import com.inframap.frontend.domain.usecase.topology.GetTopologyGraphUseCase
import com.inframap.frontend.fakes.FakeTopologyRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

private class FakeTopologySSEClient : SSEClient {
    val events = MutableSharedFlow<SSEEvent>()
    var connectCount = 0

    override fun connect(url: String): SharedFlow<SSEEvent> {
        connectCount++
        return events.asSharedFlow()
    }

    override fun disconnect() {}
}

@OptIn(ExperimentalCoroutinesApi::class)
class TopologyViewModelTest {
    private val sampleNode = TopologyNode(id = "n1", label = "router-01", deviceType = "router", status = "active")
    private val sampleEdge = TopologyEdge(id = "e1", source = "n1", target = "n1", linkType = "physical")
    private val sampleGraph = TopologyGraph(nodes = listOf(sampleNode), edges = listOf(sampleEdge))

    @Test
    fun loadGraphSuccessPopulatesState() =
        runTest {
            val fakeRepo = FakeTopologyRepository(ApiResult.Success(sampleGraph, requestId = "req-1"))
            val viewModel = TopologyViewModel(GetTopologyGraphUseCase(fakeRepo), scope = this)

            viewModel.state.test {
                val initial = awaitItem()
                assertTrue(initial.isLoading)

                advanceUntilIdle()

                val loadedState = awaitItem()
                assertFalse(loadedState.isLoading)
                assertNull(loadedState.errorMessage)
                assertNotNull(loadedState.graph)
                assertEquals(1, loadedState.graph?.nodes?.size)
                assertEquals(
                    "router-01",
                    loadedState.graph
                        ?.nodes
                        ?.first()
                        ?.label,
                )
                assertTrue(loadedState.nodePositions.containsKey("n1"))
                cancelAndIgnoreRemainingEvents()
            }

            viewModel.clear()
        }

    @Test
    fun loadGraphErrorSetsErrorMessage() =
        runTest {
            val fakeRepo =
                FakeTopologyRepository(
                    ApiResult.Error(code = "FAIL", message = "Failed to load topology", requestId = "req-err", httpStatus = 500),
                )
            val viewModel = TopologyViewModel(GetTopologyGraphUseCase(fakeRepo), scope = this)

            viewModel.state.test {
                val initial = awaitItem()
                assertTrue(initial.isLoading)

                advanceUntilIdle()

                val errorState = awaitItem()
                assertFalse(errorState.isLoading)
                assertNotNull(errorState.errorMessage)
                assertEquals("Failed to load topology", errorState.errorMessage?.asStringAsync())
                cancelAndIgnoreRemainingEvents()
            }

            viewModel.clear()
        }

    @Test
    fun nodeSelectionAndDismissalWorkflow() =
        runTest {
            val fakeRepo = FakeTopologyRepository(ApiResult.Success(sampleGraph, requestId = "req-1"))
            val viewModel = TopologyViewModel(GetTopologyGraphUseCase(fakeRepo), scope = this)

            viewModel.state.test {
                awaitItem() // initial loading state
                advanceUntilIdle()
                awaitItem() // loaded state

                viewModel.selectNode("n1")
                val selectedState = awaitItem()
                assertEquals("n1", selectedState.selectedNode?.id)

                viewModel.dismissNodeDetails()
                val dismissedState = awaitItem()
                assertNull(dismissedState.selectedNode)
                cancelAndIgnoreRemainingEvents()
            }

            viewModel.clear()
        }

    @Test
    fun panAndZoomViewportActionsUpdateState() =
        runTest {
            val fakeRepo = FakeTopologyRepository(ApiResult.Success(sampleGraph, requestId = "req-1"))
            val viewModel = TopologyViewModel(GetTopologyGraphUseCase(fakeRepo), scope = this)

            viewModel.state.test {
                awaitItem() // initial loading state
                advanceUntilIdle()
                awaitItem() // loaded state

                viewModel.onPan(Offset(10f, 20f))
                val pannedState = awaitItem()
                assertEquals(Offset(10f, 20f), pannedState.panOffset)

                viewModel.onZoom(1.5f)
                val zoomedState = awaitItem()
                assertEquals(1.5f, zoomedState.zoomScale)

                viewModel.resetViewport()
                val resetState = awaitItem()
                assertEquals(Offset.Zero, resetState.panOffset)
                assertEquals(1.0f, resetState.zoomScale)
                cancelAndIgnoreRemainingEvents()
            }

            viewModel.clear()
        }

    @Test
    fun sseTopologyUpdatedTriggersReload() =
        runTest {
            val fakeSse = FakeTopologySSEClient()
            val fakeRepo = FakeTopologyRepository(ApiResult.Success(sampleGraph, requestId = "req-1"))
            val viewModel =
                TopologyViewModel(
                    getTopologyGraphUseCase = GetTopologyGraphUseCase(fakeRepo),
                    sseClient = fakeSse,
                    scope = this,
                )

            viewModel.state.test {
                val initialLoading = awaitItem()
                assertTrue(initialLoading.isLoading)

                advanceUntilIdle()

                val initialLoaded = awaitItem()
                assertFalse(initialLoaded.isLoading)
                assertEquals(1, initialLoaded.graph?.nodes?.size)
                assertEquals(1, fakeSse.connectCount)

                fakeRepo.result =
                    ApiResult.Success(
                        data =
                            TopologyGraph(
                                nodes = listOf(sampleNode, TopologyNode("n2", "sw1", "switch", "active")),
                                edges = emptyList(),
                            ),
                        requestId = "req-2",
                    )
                fakeSse.events.emit(SSEEvent.TopologyUpdated(id = "ev1", data = "{}"))
                advanceUntilIdle()

                val refreshedState = expectMostRecentItem()
                assertFalse(refreshedState.isLoading)
                assertEquals(2, refreshedState.graph?.nodes?.size)
                assertEquals(2, fakeRepo.getTopologyGraphCallCount)
                cancelAndIgnoreRemainingEvents()
            }

            viewModel.clear()
        }

    @Test
    fun toolSelectionAutoLayoutAndSubnetToggleWorkCorrectly() =
        runTest {
            val fakeRepo = FakeTopologyRepository(ApiResult.Success(sampleGraph, requestId = "req-1"))
            val viewModel = TopologyViewModel(GetTopologyGraphUseCase(fakeRepo), scope = this)

            viewModel.state.test {
                awaitItem() // initial loading
                advanceUntilIdle()
                awaitItem() // loaded

                assertEquals(CanvasTool.POINTER, viewModel.state.value.activeTool)
                viewModel.selectTool(CanvasTool.HAND)
                val handState = awaitItem()
                assertEquals(CanvasTool.HAND, handState.activeTool)

                assertTrue(viewModel.state.value.showSubnetBoundaries)
                viewModel.toggleSubnetBoundaries()
                val toggleState = awaitItem()
                assertFalse(toggleState.showSubnetBoundaries)

                viewModel.runAutoLayout()
                advanceUntilIdle()
                assertTrue(
                    viewModel.state.value.nodePositions
                        .containsKey("n1"),
                )
                cancelAndIgnoreRemainingEvents()
            }

            viewModel.clear()
        }
}
