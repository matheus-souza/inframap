package com.inframap.frontend.domain.usecase.discovery

import com.inframap.frontend.data.api.ApiResult
import com.inframap.frontend.domain.model.DiscoverySourceDeletionImpact
import com.inframap.frontend.domain.repository.DiscoveryRepository
import com.inframap.frontend.domain.usecase.UseCase

class GetDiscoverySourceDeletionImpactUseCase(
    private val discoveryRepository: DiscoveryRepository,
) : UseCase<String, ApiResult<DiscoverySourceDeletionImpact>> {
    override suspend fun invoke(params: String): ApiResult<DiscoverySourceDeletionImpact> =
        discoveryRepository.getDeletionImpact(
            id = params,
        )
}
