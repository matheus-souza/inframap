package com.inframap.frontend.domain.usecase.credentials

import com.inframap.frontend.data.api.ApiResult
import com.inframap.frontend.domain.model.CredentialSummary
import com.inframap.frontend.domain.repository.CredentialsRepository
import com.inframap.frontend.domain.usecase.NoParamUseCase

class ListCredentialsUseCase(
    private val credentialsRepository: CredentialsRepository,
) : NoParamUseCase<ApiResult<List<CredentialSummary>>> {
    override suspend fun invoke(): ApiResult<List<CredentialSummary>> = credentialsRepository.listCredentials()
}
