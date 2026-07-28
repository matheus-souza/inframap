package com.inframap.frontend.ui.splash

data class SplashUiState(
    val isLoading: Boolean = true,
)

sealed class SplashEffect {
    data object NavigateToLogin : SplashEffect()

    data object NavigateToOnboarding : SplashEffect()

    data object NavigateToDashboard : SplashEffect()
}
