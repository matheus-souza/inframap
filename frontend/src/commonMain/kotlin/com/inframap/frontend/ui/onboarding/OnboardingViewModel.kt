package com.inframap.frontend.ui.onboarding

import com.inframap.frontend.data.api.ApiClient
import com.inframap.frontend.data.api.ApiResult
import com.inframap.frontend.data.dto.OnboardRequest
import com.inframap.frontend.data.dto.OnboardResponseDto
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class OnboardingViewModel(
    private val apiClient: ApiClient,
    private val scope: CoroutineScope,
) {
    private val _state = MutableStateFlow(OnboardingUiState())
    val state: StateFlow<OnboardingUiState> = _state.asStateFlow()

    private val _effects = Channel<OnboardingEffect>(Channel.BUFFERED)
    val effects: Flow<OnboardingEffect> = _effects.receiveAsFlow()

    fun onUsernameChanged(value: String) {
        _state.update { it.copy(username = value, errorMessage = null) }
    }

    fun onEmailChanged(value: String) {
        _state.update { it.copy(email = value, errorMessage = null) }
    }

    fun onFullNameChanged(value: String) {
        _state.update { it.copy(fullName = value, errorMessage = null) }
    }

    fun onPasswordChanged(value: String) {
        _state.update { it.copy(password = value, errorMessage = null) }
    }

    fun onConfirmPasswordChanged(value: String) {
        _state.update { it.copy(confirmPassword = value, errorMessage = null) }
    }

    fun onboard() {
        val current = _state.value
        val validationError = validate(current)
        if (validationError != null) {
            _state.update { it.copy(errorMessage = validationError) }
            return
        }
        if (current.isLoading) return

        _state.update { it.copy(isLoading = true, errorMessage = null) }

        scope.launch {
            val result =
                apiClient.post<OnboardResponseDto, OnboardRequest>(
                    "/api/v1/setup/onboard",
                    OnboardRequest(
                        adminUsername = current.username,
                        adminEmail = current.email,
                        adminPassword = current.password,
                        adminFullName = current.fullName,
                    ),
                )

            when (result) {
                is ApiResult.Success -> {
                    _state.update { it.copy(isLoading = false) }
                    _effects.send(OnboardingEffect.NavigateToLogin)
                }
                is ApiResult.Error ->
                    _state.update { it.copy(isLoading = false, errorMessage = result.message) }
                is ApiResult.NetworkError ->
                    _state.update {
                        it.copy(isLoading = false, errorMessage = "Network error. Check your connection.")
                    }
            }
        }
    }

    internal fun validate(state: OnboardingUiState): String? =
        when {
            state.username.isBlank() -> "Username is required"
            state.username.length < 3 -> "Username must be at least 3 characters"
            state.email.isBlank() -> "Email is required"
            !state.email.contains("@") -> "Invalid email address"
            state.fullName.isBlank() -> "Full name is required"
            state.password.isBlank() -> "Password is required"
            state.password.length < 12 -> "Password must be at least 12 characters"
            state.confirmPassword != state.password -> "Passwords do not match"
            else -> null
        }
}
