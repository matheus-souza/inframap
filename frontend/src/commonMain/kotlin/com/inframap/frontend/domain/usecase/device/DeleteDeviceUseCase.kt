package com.inframap.frontend.domain.usecase.device

import com.inframap.frontend.data.api.ApiResult
import com.inframap.frontend.domain.repository.DeviceRepository
import com.inframap.frontend.domain.usecase.UseCase

class DeleteDeviceUseCase(
    private val deviceRepository: DeviceRepository,
) : UseCase<String, ApiResult<Unit>> {
    override suspend fun invoke(params: String): ApiResult<Unit> = deviceRepository.deleteDevice(params)
}
