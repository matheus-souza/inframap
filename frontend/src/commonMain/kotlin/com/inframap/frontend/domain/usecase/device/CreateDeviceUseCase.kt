package com.inframap.frontend.domain.usecase.device

import com.inframap.frontend.data.api.ApiResult
import com.inframap.frontend.data.dto.CreateDeviceRequest
import com.inframap.frontend.domain.model.Device
import com.inframap.frontend.domain.repository.DeviceRepository
import com.inframap.frontend.domain.usecase.UseCase

class CreateDeviceUseCase(
    private val deviceRepository: DeviceRepository,
) : UseCase<CreateDeviceRequest, ApiResult<Device>> {
    override suspend fun invoke(params: CreateDeviceRequest): ApiResult<Device> = deviceRepository.createDevice(params)

    suspend operator fun invoke(
        hostname: String,
        deviceType: String,
        ipAddress: String? = null,
        macAddress: String? = null,
    ): ApiResult<Device> =
        invoke(
            CreateDeviceRequest(
                hostname = hostname,
                deviceType = deviceType,
                ipAddress = ipAddress,
                macAddress = macAddress,
            ),
        )
}
