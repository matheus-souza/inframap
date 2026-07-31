package com.inframap.frontend.data.repository

import com.inframap.frontend.data.api.ApiClient
import com.inframap.frontend.data.api.ApiResult
import com.inframap.frontend.data.dto.CreateDeviceRequest
import com.inframap.frontend.data.dto.DeviceDto
import com.inframap.frontend.data.dto.DeviceListResponse
import com.inframap.frontend.data.dto.UpdateDeviceRequest
import com.inframap.frontend.data.mapper.DeviceMapper
import com.inframap.frontend.domain.model.Device
import com.inframap.frontend.domain.model.PaginatedList
import com.inframap.frontend.domain.repository.DeviceRepository

class DeviceRepositoryImpl(
    private val apiClient: ApiClient,
) : DeviceRepository {
    override suspend fun getDevices(
        page: Int,
        perPage: Int,
        search: String,
    ): ApiResult<PaginatedList<Device>> {
        val params = mutableMapOf("page" to page.toString(), "per_page" to perPage.toString())
        if (search.isNotBlank()) {
            params["search"] = search.trim()
        }
        val result = apiClient.get<DeviceListResponse>("/api/v1/devices", params)
        return when (result) {
            is ApiResult.Success -> ApiResult.Success(DeviceMapper.toPaginatedList(result.data), result.requestId)
            is ApiResult.Error -> ApiResult.Error(result.code, result.message, result.requestId, result.httpStatus)
            is ApiResult.NetworkError -> ApiResult.NetworkError(result.throwable)
        }
    }

    override suspend fun getDeviceById(id: String): ApiResult<Device> {
        val result = apiClient.get<DeviceDto>("/api/v1/devices/$id")
        return when (result) {
            is ApiResult.Success -> ApiResult.Success(DeviceMapper.toDomain(result.data), result.requestId)
            is ApiResult.Error -> ApiResult.Error(result.code, result.message, result.requestId, result.httpStatus)
            is ApiResult.NetworkError -> ApiResult.NetworkError(result.throwable)
        }
    }

    override suspend fun createDevice(request: CreateDeviceRequest): ApiResult<Device> {
        val result = apiClient.post<DeviceDto, CreateDeviceRequest>("/api/v1/devices", request)
        return when (result) {
            is ApiResult.Success -> ApiResult.Success(DeviceMapper.toDomain(result.data), result.requestId)
            is ApiResult.Error -> ApiResult.Error(result.code, result.message, result.requestId, result.httpStatus)
            is ApiResult.NetworkError -> ApiResult.NetworkError(result.throwable)
        }
    }

    override suspend fun updateDevice(
        id: String,
        request: UpdateDeviceRequest,
    ): ApiResult<Device> {
        val result = apiClient.put<DeviceDto, UpdateDeviceRequest>("/api/v1/devices/$id", request)
        return when (result) {
            is ApiResult.Success -> ApiResult.Success(DeviceMapper.toDomain(result.data), result.requestId)
            is ApiResult.Error -> ApiResult.Error(result.code, result.message, result.requestId, result.httpStatus)
            is ApiResult.NetworkError -> ApiResult.NetworkError(result.throwable)
        }
    }

    override suspend fun deleteDevice(id: String): ApiResult<Unit> = apiClient.delete("/api/v1/devices/$id")
}
