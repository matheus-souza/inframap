package com.inframap.frontend.domain.repository

import com.inframap.frontend.data.api.ApiResult
import com.inframap.frontend.data.dto.CreateDeviceRequest
import com.inframap.frontend.data.dto.UpdateDeviceRequest
import com.inframap.frontend.domain.model.Device
import com.inframap.frontend.domain.model.PaginatedList

interface DeviceRepository {
    suspend fun getDevices(
        page: Int = 1,
        perPage: Int = 10,
        search: String = "",
    ): ApiResult<PaginatedList<Device>>

    suspend fun getDeviceById(id: String): ApiResult<Device>

    suspend fun createDevice(request: CreateDeviceRequest): ApiResult<Device>

    suspend fun updateDevice(
        id: String,
        request: UpdateDeviceRequest,
    ): ApiResult<Device>

    suspend fun deleteDevice(id: String): ApiResult<Unit>
}
