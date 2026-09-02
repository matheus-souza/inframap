package com.inframap.frontend.domain.repository

import com.inframap.frontend.data.api.ApiResult
import com.inframap.frontend.domain.model.ProviderHealth

interface IntegrationsRepository {
    suspend fun testProviderHealth(
        providerId: String,
        config: Map<String, String>,
    ): ApiResult<ProviderHealth>
}
