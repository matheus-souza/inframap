package com.inframap.frontend.domain.repository

import com.inframap.frontend.data.api.ApiResult
import com.inframap.frontend.domain.model.TopologyGraph

interface TopologyRepository {
    suspend fun getTopologyGraph(): ApiResult<TopologyGraph>
}
