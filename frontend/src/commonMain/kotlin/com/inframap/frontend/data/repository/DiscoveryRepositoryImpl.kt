package com.inframap.frontend.data.repository

import com.inframap.frontend.data.api.ApiClient
import com.inframap.frontend.data.api.ApiResult
import com.inframap.frontend.data.api.map
import com.inframap.frontend.data.dto.CreateDiscoverySourceRequest
import com.inframap.frontend.data.dto.DiscoveryListResponse
import com.inframap.frontend.data.dto.DiscoverySourceDto
import com.inframap.frontend.data.mapper.DiscoveryMapper
import com.inframap.frontend.domain.model.DiscoverySource
import com.inframap.frontend.domain.model.PaginatedList
import com.inframap.frontend.domain.repository.DiscoveryRepository

class DiscoveryRepositoryImpl(
    private val apiClient: ApiClient,
) : DiscoveryRepository {
    override suspend fun getSources(): ApiResult<PaginatedList<DiscoverySource>> =
        apiClient
            .get<DiscoveryListResponse>("/api/v1/discovery/sources")
            .map { DiscoveryMapper.toPaginatedList(it) }

    override suspend fun createSource(request: CreateDiscoverySourceRequest): ApiResult<DiscoverySource> =
        apiClient
            .post<DiscoverySourceDto, CreateDiscoverySourceRequest>("/api/v1/discovery/sources", request)
            .map { DiscoveryMapper.toDomain(it) }

    override suspend fun triggerRun(sourceId: String): ApiResult<DiscoverySource> =
        apiClient
            .post<DiscoverySourceDto>("/api/v1/discovery/sources/$sourceId/run")
            .map { DiscoveryMapper.toDomain(it) }

    override suspend fun deleteSource(sourceId: String): ApiResult<Unit> =
        apiClient
            .delete("/api/v1/discovery/sources/$sourceId")
}
