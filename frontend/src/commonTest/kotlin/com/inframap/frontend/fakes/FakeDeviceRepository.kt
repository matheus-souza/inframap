package com.inframap.frontend.fakes

import com.inframap.frontend.data.api.ApiResult
import com.inframap.frontend.data.dto.CreateDeviceRequest
import com.inframap.frontend.data.dto.UpdateDeviceRequest
import com.inframap.frontend.domain.model.Device
import com.inframap.frontend.domain.model.PaginatedList
import com.inframap.frontend.domain.repository.DeviceRepository

class FakeDeviceRepository(
    var getDevicesResult: ApiResult<PaginatedList<Device>> =
        ApiResult.Success(
            PaginatedList(items = listOf(DEFAULT_DEVICE), total = 1L, page = 1, perPage = 10),
            requestId = "",
        ),
    var getDeviceByIdResult: ApiResult<Device> = ApiResult.Success(DEFAULT_DEVICE, requestId = ""),
    var createDeviceResult: ApiResult<Device> = ApiResult.Success(DEFAULT_DEVICE, requestId = ""),
    var updateDeviceResult: ApiResult<Device> = ApiResult.Success(DEFAULT_DEVICE, requestId = ""),
    var deleteDeviceResult: ApiResult<Unit> = ApiResult.Success(Unit, requestId = ""),
) : DeviceRepository {
    var getDevicesCallCount = 0

    override suspend fun getDevices(
        page: Int,
        perPage: Int,
        search: String,
    ): ApiResult<PaginatedList<Device>> {
        getDevicesCallCount++
        return getDevicesResult
    }

    override suspend fun getDeviceById(id: String) = getDeviceByIdResult

    override suspend fun createDevice(request: CreateDeviceRequest) = createDeviceResult

    override suspend fun updateDevice(
        id: String,
        request: UpdateDeviceRequest,
    ) = updateDeviceResult

    override suspend fun deleteDevice(id: String) = deleteDeviceResult

    companion object {
        val DEFAULT_DEVICE = Device(id = "d1", hostname = "router-01", deviceType = "router", status = "active")
    }
}
