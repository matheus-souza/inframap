package com.inframap.frontend.domain.repository

import com.inframap.frontend.data.api.ApiResult
import com.inframap.frontend.domain.model.CredentialSummary

interface CredentialsRepository {
    suspend fun listCredentials(): ApiResult<List<CredentialSummary>>
}
