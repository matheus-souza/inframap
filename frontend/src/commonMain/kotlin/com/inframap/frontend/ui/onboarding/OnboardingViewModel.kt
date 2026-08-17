package com.inframap.frontend.ui.onboarding

import com.inframap.frontend.data.api.ApiResult
import com.inframap.frontend.domain.usecase.auth.OnboardUseCase
import com.inframap.frontend.generated.resources.Res
import com.inframap.frontend.generated.resources.onboarding_error_generic
import com.inframap.frontend.generated.resources.validation_email_invalid
import com.inframap.frontend.generated.resources.validation_password_min
import com.inframap.frontend.generated.resources.validation_password_mismatch
import com.inframap.frontend.generated.resources.validation_username_min
import com.inframap.frontend.ui.base.BaseViewModel
import com.inframap.frontend.ui.util.UiText
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow

class OnboardingViewModel(
    private val onboardUseCase: OnboardUseCase,
    scope: CoroutineScope? = null,
) : BaseViewModel<OnboardingUiState>(OnboardingUiState(), scope) {
    private val _effects = Channel<OnboardingEffect>(Channel.BUFFERED)
    val effects: Flow<OnboardingEffect> = _effects.receiveAsFlow()

    fun onUsernameChanged(value: String) {
        updateState { it.copy(username = value, errorMessage = null) }
    }

    fun onEmailChanged(value: String) {
        updateState { it.copy(email = value, errorMessage = null) }
    }

    fun onFullNameChanged(value: String) {
        updateState { it.copy(fullName = value, errorMessage = null) }
    }

    fun onPasswordChanged(value: String) {
        updateState { it.copy(password = value, errorMessage = null) }
    }

    fun onConfirmPasswordChanged(value: String) {
        updateState { it.copy(confirmPassword = value, errorMessage = null) }
    }

    fun onboard() {
        val current = state.value
        val validationError = validate(current)
        if (validationError != null) {
            updateState { it.copy(errorMessage = validationError) }
            return
        }
        if (current.isLoading) return

        updateState { it.copy(isLoading = true, errorMessage = null) }

        launchJob("onboard") {
            when (
                val result =
                    onboardUseCase(
                        adminUsername = current.username,
                        adminEmail = current.email,
                        adminPassword = current.password,
                        adminFullName = current.fullName,
                    )
            ) {
                is ApiResult.Success -> {
                    updateState { it.copy(isLoading = false) }
                    _effects.send(OnboardingEffect.NavigateToLogin)
                }
                is ApiResult.Error -> {
                    updateState {
                        it.copy(
                            isLoading = false,
                            errorMessage = mapError(result, UiText.Resource(Res.string.onboarding_error_generic)),
                        )
                    }
                }
                is ApiResult.NetworkError -> {
                    updateState {
                        it.copy(
                            isLoading = false,
                            errorMessage = mapError(result, UiText.Resource(Res.string.onboarding_error_generic)),
                        )
                    }
                }
            }
        }
    }

    internal fun validate(state: OnboardingUiState): UiText? =
        when {
            state.username.isBlank() -> UiText.Resource(Res.string.validation_username_min)
            state.username.length < 3 -> UiText.Resource(Res.string.validation_username_min)
            state.email.isBlank() -> UiText.Resource(Res.string.validation_email_invalid)
            !state.email.contains("@") -> UiText.Resource(Res.string.validation_email_invalid)
            state.fullName.isBlank() -> UiText.Resource(Res.string.onboarding_error_generic)
            state.password.isBlank() -> UiText.Resource(Res.string.validation_password_min)
            state.password.length < 12 -> UiText.Resource(Res.string.validation_password_min)
            state.confirmPassword != state.password -> UiText.Resource(Res.string.validation_password_mismatch)
            else -> null
        }
}
