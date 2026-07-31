package com.inframap.frontend.domain.usecase.dashboard

import com.inframap.frontend.data.api.ApiResult
import com.inframap.frontend.domain.model.PaginatedList
import com.inframap.frontend.domain.model.StagingDevice
import com.inframap.frontend.domain.repository.DashboardRepository
import com.inframap.frontend.domain.usecase.NoParamUseCase

class GetStagingSummaryUseCase(
    private val dashboardRepository: DashboardRepository,
) : NoParamUseCase<ApiResult<PaginatedList<StagingDevice>>> {
    override suspend fun invoke(): ApiResult<PaginatedList<StagingDevice>> = dashboardRepository.getStagingSummary()
}
