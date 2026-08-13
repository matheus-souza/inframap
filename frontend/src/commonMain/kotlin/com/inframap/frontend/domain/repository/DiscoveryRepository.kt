package com.inframap.frontend.domain.repository

import com.inframap.frontend.data.api.ApiResult
import com.inframap.frontend.data.dto.CreateDiscoverySourceRequest
import com.inframap.frontend.domain.model.DiscoverySource
import com.inframap.frontend.domain.model.PaginatedList

interface DiscoveryRepository {
    suspend fun getSources(): ApiResult<PaginatedList<DiscoverySource>>

    suspend fun createSource(request: CreateDiscoverySourceRequest): ApiResult<DiscoverySource>

    suspend fun triggerRun(sourceId: String): ApiResult<DiscoverySource>

    suspend fun deleteSource(sourceId: String): ApiResult<Unit>
}
