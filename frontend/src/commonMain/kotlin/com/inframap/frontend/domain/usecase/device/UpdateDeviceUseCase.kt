package com.inframap.frontend.domain.usecase.device

import com.inframap.frontend.data.api.ApiResult
import com.inframap.frontend.data.dto.UpdateDeviceRequest
import com.inframap.frontend.domain.model.Device
import com.inframap.frontend.domain.repository.DeviceRepository
import com.inframap.frontend.domain.usecase.UseCase

class UpdateDeviceUseCase(
    private val deviceRepository: DeviceRepository,
) : UseCase<UpdateDeviceUseCase.Params, ApiResult<Device>> {
    data class Params(
        val id: String,
        val request: UpdateDeviceRequest,
    )

    override suspend fun invoke(params: Params): ApiResult<Device> =
        deviceRepository.updateDevice(
            id = params.id,
            request = params.request,
        )

    @Suppress("LongParameterList")
    suspend operator fun invoke(
        id: String,
        hostname: String? = null,
        ipAddress: String? = null,
        macAddress: String? = null,
        deviceType: String? = null,
        status: String? = null,
    ): ApiResult<Device> =
        invoke(
            Params(
                id = id,
                request =
                    UpdateDeviceRequest(
                        hostname = hostname,
                        ipAddress = ipAddress,
                        macAddress = macAddress,
                        deviceType = deviceType,
                        status = status,
                    ),
            ),
        )
}
