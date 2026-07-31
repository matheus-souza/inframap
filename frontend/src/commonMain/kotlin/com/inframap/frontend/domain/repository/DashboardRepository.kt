package com.inframap.frontend.domain.repository

import com.inframap.frontend.data.api.ApiResult
import com.inframap.frontend.domain.model.DiscoverySource
import com.inframap.frontend.domain.model.Health
import com.inframap.frontend.domain.model.PaginatedList
import com.inframap.frontend.domain.model.StagingDevice

interface DashboardRepository {
    suspend fun getHealth(): ApiResult<Health>

    suspend fun getStagingSummary(): ApiResult<PaginatedList<StagingDevice>>

    suspend fun getDiscoverySources(): ApiResult<List<DiscoverySource>>
}
