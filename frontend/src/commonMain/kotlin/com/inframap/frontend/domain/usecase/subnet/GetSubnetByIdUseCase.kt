package com.inframap.frontend.domain.usecase.subnet

import com.inframap.frontend.data.api.ApiResult
import com.inframap.frontend.domain.model.Subnet
import com.inframap.frontend.domain.repository.SubnetRepository
import com.inframap.frontend.domain.usecase.UseCase

class GetSubnetByIdUseCase(
    private val subnetRepository: SubnetRepository,
) : UseCase<String, ApiResult<Subnet>> {
    override suspend fun invoke(params: String): ApiResult<Subnet> = subnetRepository.getSubnetById(params)
}
