package com.inframap.frontend.data.repository

import com.inframap.frontend.data.api.ApiClient
import com.inframap.frontend.data.api.ApiResult
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
        val result = apiClient.get<StagingListResponse>("/api/v1/devices/staging", params)
        return when (result) {
            is ApiResult.Success -> ApiResult.Success(StagingMapper.toPaginatedList(result.data), result.requestId)
            is ApiResult.Error -> ApiResult.Error(result.code, result.message, result.requestId, result.httpStatus)
            is ApiResult.NetworkError -> ApiResult.NetworkError(result.throwable)
        }
    }

    override suspend fun approveDevice(id: String): ApiResult<Device> {
        val result = apiClient.post<DeviceDto>("/api/v1/devices/staging/$id/approve")
        return when (result) {
            is ApiResult.Success -> ApiResult.Success(DeviceMapper.toDomain(result.data), result.requestId)
            is ApiResult.Error -> ApiResult.Error(result.code, result.message, result.requestId, result.httpStatus)
            is ApiResult.NetworkError -> ApiResult.NetworkError(result.throwable)
        }
    }

    override suspend fun dismissDevice(id: String): ApiResult<Unit> =
        apiClient.post(
            path = "/api/v1/devices/staging/$id/dismiss",
        )
}
