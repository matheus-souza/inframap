package com.inframap.frontend.domain.usecase.staging

import com.inframap.frontend.data.api.ApiResult
import com.inframap.frontend.domain.model.Device
import com.inframap.frontend.domain.repository.StagingRepository
import com.inframap.frontend.domain.usecase.UseCase

class ApproveDeviceUseCase(
    private val stagingRepository: StagingRepository,
) : UseCase<String, ApiResult<Device>> {
    override suspend fun invoke(params: String): ApiResult<Device> = stagingRepository.approveDevice(params)
}
