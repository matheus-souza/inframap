package com.inframap.frontend.domain.usecase.credentials

import com.inframap.frontend.data.api.ApiResult
import com.inframap.frontend.domain.repository.CredentialsRepository
import com.inframap.frontend.domain.usecase.UseCase

class DeleteCredentialUseCase(
    private val credentialsRepository: CredentialsRepository,
) : UseCase<String, ApiResult<Unit>> {
    override suspend fun invoke(params: String): ApiResult<Unit> = credentialsRepository.deleteCredential(params)
}
