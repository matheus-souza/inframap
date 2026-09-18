package com.inframap.frontend.domain.usecase.subnet

import com.inframap.frontend.data.api.ApiResult
import com.inframap.frontend.data.dto.SubnetDeletionImpactResponse
import com.inframap.frontend.domain.repository.SubnetRepository
import com.inframap.frontend.domain.usecase.UseCase

class GetSubnetDeletionImpactUseCase(
    private val subnetRepository: SubnetRepository,
) : UseCase<String, ApiResult<SubnetDeletionImpactResponse>> {
    override suspend fun invoke(params: String): ApiResult<SubnetDeletionImpactResponse> =
        subnetRepository.getSubnetDeletionImpact(
            id = params,
        )
}
