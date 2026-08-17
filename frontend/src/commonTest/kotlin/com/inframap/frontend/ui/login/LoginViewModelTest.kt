package com.inframap.frontend.ui.login

import app.cash.turbine.test
import com.inframap.frontend.data.api.ApiResult
import com.inframap.frontend.domain.model.LoginResult
import com.inframap.frontend.domain.usecase.auth.LoginUseCase
import com.inframap.frontend.fakes.FakeAuthRepository
import com.inframap.frontend.generated.resources.Res
import com.inframap.frontend.generated.resources.login_error_credentials
import com.inframap.frontend.ui.util.UiText
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class LoginViewModelTest {
    private val sampleLoginResult =
        LoginResult(
            token = "tok",
            userId = "u1",
            username = "admin",
            email = "a@b.com",
        )

    private val mockLoginSuccess =
        LoginUseCase(
            FakeAuthRepository(loginResult = ApiResult.Success(sampleLoginResult, requestId = "")),
        )

    @Test
    fun initialStateIsEmpty() {
        val vm = LoginViewModel(mockLoginSuccess)
        val state = vm.state.value

        assertEquals("", state.username)
        assertEquals("", state.password)
        assertFalse(state.isLoading)
        assertNull(state.errorMessage)
        vm.clear()
    }

    @Test
    fun usernameChangedUpdatesState() {
        val vm = LoginViewModel(mockLoginSuccess)

        vm.onUsernameChanged("admin")
        assertEquals("admin", vm.state.value.username)
        vm.clear()
    }

    @Test
    fun passwordChangedUpdatesState() {
        val vm = LoginViewModel(mockLoginSuccess)

        vm.onPasswordChanged("secret123")
        assertEquals("secret123", vm.state.value.password)
        vm.clear()
    }

    @Test
    fun usernameChangeClearsError() {
        val vm = LoginViewModel(mockLoginSuccess)

        vm.login()
        assertTrue(vm.state.value.errorMessage != null)

        vm.onUsernameChanged("admin")
        assertNull(vm.state.value.errorMessage)
        vm.clear()
    }

    @Test
    fun passwordChangeClearsError() {
        val vm = LoginViewModel(mockLoginSuccess)

        vm.login()
        assertTrue(vm.state.value.errorMessage != null)

        vm.onPasswordChanged("secret")
        assertNull(vm.state.value.errorMessage)
        vm.clear()
    }

    @Test
    fun emptyUsernameShowsValidationError() {
        val vm = LoginViewModel(mockLoginSuccess)

        vm.onPasswordChanged("secret")
        vm.login()

        assertNotNull(vm.state.value.errorMessage)
        assertFalse(vm.state.value.isLoading)
        vm.clear()
    }

    @Test
    fun emptyPasswordShowsValidationError() {
        val vm = LoginViewModel(mockLoginSuccess)

        vm.onUsernameChanged("admin")
        vm.login()

        assertNotNull(vm.state.value.errorMessage)
        assertFalse(vm.state.value.isLoading)
        vm.clear()
    }

    @Test
    fun successfulLoginNavigatesToDashboard() =
        runTest {
            val vm = LoginViewModel(mockLoginSuccess, scope = this)

            vm.onUsernameChanged("admin")
            vm.onPasswordChanged("correctpassword")

            vm.effects.test {
                vm.login()
                assertIs<LoginEffect.NavigateToDashboard>(awaitItem())
                cancelAndIgnoreRemainingEvents()
            }
            assertFalse(vm.state.value.isLoading)
            vm.clear()
        }

    @Test
    fun failedLoginShowsErrorMessage() =
        runTest {
            val useCase =
                LoginUseCase(
                    FakeAuthRepository(
                        loginResult =
                            ApiResult.Error(
                                code = "INVALID_CREDENTIALS",
                                message = "Invalid username or password",
                                requestId = "",
                                httpStatus = 401,
                            ),
                    ),
                )
            val vm = LoginViewModel(useCase, scope = this)

            vm.onUsernameChanged("admin")
            vm.onPasswordChanged("wrongpassword")

            vm.state.test {
                skipItems(1)
                vm.login()
                advanceUntilIdle()

                val result = expectMostRecentItem()
                assertIs<UiText.Resource>(result.errorMessage)
                assertEquals(Res.string.login_error_credentials, (result.errorMessage as UiText.Resource).resId)
                assertFalse(result.isLoading)
                cancelAndIgnoreRemainingEvents()
            }
            vm.clear()
        }

    @Test
    fun rateLimitShowsSpecificMessage() =
        runTest {
            val useCase =
                LoginUseCase(
                    FakeAuthRepository(
                        loginResult =
                            ApiResult.Error(
                                code = "RATE_LIMIT",
                                message = "Rate limit exceeded",
                                requestId = "",
                                httpStatus = 429,
                            ),
                    ),
                )
            val vm = LoginViewModel(useCase, scope = this)

            vm.onUsernameChanged("admin")
            vm.onPasswordChanged("password123")

            vm.state.test {
                skipItems(1)
                vm.login()
                advanceUntilIdle()
                assertNotNull(expectMostRecentItem().errorMessage)
                cancelAndIgnoreRemainingEvents()
            }
            vm.clear()
        }

    @Test
    fun networkErrorShowsConnectionMessage() =
        runTest {
            val useCase =
                LoginUseCase(
                    FakeAuthRepository(
                        loginResult = ApiResult.NetworkError(RuntimeException("Network failure")),
                    ),
                )
            val vm = LoginViewModel(useCase, scope = this)

            vm.onUsernameChanged("admin")
            vm.onPasswordChanged("password123")

            vm.state.test {
                skipItems(1)
                vm.login()
                advanceUntilIdle()
                assertNotNull(expectMostRecentItem().errorMessage)
                cancelAndIgnoreRemainingEvents()
            }
            vm.clear()
        }
}
