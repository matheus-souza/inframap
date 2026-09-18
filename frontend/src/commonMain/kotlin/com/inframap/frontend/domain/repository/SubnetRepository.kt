package com.inframap.frontend.domain.repository

import com.inframap.frontend.data.api.ApiResult
import com.inframap.frontend.data.dto.CreateSubnetRequest
import com.inframap.frontend.data.dto.DeleteSubnetResponse
import com.inframap.frontend.data.dto.SubnetCidrImpactRequest
import com.inframap.frontend.data.dto.SubnetCidrImpactResponse
import com.inframap.frontend.data.dto.SubnetDeletionImpactResponse
import com.inframap.frontend.data.dto.UpdateSubnetRequest
import com.inframap.frontend.domain.model.PaginatedList
import com.inframap.frontend.domain.model.Subnet

interface SubnetRepository {
    suspend fun getSubnets(): ApiResult<PaginatedList<Subnet>>

    suspend fun getSubnetById(id: String): ApiResult<Subnet>

    suspend fun createSubnet(request: CreateSubnetRequest): ApiResult<Subnet>

    suspend fun updateSubnet(
        id: String,
        request: UpdateSubnetRequest,
    ): ApiResult<Subnet>

    suspend fun getSubnetCidrImpact(
        id: String,
        request: SubnetCidrImpactRequest,
    ): ApiResult<SubnetCidrImpactResponse>

    suspend fun getSubnetDeletionImpact(id: String): ApiResult<SubnetDeletionImpactResponse>

    suspend fun deleteSubnet(id: String): ApiResult<DeleteSubnetResponse>
}
