package com.inframap.frontend.fakes

import com.inframap.frontend.data.api.ApiResult
import com.inframap.frontend.domain.model.DiscoverySource
import com.inframap.frontend.domain.model.Health
import com.inframap.frontend.domain.model.PaginatedList
import com.inframap.frontend.domain.model.StagingDevice
import com.inframap.frontend.domain.repository.DashboardRepository

class FakeDashboardRepository(
    var getHealthResult: ApiResult<Health> =
        ApiResult.Success(DEFAULT_HEALTH, requestId = ""),
    var getStagingSummaryResult: ApiResult<PaginatedList<StagingDevice>> =
        ApiResult.Success(
            PaginatedList(
                items = listOf(FakeStagingRepository.DEFAULT_STAGING_DEVICE),
                total = 1,
                page = 1,
                perPage = 50,
            ),
            requestId = "",
        ),
    var getDiscoverySourcesResult: ApiResult<List<DiscoverySource>> =
        ApiResult.Success(listOf(DEFAULT_SOURCE, DEFAULT_SOURCE), requestId = ""),
) : DashboardRepository {
    override suspend fun getHealth() = getHealthResult

    override suspend fun getStagingSummary() = getStagingSummaryResult

    override suspend fun getDiscoverySources() = getDiscoverySourcesResult

    companion object {
        val DEFAULT_HEALTH = Health(status = "ok", version = "v1.2.3")
        val DEFAULT_SOURCE = DiscoverySource(id = "src1", name = "homelab-subnet")
    }
}
