package com.inframap.frontend.domain.usecase.integrations

import com.inframap.frontend.data.api.ApiResult
import com.inframap.frontend.domain.model.ProviderHealth
import com.inframap.frontend.domain.repository.IntegrationsRepository

class TestProviderHealthUseCase(
    private val integrationsRepository: IntegrationsRepository,
) {
    suspend operator fun invoke(
        providerId: String,
        config: Map<String, String>,
    ): ApiResult<ProviderHealth> {
        // The backend rejects an empty config, so a check with nothing filled in would come
        // back as a generic 400 that reads like a connection failure.
        val filled = config.filterValues { it.isNotBlank() }
        if (filled.isEmpty()) {
            return ApiResult.Error(
                code = "INVALID_INPUT",
                message = "Preencha os campos do provedor antes de testar a conexão",
                requestId = "",
                httpStatus = 400,
            )
        }
        return integrationsRepository.testProviderHealth(providerId, filled)
    }
}
