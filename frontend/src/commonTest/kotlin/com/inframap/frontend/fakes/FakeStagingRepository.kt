package com.inframap.frontend.fakes

import com.inframap.frontend.data.api.ApiResult
import com.inframap.frontend.domain.model.Device
import com.inframap.frontend.domain.model.PaginatedList
import com.inframap.frontend.domain.model.StagingDevice
import com.inframap.frontend.domain.repository.StagingRepository

class FakeStagingRepository(
    var getStagingDevicesResult: ApiResult<PaginatedList<StagingDevice>> =
        ApiResult.Success(
            PaginatedList(items = emptyList(), total = 0L, page = 1, perPage = 50),
            requestId = "",
        ),
    var approveDeviceResult: ApiResult<Device> =
        ApiResult.Success(FakeDeviceRepository.DEFAULT_DEVICE, requestId = ""),
    var dismissDeviceResult: ApiResult<Unit> = ApiResult.Success(Unit, requestId = ""),
) : StagingRepository {
    override suspend fun getStagingDevices(
        page: Int,
        perPage: Int,
    ) = getStagingDevicesResult

    override suspend fun approveDevice(id: String) = approveDeviceResult

    override suspend fun dismissDevice(id: String) = dismissDeviceResult

    companion object {
        val DEFAULT_STAGING_DEVICE = StagingDevice(id = "st1", hostname = "pending-01", deviceType = "switch")
    }
}
