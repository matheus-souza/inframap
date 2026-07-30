package com.inframap.frontend.ui.app

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import com.inframap.frontend.data.api.ApiClient
import com.inframap.frontend.data.api.ApiResult
import com.inframap.frontend.data.dto.HealthDto
import com.inframap.frontend.data.sse.SSEClient
import com.inframap.frontend.designsystem.InfraMapTheme
import com.inframap.frontend.navigation.Navigator
import com.inframap.frontend.navigation.Route
import com.inframap.frontend.ui.login.LoginEffect
import com.inframap.frontend.ui.login.LoginScreen
import com.inframap.frontend.ui.login.LoginViewModel
import com.inframap.frontend.ui.onboarding.OnboardingEffect
import com.inframap.frontend.ui.onboarding.OnboardingScreen
import com.inframap.frontend.ui.onboarding.OnboardingViewModel
import com.inframap.frontend.ui.splash.SplashEffect
import com.inframap.frontend.ui.splash.SplashScreen
import com.inframap.frontend.ui.splash.SplashViewModel

@Composable
fun InfraMapApp(
    apiClient: ApiClient,
    sseClient: SSEClient? = null,
) {
    val navigator = remember { Navigator() }
    val currentRoute by navigator.currentRoute.collectAsState()
    var isHealthy by remember { mutableStateOf<Boolean?>(null) }

    LaunchedEffect(Unit) {
        val result = apiClient.get<HealthDto>("/api/v1/health")
        isHealthy = result is ApiResult.Success && result.data.status == "ok"
    }

    InfraMapTheme {
        when (currentRoute) {
            Route.Splash -> SplashRoute(apiClient, navigator)
            Route.Login -> LoginRoute(apiClient, navigator)
            Route.Onboarding -> OnboardingRoute(apiClient, navigator)
            else ->
                MainScaffold(
                    currentRoute = currentRoute,
                    navigator = navigator,
                    isHealthy = isHealthy,
                    apiClient = apiClient,
                    sseClient = sseClient,
                )
        }
    }
}

@Composable
private fun SplashRoute(
    apiClient: ApiClient,
    navigator: Navigator,
) {
    val scope = rememberCoroutineScope()
    val viewModel = remember { SplashViewModel(apiClient, scope) }
    SplashScreen()
    LaunchedEffect(Unit) {
        viewModel.checkAuthState()
        viewModel.effects.collect { effect ->
            when (effect) {
                SplashEffect.NavigateToLogin -> navigator.navigateTo(Route.Login)
                SplashEffect.NavigateToOnboarding -> navigator.navigateTo(Route.Onboarding)
                SplashEffect.NavigateToDashboard -> navigator.navigateTo(Route.Dashboard)
            }
        }
    }
}

@Composable
private fun LoginRoute(
    apiClient: ApiClient,
    navigator: Navigator,
) {
    val scope = rememberCoroutineScope()
    val viewModel = remember { LoginViewModel(apiClient, scope) }
    val loginState by viewModel.state.collectAsState()
    LoginScreen(
        state = loginState,
        onUsernameChanged = viewModel::onUsernameChanged,
        onPasswordChanged = viewModel::onPasswordChanged,
        onLoginClick = viewModel::login,
    )
    LaunchedEffect(Unit) {
        viewModel.effects.collect { effect ->
            when (effect) {
                LoginEffect.NavigateToDashboard -> navigator.navigateTo(Route.Dashboard)
            }
        }
    }
}

@Composable
private fun OnboardingRoute(
    apiClient: ApiClient,
    navigator: Navigator,
) {
    val scope = rememberCoroutineScope()
    val viewModel = remember { OnboardingViewModel(apiClient, scope) }
    val onboardingState by viewModel.state.collectAsState()
    OnboardingScreen(
        state = onboardingState,
        onUsernameChanged = viewModel::onUsernameChanged,
        onEmailChanged = viewModel::onEmailChanged,
        onFullNameChanged = viewModel::onFullNameChanged,
        onPasswordChanged = viewModel::onPasswordChanged,
        onConfirmPasswordChanged = viewModel::onConfirmPasswordChanged,
        onSetupClick = viewModel::onboard,
    )
    LaunchedEffect(Unit) {
        viewModel.effects.collect { effect ->
            when (effect) {
                OnboardingEffect.NavigateToLogin -> navigator.navigateTo(Route.Login)
            }
        }
    }
}
