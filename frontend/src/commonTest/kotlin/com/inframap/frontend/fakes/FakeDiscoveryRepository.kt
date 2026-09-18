package com.inframap.frontend.fakes

import com.inframap.frontend.data.api.ApiResult
import com.inframap.frontend.data.dto.CreateDiscoverySourceRequest
import com.inframap.frontend.domain.model.DiscoverySource
import com.inframap.frontend.domain.model.DiscoverySourceDeletionImpact
import com.inframap.frontend.domain.model.PaginatedList
import com.inframap.frontend.domain.repository.DiscoveryRepository

class FakeDiscoveryRepository(
    var getSourcesResult: ApiResult<PaginatedList<DiscoverySource>> =
        ApiResult.Success(
            PaginatedList(items = listOf(DEFAULT_SOURCE), total = 1L, page = 1, perPage = 50),
            requestId = "",
        ),
    var createSourceResult: ApiResult<DiscoverySource> = ApiResult.Success(DEFAULT_SOURCE, requestId = ""),
    var getSourceByIdResult: ApiResult<DiscoverySource> = ApiResult.Success(DEFAULT_SOURCE, requestId = ""),
    var updateSourceResult: ApiResult<DiscoverySource> = ApiResult.Success(DEFAULT_SOURCE, requestId = ""),
    var triggerRunResult: ApiResult<DiscoverySource> = ApiResult.Success(DEFAULT_SOURCE, requestId = ""),
    var deleteSourceResult: ApiResult<Unit> = ApiResult.Success(Unit, requestId = ""),
    var getDeletionImpactResult: ApiResult<DiscoverySourceDeletionImpact> =
        ApiResult.Success(
            DiscoverySourceDeletionImpact(
                collectorsHalted = 2,
                devicesUnlinked = 5,
            ),
            requestId = "",
        ),
    var testSourceHealthResult: ApiResult<com.inframap.frontend.domain.model.ProviderHealth> =
        ApiResult.Success(
            com.inframap.frontend.domain.model.ProviderHealth(
                providerId = "proxmox",
                isHealthy = true,
                message = "Connection established",
            ),
            requestId = "",
        ),
) : DiscoveryRepository {
    var getSourcesCallCount = 0
    var getSourceByIdCallCount = 0
    var createSourceCallCount = 0
    var updateSourceCallCount = 0
    var getDeletionImpactCallCount = 0
    var lastGetDeletionImpactId: String? = null
    var lastCreateSourceRequest: CreateDiscoverySourceRequest? = null
    var lastUpdateSourceRequest: com.inframap.frontend.data.dto.UpdateDiscoverySourceRequest? = null
    var triggerRunCallCount = 0
    var deleteSourceCallCount = 0
    var testSourceHealthCallCount = 0
    var lastTestSourceHealthId: String? = null
    var lastTestSourceHealthProviderId: String? = null

    override suspend fun getSources(): ApiResult<PaginatedList<DiscoverySource>> {
        getSourcesCallCount++
        return getSourcesResult
    }

    override suspend fun getSourceById(id: String): ApiResult<DiscoverySource> {
        getSourceByIdCallCount++
        return getSourceByIdResult
    }

    override suspend fun createSource(request: CreateDiscoverySourceRequest): ApiResult<DiscoverySource> {
        createSourceCallCount++
        lastCreateSourceRequest = request
        return createSourceResult
    }

    override suspend fun updateSource(
        id: String,
        request: com.inframap.frontend.data.dto.UpdateDiscoverySourceRequest,
    ): ApiResult<DiscoverySource> {
        updateSourceCallCount++
        lastUpdateSourceRequest = request
        return updateSourceResult
    }

    override suspend fun triggerRun(sourceId: String): ApiResult<DiscoverySource> {
        triggerRunCallCount++
        return triggerRunResult
    }

    override suspend fun deleteSource(sourceId: String): ApiResult<Unit> {
        deleteSourceCallCount++
        return deleteSourceResult
    }

    override suspend fun getDeletionImpact(id: String): ApiResult<DiscoverySourceDeletionImpact> {
        getDeletionImpactCallCount++
        lastGetDeletionImpactId = id
        return getDeletionImpactResult
    }

    override suspend fun testSourceHealth(
        id: String,
        providerId: String?,
    ): ApiResult<com.inframap.frontend.domain.model.ProviderHealth> {
        testSourceHealthCallCount++
        lastTestSourceHealthId = id
        lastTestSourceHealthProviderId = providerId
        return testSourceHealthResult
    }

    companion object {
        val DEFAULT_SOURCE =
            DiscoverySource(
                id = "src-1",
                name = "Docker Network",
                sourceType = "docker",
                enabled = true,
                lastStatus = "idle",
            )
    }
}
