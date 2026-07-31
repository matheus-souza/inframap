package com.inframap.frontend.domain.usecase.dashboard

import com.inframap.frontend.data.api.ApiResult
import com.inframap.frontend.domain.model.Device
import com.inframap.frontend.domain.model.PaginatedList
import com.inframap.frontend.domain.repository.DeviceRepository
import com.inframap.frontend.domain.usecase.NoParamUseCase

class GetDeviceSummaryUseCase(
    private val deviceRepository: DeviceRepository,
) : NoParamUseCase<ApiResult<PaginatedList<Device>>> {
    override suspend fun invoke(): ApiResult<PaginatedList<Device>> = deviceRepository.getDevices(page = 1, perPage = 5)
}
