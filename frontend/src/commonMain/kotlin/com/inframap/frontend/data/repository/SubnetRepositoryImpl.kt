package com.inframap.frontend.data.repository

import com.inframap.frontend.data.api.ApiClient
import com.inframap.frontend.data.api.ApiResult
import com.inframap.frontend.data.dto.CreateSubnetRequest
import com.inframap.frontend.data.dto.SubnetDto
import com.inframap.frontend.data.dto.SubnetListResponse
import com.inframap.frontend.data.mapper.SubnetMapper
import com.inframap.frontend.domain.model.PaginatedList
import com.inframap.frontend.domain.model.Subnet
import com.inframap.frontend.domain.repository.SubnetRepository

class SubnetRepositoryImpl(
    private val apiClient: ApiClient,
) : SubnetRepository {
    override suspend fun getSubnets(): ApiResult<PaginatedList<Subnet>> {
        val result = apiClient.get<SubnetListResponse>("/api/v1/subnets")
        return when (result) {
            is ApiResult.Success -> ApiResult.Success(SubnetMapper.toPaginatedList(result.data), result.requestId)
            is ApiResult.Error -> ApiResult.Error(result.code, result.message, result.requestId, result.httpStatus)
            is ApiResult.NetworkError -> ApiResult.NetworkError(result.throwable)
        }
    }

    override suspend fun createSubnet(request: CreateSubnetRequest): ApiResult<Subnet> {
        val result = apiClient.post<SubnetDto, CreateSubnetRequest>("/api/v1/subnets", request)
        return when (result) {
            is ApiResult.Success -> ApiResult.Success(SubnetMapper.toDomain(result.data), result.requestId)
            is ApiResult.Error -> ApiResult.Error(result.code, result.message, result.requestId, result.httpStatus)
            is ApiResult.NetworkError -> ApiResult.NetworkError(result.throwable)
        }
    }
}
