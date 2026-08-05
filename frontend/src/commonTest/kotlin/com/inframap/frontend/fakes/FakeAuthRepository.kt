package com.inframap.frontend.fakes

import com.inframap.frontend.data.api.ApiResult
import com.inframap.frontend.data.dto.LoginRequest
import com.inframap.frontend.data.dto.OnboardRequest
import com.inframap.frontend.domain.model.LoginResult
import com.inframap.frontend.domain.model.OnboardResult
import com.inframap.frontend.domain.model.SetupStatus
import com.inframap.frontend.domain.model.User
import com.inframap.frontend.domain.repository.AuthRepository

class FakeAuthRepository(
    var getSetupStatusResult: ApiResult<SetupStatus> =
        ApiResult.Success(SetupStatus(onboardingCompleted = true, systemInstanceId = "inst-1"), requestId = ""),
    var loginResult: ApiResult<LoginResult> =
        ApiResult.Success(DEFAULT_LOGIN_RESULT, requestId = ""),
    var onboardResult: ApiResult<OnboardResult> =
        ApiResult.Success(DEFAULT_ONBOARD_RESULT, requestId = ""),
    var getCurrentUserResult: ApiResult<User> =
        ApiResult.Success(DEFAULT_USER, requestId = ""),
) : AuthRepository {
    override suspend fun getSetupStatus() = getSetupStatusResult

    override suspend fun login(request: LoginRequest) = loginResult

    override suspend fun onboard(request: OnboardRequest) = onboardResult

    override suspend fun getCurrentUser() = getCurrentUserResult

    companion object {
        val DEFAULT_USER = User(id = "u1", username = "admin", email = "a@b.com", fullName = "Admin")
        val DEFAULT_LOGIN_RESULT = LoginResult(token = "tok", userId = "u1", username = "admin")
        val DEFAULT_ONBOARD_RESULT =
            OnboardResult(onboardingCompleted = true, systemInstanceId = "inst-1", adminUserId = "u1")
    }
}
