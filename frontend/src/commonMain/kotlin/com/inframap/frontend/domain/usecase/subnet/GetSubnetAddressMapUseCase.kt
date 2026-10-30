package com.inframap.frontend.domain.usecase.subnet

import com.inframap.frontend.data.api.ApiResult
import com.inframap.frontend.data.dto.SubnetAddressMapResponse
import com.inframap.frontend.domain.repository.SubnetRepository
import com.inframap.frontend.domain.usecase.UseCase

class GetSubnetAddressMapUseCase(
    private val subnetRepository: SubnetRepository,
) : UseCase<String, ApiResult<SubnetAddressMapResponse>> {
    override suspend fun invoke(params: String) = subnetRepository.getSubnetAddressMap(params)
}
