package com.inframap.frontend.fakes

import com.inframap.frontend.data.api.ApiResult
import com.inframap.frontend.data.dto.CreateSubnetRequest
import com.inframap.frontend.domain.model.PaginatedList
import com.inframap.frontend.domain.model.Subnet
import com.inframap.frontend.domain.repository.SubnetRepository

class FakeSubnetRepository(
    var getSubnetsResult: ApiResult<PaginatedList<Subnet>> =
        ApiResult.Success(
            PaginatedList(items = listOf(DEFAULT_SUBNET), total = 1L, page = 1, perPage = 50),
            requestId = "",
        ),
    var createSubnetResult: ApiResult<Subnet> = ApiResult.Success(DEFAULT_SUBNET, requestId = ""),
) : SubnetRepository {
    override suspend fun getSubnets() = getSubnetsResult

    override suspend fun createSubnet(request: CreateSubnetRequest) = createSubnetResult

    companion object {
        val DEFAULT_SUBNET =
            Subnet(
                id = "sub1",
                name = "homelab",
                cidr = "192.168.1.0/24",
            )
    }
}
