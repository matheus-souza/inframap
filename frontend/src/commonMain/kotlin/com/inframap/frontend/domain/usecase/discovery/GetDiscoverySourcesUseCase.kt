package com.inframap.frontend.domain.usecase.discovery

import com.inframap.frontend.data.api.ApiResult
import com.inframap.frontend.domain.model.DiscoverySource
import com.inframap.frontend.domain.model.PaginatedList
import com.inframap.frontend.domain.repository.DiscoveryRepository
import com.inframap.frontend.domain.usecase.NoParamUseCase

class GetDiscoverySourcesUseCase(
    private val discoveryRepository: DiscoveryRepository,
) : NoParamUseCase<ApiResult<PaginatedList<DiscoverySource>>> {
    override suspend fun invoke(): ApiResult<PaginatedList<DiscoverySource>> = discoveryRepository.getSources()
}
