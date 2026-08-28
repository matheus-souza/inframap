package com.inframap.frontend.domain.usecase.subnet

import com.inframap.frontend.data.api.ApiResult
import com.inframap.frontend.domain.model.PaginatedList
import com.inframap.frontend.domain.model.Subnet
import com.inframap.frontend.domain.repository.SubnetRepository
import com.inframap.frontend.domain.usecase.NoParamUseCase

class ListSubnetsUseCase(
    private val subnetRepository: SubnetRepository,
) : NoParamUseCase<ApiResult<PaginatedList<Subnet>>> {
    override suspend fun invoke(): ApiResult<PaginatedList<Subnet>> = subnetRepository.getSubnets()
}
