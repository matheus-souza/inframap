package com.inframap.frontend.ui.login

import com.inframap.frontend.data.api.ApiClient
import com.inframap.frontend.data.api.ApiResult
import com.inframap.frontend.data.dto.LoginRequest
import com.inframap.frontend.data.dto.LoginResponseDto
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class LoginViewModel(
    private val apiClient: ApiClient,
    private val scope: CoroutineScope,
) {
    private val _state = MutableStateFlow(LoginUiState())
    val state: StateFlow<LoginUiState> = _state.asStateFlow()

    private val _effects = Channel<LoginEffect>(Channel.BUFFERED)
    val effects: Flow<LoginEffect> = _effects.receiveAsFlow()

    fun onUsernameChanged(value: String) {
        _state.update { it.copy(username = value, errorMessage = null) }
    }

    fun onPasswordChanged(value: String) {
        _state.update { it.copy(password = value, errorMessage = null) }
    }

    fun login() {
        val current = _state.value
        if (current.username.isBlank() || current.password.isBlank()) {
            _state.update { it.copy(errorMessage = "Username and password are required") }
            return
        }
        if (current.isLoading) return

        _state.update { it.copy(isLoading = true, errorMessage = null) }

        scope.launch {
            val result =
                apiClient.post<LoginResponseDto, LoginRequest>(
                    "/api/v1/auth/login",
                    LoginRequest(username = current.username, password = current.password),
                )

            when (result) {
                is ApiResult.Success -> {
                    _state.update { it.copy(isLoading = false) }
                    _effects.send(LoginEffect.NavigateToDashboard)
                }
                is ApiResult.Error -> {
                    val message =
                        if (result.httpStatus == 429) {
                            "Too many attempts. Please wait before trying again."
                        } else {
                            result.message
                        }
                    _state.update { it.copy(isLoading = false, errorMessage = message) }
                }
                is ApiResult.NetworkError ->
                    _state.update {
                        it.copy(isLoading = false, errorMessage = "Network error. Check your connection.")
                    }
            }
        }
    }
}
