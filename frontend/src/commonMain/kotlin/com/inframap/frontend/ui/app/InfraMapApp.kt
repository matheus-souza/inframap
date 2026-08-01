package com.inframap.frontend.ui.app

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import org.koin.compose.koinInject

@Composable
fun InfraMapApp() {
    val navigator = remember { Navigator() }
    val currentRoute by navigator.currentRoute.collectAsState()
    var isHealthy by remember { mutableStateOf<Boolean?>(null) }

    InfraMapTheme {
        when (currentRoute) {
            Route.Splash -> SplashRoute(navigator)
            Route.Login -> LoginRoute(navigator)
            Route.Onboarding -> OnboardingRoute(navigator)
            else ->
                MainScaffold(
                    currentRoute = currentRoute,
                    navigator = navigator,
                    isHealthy = isHealthy,
                    onHealthChanged = { isHealthy = it },
                )
        }
    }
}

@Composable
private fun SplashRoute(navigator: Navigator) {
    val viewModel: SplashViewModel = koinInject()
    SplashScreen()
    DisposableEffect(viewModel) {
        onDispose { viewModel.clear() }
    }
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
private fun LoginRoute(navigator: Navigator) {
    val viewModel: LoginViewModel = koinInject()
    val loginState by viewModel.state.collectAsState()
    DisposableEffect(viewModel) {
        onDispose { viewModel.clear() }
    }
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
private fun OnboardingRoute(navigator: Navigator) {
    val viewModel: OnboardingViewModel = koinInject()
    val onboardingState by viewModel.state.collectAsState()
    DisposableEffect(viewModel) {
        onDispose { viewModel.clear() }
    }
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
