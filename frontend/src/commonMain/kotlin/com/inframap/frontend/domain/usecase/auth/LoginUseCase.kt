package com.inframap.frontend.domain.usecase.auth

import com.inframap.frontend.data.api.ApiResult
import com.inframap.frontend.data.dto.LoginRequest
import com.inframap.frontend.domain.model.LoginResult
import com.inframap.frontend.domain.repository.AuthRepository
import com.inframap.frontend.domain.usecase.UseCase

class LoginUseCase(
    private val authRepository: AuthRepository,
) : UseCase<LoginRequest, ApiResult<LoginResult>> {
    override suspend fun invoke(params: LoginRequest): ApiResult<LoginResult> = authRepository.login(params)
}
