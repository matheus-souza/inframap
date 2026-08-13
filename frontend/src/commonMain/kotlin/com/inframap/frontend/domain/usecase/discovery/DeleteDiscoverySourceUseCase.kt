package com.inframap.frontend.domain.usecase.discovery

import com.inframap.frontend.data.api.ApiResult
import com.inframap.frontend.domain.repository.DiscoveryRepository
import com.inframap.frontend.domain.usecase.UseCase

class DeleteDiscoverySourceUseCase(
    private val discoveryRepository: DiscoveryRepository,
) : UseCase<String, ApiResult<Unit>> {
    override suspend fun invoke(params: String): ApiResult<Unit> = discoveryRepository.deleteSource(params)
}
