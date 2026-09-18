package com.inframap.frontend.fakes

import com.inframap.frontend.data.api.ApiResult
import com.inframap.frontend.data.dto.CreateSubnetRequest
import com.inframap.frontend.data.dto.DeleteSubnetResponse
import com.inframap.frontend.data.dto.SubnetCidrImpactRequest
import com.inframap.frontend.data.dto.SubnetCidrImpactResponse
import com.inframap.frontend.data.dto.SubnetDeletionImpact
import com.inframap.frontend.data.dto.SubnetDeletionImpactResponse
import com.inframap.frontend.data.dto.UpdateSubnetRequest
import com.inframap.frontend.domain.model.PaginatedList
import com.inframap.frontend.domain.model.Subnet
import com.inframap.frontend.domain.repository.SubnetRepository

class FakeSubnetRepository(
    var getSubnetsResult: ApiResult<PaginatedList<Subnet>> =
        ApiResult.Success(
            PaginatedList(items = listOf(DEFAULT_SUBNET), total = 1L, page = 1, perPage = 50),
            requestId = "",
        ),
    var getSubnetByIdResult: ApiResult<Subnet> = ApiResult.Success(DEFAULT_SUBNET, requestId = ""),
    var createSubnetResult: ApiResult<Subnet> = ApiResult.Success(DEFAULT_SUBNET, requestId = ""),
    var updateSubnetResult: ApiResult<Subnet> = ApiResult.Success(DEFAULT_SUBNET, requestId = ""),
    var getSubnetCidrImpactResult: ApiResult<SubnetCidrImpactResponse> =
        ApiResult.Success(
            SubnetCidrImpactResponse(
                currentCidr = "192.168.1.0/24",
                newCidr = "192.168.1.0/25",
                affectedDevicesCount = 0,
            ),
            requestId = "",
        ),
    var getSubnetDeletionImpactResult: ApiResult<SubnetDeletionImpactResponse> =
        ApiResult.Success(
            SubnetDeletionImpactResponse(
                subnetId = "sub1",
                impact = SubnetDeletionImpact(affectedDevices = 0, unlinkedTopologyEdges = 0),
            ),
            requestId = "",
        ),
    var deleteSubnetResult: ApiResult<DeleteSubnetResponse> =
        ApiResult.Success(
            DeleteSubnetResponse(
                deletedId = "sub1",
                impact = SubnetDeletionImpact(affectedDevices = 0, unlinkedTopologyEdges = 0),
            ),
            requestId = "",
        ),
) : SubnetRepository {
    override suspend fun getSubnets() = getSubnetsResult

    override suspend fun getSubnetById(id: String) = getSubnetByIdResult

    override suspend fun createSubnet(request: CreateSubnetRequest) = createSubnetResult

    override suspend fun updateSubnet(
        id: String,
        request: UpdateSubnetRequest,
    ) = updateSubnetResult

    override suspend fun getSubnetCidrImpact(
        id: String,
        request: SubnetCidrImpactRequest,
    ) = getSubnetCidrImpactResult

    override suspend fun getSubnetDeletionImpact(id: String) = getSubnetDeletionImpactResult

    override suspend fun deleteSubnet(id: String) = deleteSubnetResult

    companion object {
        val DEFAULT_SUBNET =
            Subnet(
                id = "sub1",
                name = "homelab",
                cidr = "192.168.1.0/24",
            )
    }
}
