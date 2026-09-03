package com.inframap.frontend.data.repository

import com.inframap.frontend.data.api.ApiClient
import com.inframap.frontend.data.api.ApiResult
import com.inframap.frontend.data.api.map
import com.inframap.frontend.data.dto.CredentialListResponse
import com.inframap.frontend.domain.model.CredentialSummary
import com.inframap.frontend.domain.repository.CredentialsRepository

class CredentialsRepositoryImpl(
    private val apiClient: ApiClient,
) : CredentialsRepository {
    override suspend fun listCredentials(): ApiResult<List<CredentialSummary>> =
        apiClient
            .get<CredentialListResponse>("/api/v1/credentials")
            .map { response ->
                response.items.map { CredentialSummary(id = it.id, name = it.name, type = it.type) }
            }
}
