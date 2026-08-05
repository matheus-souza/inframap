package com.inframap.frontend.ui.splash

import app.cash.turbine.test
import com.inframap.frontend.data.api.ApiResult
import com.inframap.frontend.domain.model.SetupStatus
import com.inframap.frontend.domain.usecase.auth.GetCurrentUserUseCase
import com.inframap.frontend.domain.usecase.auth.GetSetupStatusUseCase
import com.inframap.frontend.fakes.FakeAuthRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertIs
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class SplashViewModelTest {
    private fun makeVm(
        repo: FakeAuthRepository = FakeAuthRepository(),
        scope: CoroutineScope? = null,
    ) = SplashViewModel(
        GetSetupStatusUseCase(repo),
        GetCurrentUserUseCase(repo),
        scope = scope,
    )

    @Test
    fun notOnboardedNavigatesToOnboarding() =
        runTest {
            val repo =
                FakeAuthRepository(
                    getSetupStatusResult =
                        ApiResult.Success(
                            SetupStatus(onboardingCompleted = false, systemInstanceId = ""),
                            requestId = "",
                        ),
                )
            val vm = makeVm(repo = repo, scope = this)

            vm.effects.test {
                vm.checkAuthState()
                assertIs<SplashEffect.NavigateToOnboarding>(awaitItem())
                cancelAndIgnoreRemainingEvents()
            }
            vm.clear()
        }

    @Test
    fun onboardedAndAuthenticatedNavigatesToDashboard() =
        runTest {
            val vm = makeVm(scope = this)

            vm.effects.test {
                vm.checkAuthState()
                assertIs<SplashEffect.NavigateToDashboard>(awaitItem())
                cancelAndIgnoreRemainingEvents()
            }
            vm.clear()
        }

    @Test
    fun onboardedButUnauthenticatedNavigatesToLogin() =
        runTest {
            val repo =
                FakeAuthRepository(
                    getCurrentUserResult =
                        ApiResult.Error(
                            code = "UNAUTH",
                            message = "Unauthenticated",
                            requestId = "",
                            httpStatus = 401,
                        ),
                )
            val vm = makeVm(repo = repo, scope = this)

            vm.effects.test {
                vm.checkAuthState()
                assertIs<SplashEffect.NavigateToLogin>(awaitItem())
                cancelAndIgnoreRemainingEvents()
            }
            vm.clear()
        }

    @Test
    fun setupStatusErrorNavigatesToLogin() =
        runTest {
            val repo =
                FakeAuthRepository(
                    getSetupStatusResult =
                        ApiResult.Error(
                            code = "SERVER_ERROR",
                            message = "Server error",
                            requestId = "",
                            httpStatus = 500,
                        ),
                )
            val vm = makeVm(repo = repo, scope = this)

            vm.effects.test {
                vm.checkAuthState()
                assertIs<SplashEffect.NavigateToLogin>(awaitItem())
                cancelAndIgnoreRemainingEvents()
            }
            vm.clear()
        }

    @Test
    fun initialStateIsLoading() {
        val vm = makeVm()
        assertTrue(vm.state.value.isLoading)
        vm.clear()
    }

    @Test
    fun authMeNetworkErrorNavigatesToLogin() =
        runTest {
            val repo =
                FakeAuthRepository(
                    getCurrentUserResult = ApiResult.NetworkError(RuntimeException("Network failure")),
                )
            val vm = makeVm(repo = repo, scope = this)

            vm.effects.test {
                vm.checkAuthState()
                assertIs<SplashEffect.NavigateToLogin>(awaitItem())
                cancelAndIgnoreRemainingEvents()
            }
            vm.clear()
        }
}
