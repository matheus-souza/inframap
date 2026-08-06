package com.inframap.frontend.fakes

import com.inframap.frontend.data.api.ApiResult
import com.inframap.frontend.domain.model.TopologyGraph
import com.inframap.frontend.domain.repository.TopologyRepository

class FakeTopologyRepository(
    var result: ApiResult<TopologyGraph> =
        ApiResult.Success(
            data = TopologyGraph(nodes = emptyList(), edges = emptyList()),
            requestId = "req-fake-topology",
        ),
) : TopologyRepository {
    var getTopologyGraphCallCount = 0

    override suspend fun getTopologyGraph(): ApiResult<TopologyGraph> {
        getTopologyGraphCallCount++
        return result
    }
}
