package com.inframap.frontend.domain.usecase.auth

import com.inframap.frontend.data.api.ApiResult
import com.inframap.frontend.domain.model.SetupStatus
import com.inframap.frontend.domain.repository.AuthRepository
import com.inframap.frontend.domain.usecase.NoParamUseCase

class GetSetupStatusUseCase(
    private val authRepository: AuthRepository,
) : NoParamUseCase<ApiResult<SetupStatus>> {
    override suspend fun invoke(): ApiResult<SetupStatus> = authRepository.getSetupStatus()
}
