package com.inframap.frontend.ui.login

import com.inframap.frontend.data.api.ApiResult
import com.inframap.frontend.data.dto.LoginRequest
import com.inframap.frontend.data.dto.OnboardRequest
import com.inframap.frontend.domain.model.LoginResult
import com.inframap.frontend.domain.model.SetupStatus
import com.inframap.frontend.domain.model.User
import com.inframap.frontend.domain.repository.AuthRepository
import com.inframap.frontend.domain.usecase.auth.LoginUseCase
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
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

    private fun repoWithLogin(result: ApiResult<LoginResult>): AuthRepository =
        object : AuthRepository {
            override suspend fun getSetupStatus() = ApiResult.Success(SetupStatus(onboardingCompleted = true), requestId = "")

            override suspend fun login(request: LoginRequest) = result

            override suspend fun onboard(request: OnboardRequest) =
                ApiResult.Error(code = "ERR", message = "Not implemented", requestId = "", httpStatus = 500)

            override suspend fun getCurrentUser() =
                ApiResult.Success(User(id = "u1", username = "admin", email = "a@b.com"), requestId = "")
        }

    private val mockLoginSuccess = LoginUseCase(repoWithLogin(ApiResult.Success(sampleLoginResult, requestId = "")))

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

            val deferred = async { vm.effects.first() }
            vm.login()
            advanceUntilIdle()

            assertIs<LoginEffect.NavigateToDashboard>(deferred.await())
            assertFalse(vm.state.value.isLoading)
            vm.clear()
        }

    @Test
    fun failedLoginShowsErrorMessage() =
        runTest {
            val useCase =
                LoginUseCase(
                    repoWithLogin(
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

            val deferred = async { vm.state.first { !it.isLoading && it.errorMessage != null } }
            vm.login()
            advanceUntilIdle()

            val result = deferred.await()
            assertEquals("Invalid username or password", result.errorMessage?.asStringAsync())
            assertFalse(result.isLoading)
            vm.clear()
        }

    @Test
    fun rateLimitShowsSpecificMessage() =
        runTest {
            val useCase =
                LoginUseCase(
                    repoWithLogin(
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

            val deferred = async { vm.state.first { !it.isLoading && it.errorMessage != null } }
            vm.login()
            advanceUntilIdle()

            assertNotNull(deferred.await().errorMessage)
            vm.clear()
        }

    @Test
    fun networkErrorShowsConnectionMessage() =
        runTest {
            val useCase =
                LoginUseCase(
                    repoWithLogin(ApiResult.NetworkError(RuntimeException("Network failure"))),
                )
            val vm = LoginViewModel(useCase, scope = this)

            vm.onUsernameChanged("admin")
            vm.onPasswordChanged("password123")

            val deferred = async { vm.state.first { !it.isLoading && it.errorMessage != null } }
            vm.login()
            advanceUntilIdle()

            assertNotNull(deferred.await().errorMessage)
            vm.clear()
        }
}
