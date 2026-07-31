package com.inframap.frontend.domain.repository

import com.inframap.frontend.data.api.ApiResult
import com.inframap.frontend.data.dto.LoginRequest
import com.inframap.frontend.data.dto.OnboardRequest
import com.inframap.frontend.domain.model.LoginResult
import com.inframap.frontend.domain.model.OnboardResult
import com.inframap.frontend.domain.model.SetupStatus
import com.inframap.frontend.domain.model.User

interface AuthRepository {
    suspend fun getSetupStatus(): ApiResult<SetupStatus>

    suspend fun login(request: LoginRequest): ApiResult<LoginResult>

    suspend fun onboard(request: OnboardRequest): ApiResult<OnboardResult>

    suspend fun getCurrentUser(): ApiResult<User>
}
