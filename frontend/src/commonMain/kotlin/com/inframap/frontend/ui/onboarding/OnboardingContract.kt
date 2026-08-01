package com.inframap.frontend.ui.onboarding

import com.inframap.frontend.ui.util.UiText

data class OnboardingUiState(
    val username: String = "",
    val email: String = "",
    val fullName: String = "",
    val password: String = "",
    val confirmPassword: String = "",
    val isLoading: Boolean = false,
    val errorMessage: UiText? = null,
)

sealed class OnboardingEffect {
    data object NavigateToLogin : OnboardingEffect()
}
