package com.inframap.frontend.ui.login

import com.inframap.frontend.ui.util.UiText

data class LoginUiState(
    val username: String = "",
    val password: String = "",
    val isLoading: Boolean = false,
    val errorMessage: UiText? = null,
)

sealed class LoginEffect {
    data object NavigateToDashboard : LoginEffect()
}
