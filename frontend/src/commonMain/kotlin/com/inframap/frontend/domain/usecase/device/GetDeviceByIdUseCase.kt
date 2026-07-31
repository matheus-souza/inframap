package com.inframap.frontend.domain.usecase.device

import com.inframap.frontend.data.api.ApiResult
import com.inframap.frontend.domain.model.Device
import com.inframap.frontend.domain.repository.DeviceRepository
import com.inframap.frontend.domain.usecase.UseCase

class GetDeviceByIdUseCase(
    private val deviceRepository: DeviceRepository,
) : UseCase<String, ApiResult<Device>> {
    override suspend fun invoke(params: String): ApiResult<Device> = deviceRepository.getDeviceById(params)
}
