package com.inframap.frontend.ui.splash

import com.inframap.frontend.data.api.ApiClient
import com.inframap.frontend.data.api.ApiResult
import com.inframap.frontend.data.dto.SetupStatusDto
import com.inframap.frontend.data.dto.UserProfileDto
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch

class SplashViewModel(
    private val apiClient: ApiClient,
    private val scope: CoroutineScope,
) {
    private val _state = MutableStateFlow(SplashUiState())
    val state: StateFlow<SplashUiState> = _state.asStateFlow()

    private val _effects = Channel<SplashEffect>(Channel.BUFFERED)
    val effects: Flow<SplashEffect> = _effects.receiveAsFlow()

    fun checkAuthState() {
        scope.launch {
            val setupResult =
                apiClient.get<SetupStatusDto>("/api/v1/setup/status")

            when (setupResult) {
                is ApiResult.Success -> {
                    if (!setupResult.data.onboardingCompleted) {
                        _effects.send(SplashEffect.NavigateToOnboarding)
                        return@launch
                    }
                }
                is ApiResult.Error -> {
                    _effects.send(SplashEffect.NavigateToLogin)
                    return@launch
                }
                is ApiResult.NetworkError -> {
                    _effects.send(SplashEffect.NavigateToLogin)
                    return@launch
                }
            }

            val meResult =
                apiClient.get<UserProfileDto>("/api/v1/auth/me")

            when (meResult) {
                is ApiResult.Success ->
                    _effects.send(SplashEffect.NavigateToDashboard)
                is ApiResult.Error ->
                    _effects.send(SplashEffect.NavigateToLogin)
                is ApiResult.NetworkError ->
                    _effects.send(SplashEffect.NavigateToLogin)
            }
        }
    }
}
