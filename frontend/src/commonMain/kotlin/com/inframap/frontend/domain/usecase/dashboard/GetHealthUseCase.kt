package com.inframap.frontend.domain.usecase.dashboard

import com.inframap.frontend.data.api.ApiResult
import com.inframap.frontend.domain.model.Health
import com.inframap.frontend.domain.repository.DashboardRepository
import com.inframap.frontend.domain.usecase.NoParamUseCase

class GetHealthUseCase(
    private val dashboardRepository: DashboardRepository,
) : NoParamUseCase<ApiResult<Health>> {
    override suspend fun invoke(): ApiResult<Health> = dashboardRepository.getHealth()
}
