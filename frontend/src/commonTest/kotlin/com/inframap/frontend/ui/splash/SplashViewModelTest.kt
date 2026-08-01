package com.inframap.frontend.ui.splash

import com.inframap.frontend.data.api.ApiResult
import com.inframap.frontend.data.dto.LoginRequest
import com.inframap.frontend.data.dto.OnboardRequest
import com.inframap.frontend.domain.model.LoginResult
import com.inframap.frontend.domain.model.OnboardResult
import com.inframap.frontend.domain.model.SetupStatus
import com.inframap.frontend.domain.model.User
import com.inframap.frontend.domain.repository.AuthRepository
import com.inframap.frontend.domain.usecase.auth.GetCurrentUserUseCase
import com.inframap.frontend.domain.usecase.auth.GetSetupStatusUseCase
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertIs
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class SplashViewModelTest {
    private val notOnboarded = SetupStatus(onboardingCompleted = false, systemInstanceId = "")
    private val onboarded = SetupStatus(onboardingCompleted = true, systemInstanceId = "inst-1")
    private val sampleUser = User(id = "u1", username = "admin", email = "a@b.com", fullName = "Admin")

    private fun repoWithSetupAndUser(
        setupResult: ApiResult<SetupStatus>,
        userResult: ApiResult<User>,
    ): AuthRepository =
        object : AuthRepository {
            override suspend fun getSetupStatus() = setupResult

            override suspend fun login(request: LoginRequest) =
                ApiResult.Success(
                    LoginResult(token = "tok", userId = "u1", username = "admin"),
                    requestId = "",
                )

            override suspend fun onboard(request: OnboardRequest) =
                ApiResult.Success(
                    OnboardResult(
                        onboardingCompleted = true,
                        systemInstanceId = "inst-1",
                        adminUserId = "u1",
                    ),
                    requestId = "",
                )

            override suspend fun getCurrentUser() = userResult
        }

    @Test
    fun notOnboardedNavigatesToOnboarding() =
        runTest {
            val repo =
                repoWithSetupAndUser(
                    setupResult = ApiResult.Success(notOnboarded, requestId = ""),
                    userResult = ApiResult.Success(sampleUser, requestId = ""),
                )
            val vm = SplashViewModel(GetSetupStatusUseCase(repo), GetCurrentUserUseCase(repo), scope = this)
            val deferred = async { vm.effects.first() }
            vm.checkAuthState()
            advanceUntilIdle()

            assertIs<SplashEffect.NavigateToOnboarding>(deferred.await())
            vm.clear()
        }

    @Test
    fun onboardedAndAuthenticatedNavigatesToDashboard() =
        runTest {
            val repo =
                repoWithSetupAndUser(
                    setupResult = ApiResult.Success(onboarded, requestId = ""),
                    userResult = ApiResult.Success(sampleUser, requestId = ""),
                )
            val vm = SplashViewModel(GetSetupStatusUseCase(repo), GetCurrentUserUseCase(repo), scope = this)
            val deferred = async { vm.effects.first() }
            vm.checkAuthState()
            advanceUntilIdle()

            assertIs<SplashEffect.NavigateToDashboard>(deferred.await())
            vm.clear()
        }

    @Test
    fun onboardedButUnauthenticatedNavigatesToLogin() =
        runTest {
            val repo =
                repoWithSetupAndUser(
                    setupResult = ApiResult.Success(onboarded, requestId = ""),
                    userResult =
                        ApiResult.Error(
                            code = "UNAUTH",
                            message = "Unauthenticated",
                            requestId = "",
                            httpStatus = 401,
                        ),
                )
            val vm = SplashViewModel(GetSetupStatusUseCase(repo), GetCurrentUserUseCase(repo), scope = this)
            val deferred = async { vm.effects.first() }
            vm.checkAuthState()
            advanceUntilIdle()

            assertIs<SplashEffect.NavigateToLogin>(deferred.await())
            vm.clear()
        }

    @Test
    fun setupStatusErrorNavigatesToLogin() =
        runTest {
            val repo =
                repoWithSetupAndUser(
                    setupResult =
                        ApiResult.Error(
                            code = "SERVER_ERROR",
                            message = "Server error",
                            requestId = "",
                            httpStatus = 500,
                        ),
                    userResult = ApiResult.Success(sampleUser, requestId = ""),
                )
            val vm = SplashViewModel(GetSetupStatusUseCase(repo), GetCurrentUserUseCase(repo), scope = this)
            val deferred = async { vm.effects.first() }
            vm.checkAuthState()
            advanceUntilIdle()

            assertIs<SplashEffect.NavigateToLogin>(deferred.await())
            vm.clear()
        }

    @Test
    fun initialStateIsLoading() {
        val repo =
            repoWithSetupAndUser(
                setupResult = ApiResult.Success(onboarded, requestId = ""),
                userResult = ApiResult.Success(sampleUser, requestId = ""),
            )

        @Suppress("UNUSED_VARIABLE")
        val scope = TestScope(StandardTestDispatcher())
        val vm = SplashViewModel(GetSetupStatusUseCase(repo), GetCurrentUserUseCase(repo))

        assertTrue(vm.state.value.isLoading)
        vm.clear()
    }

    @Test
    fun authMeNetworkErrorNavigatesToLogin() =
        runTest {
            val repo =
                repoWithSetupAndUser(
                    setupResult = ApiResult.Success(onboarded, requestId = ""),
                    userResult = ApiResult.NetworkError(RuntimeException("Network failure")),
                )
            val vm = SplashViewModel(GetSetupStatusUseCase(repo), GetCurrentUserUseCase(repo), scope = this)
            val deferred = async { vm.effects.first() }
            vm.checkAuthState()
            advanceUntilIdle()

            assertIs<SplashEffect.NavigateToLogin>(deferred.await())
            vm.clear()
        }
}
