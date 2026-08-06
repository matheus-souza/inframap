package com.inframap.frontend.domain.usecase.topology

import com.inframap.frontend.data.api.ApiResult
import com.inframap.frontend.domain.model.TopologyEdge
import com.inframap.frontend.domain.model.TopologyGraph
import com.inframap.frontend.domain.model.TopologyNode
import com.inframap.frontend.fakes.FakeTopologyRepository
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class GetTopologyGraphUseCaseTest {
    @Test
    fun invokeInvokesTopologyRepositoryAndReturnsResult() =
        runTest {
            val sampleGraph =
                TopologyGraph(
                    nodes = listOf(TopologyNode("n1", "router-1", "router", "active")),
                    edges = listOf(TopologyEdge("e1", "n1", "n1", "virtual")),
                )
            val fakeRepo =
                FakeTopologyRepository(
                    result = ApiResult.Success(sampleGraph, requestId = "req-1"),
                )
            val useCase = GetTopologyGraphUseCase(fakeRepo)

            val result = useCase()

            assertEquals(1, fakeRepo.getTopologyGraphCallCount)
            assertIs<ApiResult.Success<TopologyGraph>>(result)
            assertEquals(
                "router-1",
                result.data.nodes
                    .first()
                    .label,
            )
        }
}
