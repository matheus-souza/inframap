package com.inframap.frontend.domain.usecase.discovery

import com.inframap.frontend.data.api.ApiResult
import com.inframap.frontend.data.dto.CreateDiscoverySourceRequest
import com.inframap.frontend.domain.model.DiscoverySource
import com.inframap.frontend.domain.repository.DiscoveryRepository
import com.inframap.frontend.domain.usecase.UseCase

class CreateDiscoverySourceUseCase(
    private val discoveryRepository: DiscoveryRepository,
) : UseCase<CreateDiscoverySourceRequest, ApiResult<DiscoverySource>> {
    override suspend fun invoke(params: CreateDiscoverySourceRequest): ApiResult<DiscoverySource> {
        if (params.name.isBlank()) {
            return ApiResult.Error(
                code = "INVALID_NAME",
                message = "Nome da fonte de descoberta é obrigatório",
                requestId = "",
                httpStatus = 400,
            )
        }
        return discoveryRepository.createSource(params)
    }
}
