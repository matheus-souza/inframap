package com.inframap.frontend.domain.usecase.subnet

import com.inframap.frontend.data.api.ApiResult
import com.inframap.frontend.domain.model.SubnetAddressMap
import com.inframap.frontend.domain.repository.SubnetRepository

class GetSubnetAddressMapUseCase(
    private val subnetRepository: SubnetRepository,
) {
    suspend operator fun invoke(subnetId: String): ApiResult<SubnetAddressMap> =
        subnetRepository.getSubnetAddressMap(subnetId)
}
