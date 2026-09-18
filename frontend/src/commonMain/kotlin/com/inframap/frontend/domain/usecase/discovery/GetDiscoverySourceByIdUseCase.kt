package com.inframap.frontend.domain.usecase.discovery

import com.inframap.frontend.data.api.ApiResult
import com.inframap.frontend.domain.model.DiscoverySource
import com.inframap.frontend.domain.repository.DiscoveryRepository
import com.inframap.frontend.domain.usecase.UseCase

class GetDiscoverySourceByIdUseCase(
    private val discoveryRepository: DiscoveryRepository,
) : UseCase<String, ApiResult<DiscoverySource>> {
    override suspend fun invoke(params: String): ApiResult<DiscoverySource> {
        if (params.isBlank()) {
            return ApiResult.Error(
                code = "INVALID_ID",
                message = "ID da fonte de descoberta é obrigatório",
                requestId = "",
                httpStatus = 400,
            )
        }
        return discoveryRepository.getSourceById(params)
    }
}
