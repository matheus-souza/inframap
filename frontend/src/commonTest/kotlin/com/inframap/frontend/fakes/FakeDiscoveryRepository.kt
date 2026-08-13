package com.inframap.frontend.fakes

import com.inframap.frontend.data.api.ApiResult
import com.inframap.frontend.data.dto.CreateDiscoverySourceRequest
import com.inframap.frontend.domain.model.DiscoverySource
import com.inframap.frontend.domain.model.PaginatedList
import com.inframap.frontend.domain.repository.DiscoveryRepository

class FakeDiscoveryRepository(
    var getSourcesResult: ApiResult<PaginatedList<DiscoverySource>> =
        ApiResult.Success(
            PaginatedList(items = listOf(DEFAULT_SOURCE), total = 1L, page = 1, perPage = 50),
            requestId = "",
        ),
    var createSourceResult: ApiResult<DiscoverySource> = ApiResult.Success(DEFAULT_SOURCE, requestId = ""),
    var triggerRunResult: ApiResult<DiscoverySource> = ApiResult.Success(DEFAULT_SOURCE, requestId = ""),
    var deleteSourceResult: ApiResult<Unit> = ApiResult.Success(Unit, requestId = ""),
) : DiscoveryRepository {
    var getSourcesCallCount = 0
    var createSourceCallCount = 0
    var triggerRunCallCount = 0
    var deleteSourceCallCount = 0

    override suspend fun getSources(): ApiResult<PaginatedList<DiscoverySource>> {
        getSourcesCallCount++
        return getSourcesResult
    }

    override suspend fun createSource(request: CreateDiscoverySourceRequest): ApiResult<DiscoverySource> {
        createSourceCallCount++
        return createSourceResult
    }

    override suspend fun triggerRun(sourceId: String): ApiResult<DiscoverySource> {
        triggerRunCallCount++
        return triggerRunResult
    }

    override suspend fun deleteSource(sourceId: String): ApiResult<Unit> {
        deleteSourceCallCount++
        return deleteSourceResult
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
