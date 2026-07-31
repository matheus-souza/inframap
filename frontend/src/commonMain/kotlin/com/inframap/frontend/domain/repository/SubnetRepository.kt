package com.inframap.frontend.domain.repository

import com.inframap.frontend.data.api.ApiResult
import com.inframap.frontend.data.dto.CreateSubnetRequest
import com.inframap.frontend.domain.model.PaginatedList
import com.inframap.frontend.domain.model.Subnet

interface SubnetRepository {
    suspend fun getSubnets(): ApiResult<PaginatedList<Subnet>>

    suspend fun createSubnet(request: CreateSubnetRequest): ApiResult<Subnet>
}
