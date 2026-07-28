package com.inframap.frontend.ui.login

data class LoginUiState(
    val username: String = "",
    val password: String = "",
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
)

sealed class LoginEffect {
    data object NavigateToDashboard : LoginEffect()
}
