package com.inframap.frontend.data.repository

import com.inframap.frontend.data.api.ApiClient
import com.inframap.frontend.data.api.ApiResult
import com.inframap.frontend.data.api.map
import com.inframap.frontend.data.dto.ProviderHealthRequestDto
import com.inframap.frontend.data.dto.ProviderHealthResponseDto
import com.inframap.frontend.domain.model.ProviderHealth
import com.inframap.frontend.domain.repository.IntegrationsRepository

class IntegrationsRepositoryImpl(
    private val apiClient: ApiClient,
) : IntegrationsRepository {
    override suspend fun testProviderHealth(
        providerId: String,
        config: Map<String, String>,
    ): ApiResult<ProviderHealth> =
        apiClient
            .post<ProviderHealthResponseDto, ProviderHealthRequestDto>(
                "/api/v1/integrations/providers/$providerId/health",
                ProviderHealthRequestDto(config = config),
            ).map {
                ProviderHealth(
                    providerId = it.providerId.ifEmpty { providerId },
                    // The endpoint returns 200 even when the provider is unreachable and
                    // reports the verdict in the body, so the status field is what decides.
                    isHealthy = it.status == "ok",
                    message = it.message,
                )
            }
}
