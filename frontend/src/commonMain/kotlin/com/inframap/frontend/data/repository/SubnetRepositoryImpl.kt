package com.inframap.frontend.data.repository

import com.inframap.frontend.data.api.ApiClient
import com.inframap.frontend.data.api.ApiResult
import com.inframap.frontend.data.api.map
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
    override suspend fun getSubnets(): ApiResult<PaginatedList<Subnet>> =
        apiClient.get<SubnetListResponse>("/api/v1/subnets").map { SubnetMapper.toPaginatedList(it) }

    override suspend fun createSubnet(request: CreateSubnetRequest): ApiResult<Subnet> =
        apiClient.post<SubnetDto, CreateSubnetRequest>("/api/v1/subnets", request).map { SubnetMapper.toDomain(it) }
}
