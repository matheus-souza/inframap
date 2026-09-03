package com.inframap.frontend.fakes

import com.inframap.frontend.data.api.ApiResult
import com.inframap.frontend.domain.model.CredentialSummary
import com.inframap.frontend.domain.repository.CredentialsRepository

class FakeCredentialsRepository(
    var listResult: ApiResult<List<CredentialSummary>> =
        ApiResult.Success(
            listOf(CredentialSummary(id = "cred-1", name = "Proxmox homelab", type = "api_token")),
            requestId = "",
        ),
) : CredentialsRepository {
    override suspend fun listCredentials(): ApiResult<List<CredentialSummary>> = listResult
}
