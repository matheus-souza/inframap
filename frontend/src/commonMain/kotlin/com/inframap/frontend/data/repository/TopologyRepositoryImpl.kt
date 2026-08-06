package com.inframap.frontend.data.repository

import com.inframap.frontend.data.api.ApiClient
import com.inframap.frontend.data.api.ApiResult
import com.inframap.frontend.data.api.map
import com.inframap.frontend.data.dto.TopologyGraphDto
import com.inframap.frontend.data.mapper.toDomain
import com.inframap.frontend.domain.model.TopologyGraph
import com.inframap.frontend.domain.repository.TopologyRepository

class TopologyRepositoryImpl(
    private val apiClient: ApiClient,
) : TopologyRepository {
    override suspend fun getTopologyGraph(): ApiResult<TopologyGraph> =
        apiClient.get<TopologyGraphDto>("/api/v1/topology/graph").map { it.toDomain() }
}
