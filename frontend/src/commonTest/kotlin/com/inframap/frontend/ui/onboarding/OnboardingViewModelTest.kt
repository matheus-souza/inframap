package com.inframap.frontend.ui.onboarding

import app.cash.turbine.test
import com.inframap.frontend.data.api.ApiResult
import com.inframap.frontend.domain.usecase.auth.OnboardUseCase
import com.inframap.frontend.fakes.FakeAuthRepository
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
class OnboardingViewModelTest {
    private val mockOnboardSuccess = OnboardUseCase(FakeAuthRepository())

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

            vm.effects.test {
                vm.onboard()
                assertIs<OnboardingEffect.NavigateToLogin>(awaitItem())
                cancelAndIgnoreRemainingEvents()
            }
            assertFalse(vm.state.value.isLoading)
            vm.clear()
        }

    @Test
    fun failedOnboardShowsErrorMessage() =
        runTest {
            val repo =
                FakeAuthRepository(
                    onboardResult =
                        ApiResult.Error(
                            code = "ALREADY_ONBOARDED",
                            message = "System is already onboarded",
                            requestId = "",
                            httpStatus = 409,
                        ),
                )
            val vm = OnboardingViewModel(OnboardUseCase(repo), scope = this)
            fillValidFields(vm)

            vm.state.test {
                skipItems(1)
                vm.onboard()
                advanceUntilIdle()
                assertEquals("System is already onboarded", expectMostRecentItem().errorMessage?.asStringAsync())
                cancelAndIgnoreRemainingEvents()
            }
            vm.clear()
        }

    @Test
    fun networkErrorShowsConnectionMessage() =
        runTest {
            val repo =
                FakeAuthRepository(
                    onboardResult = ApiResult.NetworkError(RuntimeException("Network failure")),
                )
            val vm = OnboardingViewModel(OnboardUseCase(repo), scope = this)
            fillValidFields(vm)

            vm.state.test {
                skipItems(1)
                vm.onboard()
                advanceUntilIdle()
                assertNotNull(expectMostRecentItem().errorMessage)
                cancelAndIgnoreRemainingEvents()
            }
            vm.clear()
        }
}
