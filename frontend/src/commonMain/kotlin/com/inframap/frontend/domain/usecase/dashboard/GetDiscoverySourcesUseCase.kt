package com.inframap.frontend.domain.usecase.dashboard

import com.inframap.frontend.data.api.ApiResult
import com.inframap.frontend.domain.model.DiscoverySource
import com.inframap.frontend.domain.repository.DashboardRepository
import com.inframap.frontend.domain.usecase.NoParamUseCase

class GetDiscoverySourcesUseCase(
    private val dashboardRepository: DashboardRepository,
) : NoParamUseCase<ApiResult<List<DiscoverySource>>> {
    override suspend fun invoke(): ApiResult<List<DiscoverySource>> = dashboardRepository.getDiscoverySources()
}
