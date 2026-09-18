package com.inframap.frontend.domain.usecase.subnet

import com.inframap.frontend.data.api.ApiResult
import com.inframap.frontend.data.dto.DeleteSubnetResponse
import com.inframap.frontend.domain.repository.SubnetRepository
import com.inframap.frontend.domain.usecase.UseCase

class DeleteSubnetUseCase(
    private val subnetRepository: SubnetRepository,
) : UseCase<String, ApiResult<DeleteSubnetResponse>> {
    override suspend fun invoke(params: String): ApiResult<DeleteSubnetResponse> = subnetRepository.deleteSubnet(params)
}
