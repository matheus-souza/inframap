package com.inframap.frontend.data.repository

import com.inframap.frontend.data.api.ApiClient
import com.inframap.frontend.data.api.ApiResult
import com.inframap.frontend.data.api.map
import com.inframap.frontend.data.dto.DeviceDto
import com.inframap.frontend.data.dto.StagingListResponse
import com.inframap.frontend.data.mapper.DeviceMapper
import com.inframap.frontend.data.mapper.StagingMapper
import com.inframap.frontend.domain.model.Device
import com.inframap.frontend.domain.model.PaginatedList
import com.inframap.frontend.domain.model.StagingDevice
import com.inframap.frontend.domain.repository.StagingRepository

class StagingRepositoryImpl(
    private val apiClient: ApiClient,
) : StagingRepository {
    override suspend fun getStagingDevices(
        page: Int,
        perPage: Int,
    ): ApiResult<PaginatedList<StagingDevice>> {
        val params = mapOf("page" to page.toString(), "per_page" to perPage.toString())
        return apiClient.get<StagingListResponse>("/api/v1/devices/staging", params).map {
            StagingMapper.toPaginatedList(it)
        }
    }

    override suspend fun approveDevice(id: String): ApiResult<Device> =
        apiClient.post<DeviceDto>("/api/v1/devices/staging/$id/approve").map { DeviceMapper.toDomain(it) }

    override suspend fun dismissDevice(id: String): ApiResult<Unit> =
        apiClient.post(
            path = "/api/v1/devices/staging/$id/dismiss",
        )
}
