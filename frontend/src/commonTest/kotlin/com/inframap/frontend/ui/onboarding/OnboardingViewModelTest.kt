package com.inframap.frontend.ui.onboarding

import com.inframap.frontend.data.api.ApiResult
import com.inframap.frontend.data.dto.LoginRequest
import com.inframap.frontend.data.dto.OnboardRequest
import com.inframap.frontend.domain.model.LoginResult
import com.inframap.frontend.domain.model.OnboardResult
import com.inframap.frontend.domain.model.SetupStatus
import com.inframap.frontend.domain.model.User
import com.inframap.frontend.domain.repository.AuthRepository
import com.inframap.frontend.domain.usecase.auth.OnboardUseCase
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
class OnboardingViewModelTest {
    private val sampleOnboardResult =
        OnboardResult(
            onboardingCompleted = true,
            systemInstanceId = "inst-1",
            adminUserId = "u1",
        )

    private fun successRepo(): AuthRepository =
        object : AuthRepository {
            override suspend fun getSetupStatus() = ApiResult.Success(SetupStatus(onboardingCompleted = false), requestId = "")

            override suspend fun login(request: LoginRequest) =
                ApiResult.Success(
                    LoginResult(token = "tok", userId = "u1", username = "admin"),
                    requestId = "",
                )

            override suspend fun onboard(request: OnboardRequest) = ApiResult.Success(sampleOnboardResult, requestId = "")

            override suspend fun getCurrentUser() =
                ApiResult.Success(
                    User(id = "u1", username = "admin", email = "a@b.com"),
                    requestId = "",
                )
        }

    private val mockOnboardSuccess = OnboardUseCase(successRepo())

    private fun fillValidFields(vm: OnboardingViewModel) {
        vm.onUsernameChanged("admin")
        vm.onEmailChanged("admin@inframap.local")
        vm.onFullNameChanged("Admin User")
        vm.onPasswordChanged("securepass123!")
        vm.onConfirmPasswordChanged("securepass123!")
    }

    @Test
    fun initialStateIsEmpty() {
        val vm = OnboardingViewModel(mockOnboardSuccess)
        val state = vm.state.value

        assertEquals("", state.username)
        assertEquals("", state.email)
        assertEquals("", state.fullName)
        assertEquals("", state.password)
        assertEquals("", state.confirmPassword)
        assertFalse(state.isLoading)
        assertNull(state.errorMessage)
        vm.clear()
    }

    @Test
    fun fieldChangesUpdateState() {
        val vm = OnboardingViewModel(mockOnboardSuccess)

        vm.onUsernameChanged("admin")
        vm.onEmailChanged("a@b.com")
        vm.onFullNameChanged("Admin")
        vm.onPasswordChanged("pass")
        vm.onConfirmPasswordChanged("pass")

        val state = vm.state.value
        assertEquals("admin", state.username)
        assertEquals("a@b.com", state.email)
        assertEquals("Admin", state.fullName)
        assertEquals("pass", state.password)
        assertEquals("pass", state.confirmPassword)
        vm.clear()
    }

    @Test
    fun fieldChangeClearsError() {
        val vm = OnboardingViewModel(mockOnboardSuccess)

        vm.onboard()
        assertTrue(vm.state.value.errorMessage != null)

        vm.onUsernameChanged("admin")
        assertNull(vm.state.value.errorMessage)
        vm.clear()
    }

    @Test
    fun validateBlankUsername() {
        val vm = OnboardingViewModel(mockOnboardSuccess)
        assertNotNull(
            vm.validate(
                OnboardingUiState(
                    email = "a@b.com",
                    fullName = "N",
                    password = "123456789012",
                    confirmPassword = "123456789012",
                ),
            ),
        )
        vm.clear()
    }

    @Test
    fun validateShortUsername() {
        val vm = OnboardingViewModel(mockOnboardSuccess)
        assertNotNull(
            vm.validate(
                OnboardingUiState(
                    username = "ab",
                    email = "a@b.com",
                    fullName = "N",
                    password = "123456789012",
                    confirmPassword = "123456789012",
                ),
            ),
        )
        vm.clear()
    }

    @Test
    fun validateBlankEmail() {
        val vm = OnboardingViewModel(mockOnboardSuccess)
        assertNotNull(
            vm.validate(
                OnboardingUiState(
                    username = "admin",
                    fullName = "N",
                    password = "123456789012",
                    confirmPassword = "123456789012",
                ),
            ),
        )
        vm.clear()
    }

    @Test
    fun validateInvalidEmail() {
        val vm = OnboardingViewModel(mockOnboardSuccess)
        assertNotNull(
            vm.validate(
                OnboardingUiState(
                    username = "admin",
                    email = "notanemail",
                    fullName = "N",
                    password = "123456789012",
                    confirmPassword = "123456789012",
                ),
            ),
        )
        vm.clear()
    }

    @Test
    fun validateBlankFullName() {
        val vm = OnboardingViewModel(mockOnboardSuccess)
        assertNotNull(
            vm.validate(
                OnboardingUiState(
                    username = "admin",
                    email = "a@b.com",
                    password = "123456789012",
                    confirmPassword = "123456789012",
                ),
            ),
        )
        vm.clear()
    }

    @Test
    fun validateBlankPassword() {
        val vm = OnboardingViewModel(mockOnboardSuccess)
        assertNotNull(vm.validate(OnboardingUiState(username = "admin", email = "a@b.com", fullName = "N")))
        vm.clear()
    }

    @Test
    fun validateShortPassword() {
        val vm = OnboardingViewModel(mockOnboardSuccess)
        assertNotNull(
            vm.validate(
                OnboardingUiState(
                    username = "admin",
                    email = "a@b.com",
                    fullName = "N",
                    password = "short",
                    confirmPassword = "short",
                ),
            ),
        )
        vm.clear()
    }

    @Test
    fun validatePasswordMismatch() {
        val vm = OnboardingViewModel(mockOnboardSuccess)
        assertNotNull(
            vm.validate(
                OnboardingUiState(
                    username = "admin",
                    email = "a@b.com",
                    fullName = "N",
                    password = "123456789012",
                    confirmPassword = "different1234",
                ),
            ),
        )
        vm.clear()
    }

    @Test
    fun validateValidStateReturnsNull() {
        val vm = OnboardingViewModel(mockOnboardSuccess)
        assertNull(
            vm.validate(
                OnboardingUiState(
                    username = "admin",
                    email = "a@b.com",
                    fullName = "N",
                    password = "123456789012",
                    confirmPassword = "123456789012",
                ),
            ),
        )
        vm.clear()
    }

    @Test
    fun successfulOnboardNavigatesToLogin() =
        runTest {
            val vm = OnboardingViewModel(mockOnboardSuccess, scope = this)
            fillValidFields(vm)

            val deferred = async { vm.effects.first() }
            vm.onboard()
            advanceUntilIdle()

            assertIs<OnboardingEffect.NavigateToLogin>(deferred.await())
            assertFalse(vm.state.value.isLoading)
            vm.clear()
        }

    @Test
    fun failedOnboardShowsErrorMessage() =
        runTest {
            val errorRepo =
                object : AuthRepository {
                    override suspend fun getSetupStatus() = ApiResult.Success(SetupStatus(onboardingCompleted = false), requestId = "")

                    override suspend fun login(request: LoginRequest) =
                        ApiResult.Success(
                            LoginResult(token = "tok", userId = "u1", username = "admin"),
                            requestId = "",
                        )

                    override suspend fun onboard(request: OnboardRequest) =
                        ApiResult.Error(
                            code = "ALREADY_ONBOARDED",
                            message = "System is already onboarded",
                            requestId = "",
                            httpStatus = 409,
                        )

                    override suspend fun getCurrentUser() =
                        ApiResult.Success(
                            User(id = "u1", username = "admin", email = "a@b.com"),
                            requestId = "",
                        )
                }
            val vm = OnboardingViewModel(OnboardUseCase(errorRepo), scope = this)
            fillValidFields(vm)

            val deferred = async { vm.state.first { !it.isLoading && it.errorMessage != null } }
            vm.onboard()
            advanceUntilIdle()

            assertEquals("System is already onboarded", deferred.await().errorMessage?.asStringAsync())
            vm.clear()
        }

    @Test
    fun networkErrorShowsConnectionMessage() =
        runTest {
            val errorRepo =
                object : AuthRepository {
                    override suspend fun getSetupStatus() = ApiResult.Success(SetupStatus(onboardingCompleted = false), requestId = "")

                    override suspend fun login(request: LoginRequest) =
                        ApiResult.Success(
                            LoginResult(token = "tok", userId = "u1", username = "admin"),
                            requestId = "",
                        )

                    override suspend fun onboard(request: OnboardRequest) = ApiResult.NetworkError(RuntimeException("Network failure"))

                    override suspend fun getCurrentUser() =
                        ApiResult.Success(
                            User(id = "u1", username = "admin", email = "a@b.com"),
                            requestId = "",
                        )
                }
            val vm = OnboardingViewModel(OnboardUseCase(errorRepo), scope = this)
            fillValidFields(vm)

            val deferred = async { vm.state.first { !it.isLoading && it.errorMessage != null } }
            vm.onboard()
            advanceUntilIdle()

            assertNotNull(deferred.await().errorMessage)
            vm.clear()
        }
}
