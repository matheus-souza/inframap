package com.inframap.frontend.data.repository

import com.inframap.frontend.data.api.ApiClient
import com.inframap.frontend.data.api.ApiResult
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
    override suspend fun getHealth(): ApiResult<Health> {
        val result = apiClient.get<HealthDto>("/health")
        return when (result) {
            is ApiResult.Success -> ApiResult.Success(DashboardMapper.toDomain(result.data), result.requestId)
            is ApiResult.Error -> ApiResult.Error(result.code, result.message, result.requestId, result.httpStatus)
            is ApiResult.NetworkError -> ApiResult.NetworkError(result.throwable)
        }
    }

    override suspend fun getStagingSummary(): ApiResult<PaginatedList<StagingDevice>> {
        val params = mapOf("page" to "1", "per_page" to "5")
        val result = apiClient.get<StagingListResponse>("/api/v1/devices/staging", params)
        return when (result) {
            is ApiResult.Success -> ApiResult.Success(StagingMapper.toPaginatedList(result.data), result.requestId)
            is ApiResult.Error -> ApiResult.Error(result.code, result.message, result.requestId, result.httpStatus)
            is ApiResult.NetworkError -> ApiResult.NetworkError(result.throwable)
        }
    }

    override suspend fun getDiscoverySources(): ApiResult<List<DiscoverySource>> {
        val result = apiClient.get<DiscoveryListResponse>("/api/v1/discovery/sources")
        return when (result) {
            is ApiResult.Success ->
                ApiResult.Success(
                    data = result.data.items.map { DashboardMapper.toDomain(it) },
                    requestId = result.requestId,
                )
            is ApiResult.Error -> ApiResult.Error(result.code, result.message, result.requestId, result.httpStatus)
            is ApiResult.NetworkError -> ApiResult.NetworkError(result.throwable)
        }
    }
}
