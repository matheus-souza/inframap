package com.inframap.frontend.domain.repository

import com.inframap.frontend.data.api.ApiResult
import com.inframap.frontend.domain.model.Device
import com.inframap.frontend.domain.model.PaginatedList
import com.inframap.frontend.domain.model.StagingDevice

interface StagingRepository {
    suspend fun getStagingDevices(
        page: Int = 1,
        perPage: Int = 10,
    ): ApiResult<PaginatedList<StagingDevice>>

    suspend fun approveDevice(id: String): ApiResult<Device>

    suspend fun dismissDevice(id: String): ApiResult<Unit>
}
