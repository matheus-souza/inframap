package com.inframap.frontend.ui.onboarding

data class OnboardingUiState(
    val username: String = "",
    val email: String = "",
    val fullName: String = "",
    val password: String = "",
    val confirmPassword: String = "",
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
)

sealed class OnboardingEffect {
    data object NavigateToLogin : OnboardingEffect()
}
