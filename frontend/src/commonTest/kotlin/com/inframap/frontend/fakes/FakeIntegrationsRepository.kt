package com.inframap.frontend.fakes

import com.inframap.frontend.data.api.ApiResult
import com.inframap.frontend.domain.model.ProviderHealth
import com.inframap.frontend.domain.repository.IntegrationsRepository

class FakeIntegrationsRepository(
    var healthResult: ApiResult<ProviderHealth> =
        ApiResult.Success(
            ProviderHealth(providerId = "docker", isHealthy = true, message = null),
            requestId = "",
        ),
) : IntegrationsRepository {
    /** Configurations the view model sent, in call order, so tests can assert what was checked. */
    val calls = mutableListOf<Pair<String, Map<String, String>>>()

    override suspend fun testProviderHealth(
        providerId: String,
        config: Map<String, String>,
    ): ApiResult<ProviderHealth> {
        calls += providerId to config
        return healthResult
    }
}
