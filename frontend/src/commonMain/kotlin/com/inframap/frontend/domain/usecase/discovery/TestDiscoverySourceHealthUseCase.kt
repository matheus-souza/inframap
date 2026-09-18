package com.inframap.frontend.domain.usecase.discovery

import com.inframap.frontend.data.api.ApiResult
import com.inframap.frontend.domain.model.ProviderHealth
import com.inframap.frontend.domain.repository.DiscoveryRepository

data class TestDiscoverySourceHealthParams(
    val sourceId: String,
    val providerId: String? = null,
)

class TestDiscoverySourceHealthUseCase(
    private val discoveryRepository: DiscoveryRepository,
) {
    suspend operator fun invoke(
        sourceId: String,
        providerId: String? = null,
    ): ApiResult<ProviderHealth> {
        if (sourceId.isBlank()) {
            return ApiResult.Error(
                code = "INVALID_ID",
                message = "ID da fonte de descoberta é obrigatório",
                requestId = "",
                httpStatus = 400,
            )
        }
        return discoveryRepository.testSourceHealth(sourceId, providerId)
    }

    suspend operator fun invoke(params: TestDiscoverySourceHealthParams): ApiResult<ProviderHealth> =
        invoke(params.sourceId, params.providerId)
}
