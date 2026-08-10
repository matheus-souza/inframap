package com.inframap.frontend.data.repository

import com.inframap.frontend.data.api.ApiClient
import com.inframap.frontend.data.api.ApiResult
import com.inframap.frontend.data.api.map
import com.inframap.frontend.data.dto.DiscoveryListResponse
import com.inframap.frontend.data.dto.HealthDto
import com.inframap.frontend.data.dto.StagingListResponse
import com.inframap.frontend.data.mapper.DashboardMapper
import com.inframap.frontend.data.mapper.StagingMapper
import com.inframap.frontend.domain.model.DiscoverySource
import com.inframap.frontend.domain.model.Health
import com.inframap.frontend.domain.model.PaginatedList
import com.inframap.frontend.domain.model.StagingDevice
import com.inframap.frontend.domain.repository.DashboardRepository

class DashboardRepositoryImpl(
    private val apiClient: ApiClient,
) : DashboardRepository {
    override suspend fun getHealth(): ApiResult<Health> =
        apiClient.get<HealthDto>("/api/v1/health").map {
            DashboardMapper.toDomain(it)
        }

    override suspend fun getStagingSummary(): ApiResult<PaginatedList<StagingDevice>> {
        val params = mapOf("page" to "1", "per_page" to "5")
        return apiClient.get<StagingListResponse>("/api/v1/devices/staging", params).map {
            StagingMapper.toPaginatedList(it)
        }
    }

    override suspend fun getDiscoverySources(): ApiResult<List<DiscoverySource>> =
        apiClient.get<DiscoveryListResponse>("/api/v1/discovery/sources").map { response ->
            response.items.map { DashboardMapper.toDomain(it) }
        }
}
