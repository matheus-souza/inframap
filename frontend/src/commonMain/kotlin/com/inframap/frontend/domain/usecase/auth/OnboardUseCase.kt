package com.inframap.frontend.domain.usecase.auth

import com.inframap.frontend.data.api.ApiResult
import com.inframap.frontend.data.dto.OnboardRequest
import com.inframap.frontend.domain.model.OnboardResult
import com.inframap.frontend.domain.repository.AuthRepository
import com.inframap.frontend.domain.usecase.UseCase

class OnboardUseCase(
    private val authRepository: AuthRepository,
) : UseCase<OnboardRequest, ApiResult<OnboardResult>> {
    override suspend fun invoke(params: OnboardRequest): ApiResult<OnboardResult> = authRepository.onboard(params)
}
