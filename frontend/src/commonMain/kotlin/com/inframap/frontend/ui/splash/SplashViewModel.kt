package com.inframap.frontend.ui.splash

import com.inframap.frontend.data.api.ApiResult
import com.inframap.frontend.domain.usecase.auth.GetCurrentUserUseCase
import com.inframap.frontend.domain.usecase.auth.GetSetupStatusUseCase
import com.inframap.frontend.ui.base.BaseViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow

class SplashViewModel(
    private val getSetupStatusUseCase: GetSetupStatusUseCase,
    private val getCurrentUserUseCase: GetCurrentUserUseCase,
    scope: CoroutineScope? = null,
) : BaseViewModel<SplashUiState>(SplashUiState(), scope) {
    private val _effects = Channel<SplashEffect>(Channel.BUFFERED)
    val effects: Flow<SplashEffect> = _effects.receiveAsFlow()

    fun checkAuthState() {
        launchJob("check_auth") {
            when (val setupResult = getSetupStatusUseCase()) {
                is ApiResult.Success -> {
                    if (!setupResult.data.onboardingCompleted) {
                        _effects.send(SplashEffect.NavigateToOnboarding)
                        return@launchJob
                    }
                }
                is ApiResult.Error -> {
                    _effects.send(SplashEffect.NavigateToLogin)
                    return@launchJob
                }
                is ApiResult.NetworkError -> {
                    _effects.send(SplashEffect.NavigateToLogin)
                    return@launchJob
                }
            }

            when (getCurrentUserUseCase()) {
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
