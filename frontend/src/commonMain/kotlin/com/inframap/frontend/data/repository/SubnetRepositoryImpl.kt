package com.inframap.frontend.data.repository

import com.inframap.frontend.data.api.ApiClient
import com.inframap.frontend.data.api.ApiResult
import com.inframap.frontend.data.api.map
import com.inframap.frontend.data.dto.CreateSubnetRequest
import com.inframap.frontend.data.dto.DeleteSubnetResponse
import com.inframap.frontend.data.dto.SubnetCidrImpactRequest
import com.inframap.frontend.data.dto.SubnetCidrImpactResponse
import com.inframap.frontend.data.dto.SubnetDeletionImpactResponse
import com.inframap.frontend.data.dto.SubnetDto
import com.inframap.frontend.data.dto.SubnetListResponse
import com.inframap.frontend.data.dto.UpdateSubnetRequest
import com.inframap.frontend.data.mapper.SubnetMapper
import com.inframap.frontend.domain.model.PaginatedList
import com.inframap.frontend.domain.model.Subnet
import com.inframap.frontend.domain.repository.SubnetRepository

class SubnetRepositoryImpl(
    private val apiClient: ApiClient,
) : SubnetRepository {
    override suspend fun getSubnets(): ApiResult<PaginatedList<Subnet>> =
        apiClient.get<SubnetListResponse>("/api/v1/subnets").map { SubnetMapper.toPaginatedList(it) }

    override suspend fun getSubnetById(id: String): ApiResult<Subnet> =
        apiClient.get<SubnetDto>("/api/v1/subnets/$id").map { SubnetMapper.toDomain(it) }

    override suspend fun createSubnet(request: CreateSubnetRequest): ApiResult<Subnet> =
        apiClient.post<SubnetDto, CreateSubnetRequest>("/api/v1/subnets", request).map { SubnetMapper.toDomain(it) }

    override suspend fun updateSubnet(
        id: String,
        request: UpdateSubnetRequest,
    ): ApiResult<Subnet> =
        apiClient
            .put<SubnetDto, UpdateSubnetRequest>("/api/v1/subnets/$id", request)
            .map { SubnetMapper.toDomain(it) }

    override suspend fun getSubnetCidrImpact(
        id: String,
        request: SubnetCidrImpactRequest,
    ): ApiResult<SubnetCidrImpactResponse> =
        apiClient.post<SubnetCidrImpactResponse, SubnetCidrImpactRequest>(
            "/api/v1/subnets/$id/cidr-impact",
            request,
        )

    override suspend fun getSubnetDeletionImpact(id: String): ApiResult<SubnetDeletionImpactResponse> =
        apiClient.get<SubnetDeletionImpactResponse>("/api/v1/subnets/$id/deletion-impact")

    override suspend fun deleteSubnet(id: String): ApiResult<DeleteSubnetResponse> =
        apiClient.delete<DeleteSubnetResponse>("/api/v1/subnets/$id")
}
