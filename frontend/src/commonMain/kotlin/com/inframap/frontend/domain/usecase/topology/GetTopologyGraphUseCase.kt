package com.inframap.frontend.domain.usecase.topology

import com.inframap.frontend.data.api.ApiResult
import com.inframap.frontend.domain.model.TopologyGraph
import com.inframap.frontend.domain.repository.TopologyRepository
import com.inframap.frontend.domain.usecase.NoParamUseCase

class GetTopologyGraphUseCase(
    private val topologyRepository: TopologyRepository,
) : NoParamUseCase<ApiResult<TopologyGraph>> {
    override suspend fun invoke(): ApiResult<TopologyGraph> = topologyRepository.getTopologyGraph()
}
