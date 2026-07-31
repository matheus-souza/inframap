package com.inframap.frontend.domain.usecase.auth

import com.inframap.frontend.data.api.ApiResult
import com.inframap.frontend.domain.model.User
import com.inframap.frontend.domain.repository.AuthRepository
import com.inframap.frontend.domain.usecase.NoParamUseCase

class GetCurrentUserUseCase(
    private val authRepository: AuthRepository,
) : NoParamUseCase<ApiResult<User>> {
    override suspend fun invoke(): ApiResult<User> = authRepository.getCurrentUser()
}
