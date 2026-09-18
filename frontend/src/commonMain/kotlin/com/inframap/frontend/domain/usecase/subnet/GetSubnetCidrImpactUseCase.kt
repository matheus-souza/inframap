package com.inframap.frontend.domain.usecase.subnet

import com.inframap.frontend.data.api.ApiResult
import com.inframap.frontend.data.dto.SubnetCidrImpactRequest
import com.inframap.frontend.data.dto.SubnetCidrImpactResponse
import com.inframap.frontend.domain.repository.SubnetRepository
import com.inframap.frontend.domain.usecase.UseCase

class GetSubnetCidrImpactUseCase(
    private val subnetRepository: SubnetRepository,
) : UseCase<GetSubnetCidrImpactUseCase.Params, ApiResult<SubnetCidrImpactResponse>> {
    data class Params(
        val id: String,
        val newCidr: String,
    )

    override suspend fun invoke(params: Params): ApiResult<SubnetCidrImpactResponse> =
        subnetRepository.getSubnetCidrImpact(
            id = params.id,
            request = SubnetCidrImpactRequest(newCidr = params.newCidr),
        )

    suspend operator fun invoke(
        id: String,
        newCidr: String,
    ): ApiResult<SubnetCidrImpactResponse> = invoke(Params(id = id, newCidr = newCidr))
}
