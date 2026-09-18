package com.inframap.frontend.data.repository

import com.inframap.frontend.data.api.ApiClient
import com.inframap.frontend.data.api.ApiResult
import com.inframap.frontend.data.api.map
import com.inframap.frontend.data.dto.CreateDiscoverySourceRequest
import com.inframap.frontend.data.dto.DiscoveryListResponse
import com.inframap.frontend.data.dto.DiscoverySourceDeletionImpactResponseDto
import com.inframap.frontend.data.dto.DiscoverySourceDto
import com.inframap.frontend.data.dto.DiscoverySourceHealthResponseDto
import com.inframap.frontend.data.dto.UpdateDiscoverySourceRequest
import com.inframap.frontend.data.mapper.DiscoveryMapper
import com.inframap.frontend.domain.model.DiscoverySource
import com.inframap.frontend.domain.model.DiscoverySourceDeletionImpact
import com.inframap.frontend.domain.model.PaginatedList
import com.inframap.frontend.domain.model.ProviderHealth
import com.inframap.frontend.domain.repository.DiscoveryRepository

class DiscoveryRepositoryImpl(
    private val apiClient: ApiClient,
) : DiscoveryRepository {
    override suspend fun getSources(): ApiResult<PaginatedList<DiscoverySource>> =
        apiClient
            .get<DiscoveryListResponse>("/api/v1/discovery/sources")
            .map { DiscoveryMapper.toPaginatedList(it) }

    override suspend fun getSourceById(id: String): ApiResult<DiscoverySource> =
        apiClient
            .get<DiscoverySourceDto>("/api/v1/discovery/sources/$id")
            .map { DiscoveryMapper.toDomain(it) }

    override suspend fun createSource(request: CreateDiscoverySourceRequest): ApiResult<DiscoverySource> =
        apiClient
            .post<DiscoverySourceDto, CreateDiscoverySourceRequest>("/api/v1/discovery/sources", request)
            .map { DiscoveryMapper.toDomain(it) }

    override suspend fun updateSource(
        id: String,
        request: UpdateDiscoverySourceRequest,
    ): ApiResult<DiscoverySource> =
        apiClient
            .put<DiscoverySourceDto, UpdateDiscoverySourceRequest>("/api/v1/discovery/sources/$id", request)
            .map { DiscoveryMapper.toDomain(it) }

    override suspend fun triggerRun(sourceId: String): ApiResult<DiscoverySource> =
        apiClient
            .post<DiscoverySourceDto>("/api/v1/discovery/sources/$sourceId/run")
            .map { DiscoveryMapper.toDomain(it) }

    override suspend fun deleteSource(sourceId: String): ApiResult<Unit> =
        apiClient
            .delete("/api/v1/discovery/sources/$sourceId")

    override suspend fun testSourceHealth(
        id: String,
        providerId: String?,
    ): ApiResult<ProviderHealth> {
        val path =
            if (providerId != null) {
                "/api/v1/discovery/sources/$id/health?provider=$providerId"
            } else {
                "/api/v1/discovery/sources/$id/health"
            }
        return apiClient
            .post<DiscoverySourceHealthResponseDto>(path)
            .map { dto ->
                ProviderHealth(
                    providerId = dto.providerId.ifEmpty { providerId.orEmpty() },
                    isHealthy = dto.status.equals("ok", ignoreCase = true),
                    message = dto.message,
                )
            }
    }

    override suspend fun getDeletionImpact(id: String): ApiResult<DiscoverySourceDeletionImpact> =
        apiClient
            .get<DiscoverySourceDeletionImpactResponseDto>("/api/v1/discovery/sources/$id/deletion-impact")
            .map { DiscoveryMapper.toDomain(it.impact) }
}
