package com.inframap.frontend.ui.login

import com.inframap.frontend.data.api.ApiResult
import com.inframap.frontend.designsystem.resources.Res
import com.inframap.frontend.domain.usecase.auth.LoginUseCase
import com.inframap.frontend.ui.base.BaseViewModel
import com.inframap.frontend.ui.util.UiText
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow

class LoginViewModel(
    private val loginUseCase: LoginUseCase,
    scope: CoroutineScope? = null,
) : BaseViewModel<LoginUiState>(LoginUiState(), scope) {
    private val _effects = Channel<LoginEffect>(Channel.BUFFERED)
    val effects: Flow<LoginEffect> = _effects.receiveAsFlow()

    fun onUsernameChanged(value: String) {
        updateState { it.copy(username = value, errorMessage = null) }
    }

    fun onPasswordChanged(value: String) {
        updateState { it.copy(password = value, errorMessage = null) }
    }

    fun login() {
        val current = state.value
        if (current.username.isBlank() || current.password.isBlank()) {
            updateState { it.copy(errorMessage = UiText.Resource(Res.string.login_error_credentials)) }
            return
        }
        if (current.isLoading) return

        updateState { it.copy(isLoading = true, errorMessage = null) }

        launchJob("login") {
            when (val result = loginUseCase(username = current.username, password = current.password)) {
                is ApiResult.Success -> {
                    updateState { it.copy(isLoading = false) }
                    _effects.send(LoginEffect.NavigateToDashboard)
                }
                is ApiResult.Error -> {
                    val message =
                        if (result.httpStatus == 429) {
                            UiText.Resource(Res.string.login_error_rate_limit)
                        } else {
                            UiText.Resource(Res.string.login_error_credentials)
                        }
                    updateState { it.copy(isLoading = false, errorMessage = message) }
                }
                is ApiResult.NetworkError -> {
                    updateState {
                        it.copy(
                            isLoading = false,
                            errorMessage = mapError(result, UiText.Resource(Res.string.login_error_network)),
                        )
                    }
                }
            }
        }
    }
}
