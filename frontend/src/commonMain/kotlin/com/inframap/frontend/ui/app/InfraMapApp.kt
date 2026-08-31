package com.inframap.frontend.ui.app

import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.inframap.frontend.data.api.ApiClient
import com.inframap.frontend.designsystem.InfraMapTheme
import com.inframap.frontend.designsystem.motion.MotionTransitions
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
import org.koin.compose.currentKoinScope

private sealed interface RootDestination {
    data object Splash : RootDestination

    data object Login : RootDestination

    data object Onboarding : RootDestination

    data object Main : RootDestination
}

private fun Route.toRootDestination(): RootDestination =
    when (this) {
        Route.Splash -> RootDestination.Splash
        Route.Login -> RootDestination.Login
        Route.Onboarding -> RootDestination.Onboarding
        else -> RootDestination.Main
    }

private fun rootDestinationDepth(destination: RootDestination): Int =
    when (destination) {
        RootDestination.Splash -> 0
        RootDestination.Login, RootDestination.Onboarding -> 1
        RootDestination.Main -> 2
    }

@Composable
fun InfraMapApp() {
    val navigator = remember { Navigator() }
    val currentRoute by navigator.currentRoute.collectAsState()
    val rootDestination = remember(currentRoute) { currentRoute.toRootDestination() }
    var isHealthy by remember { mutableStateOf<Boolean?>(null) }
    val koinScope = currentKoinScope()
    val apiClient: ApiClient? = remember { runCatching { koinScope.get<ApiClient>() }.getOrNull() }

    DisposableEffect(apiClient, navigator) {
        apiClient?.onSessionExpired = {
            navigator.navigateTo(Route.Login)
        }
        onDispose {
            apiClient?.onSessionExpired = null
        }
    }

    LaunchedEffect(currentRoute) {
        println("[InfraMap-Navigation] Route transitioned to: $currentRoute")
    }

    InfraMapTheme {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background,
        ) {
            AnimatedContent(
                targetState = rootDestination,
                transitionSpec = {
                    val initialDepth = rootDestinationDepth(initialState)
                    val targetDepth = rootDestinationDepth(targetState)
                    MotionTransitions.sharedAxisZ(forward = targetDepth >= initialDepth)
                },
                label = "InfraMapAppRouteTransition",
            ) { destination ->
                when (destination) {
                    RootDestination.Splash -> SplashRoute(navigator)
                    RootDestination.Login -> LoginRoute(navigator)
                    RootDestination.Onboarding -> OnboardingRoute(navigator)
                    RootDestination.Main ->
                        MainScaffold(
                            currentRoute = currentRoute,
                            navigator = navigator,
                            isHealthy = isHealthy,
                            onHealthChanged = { isHealthy = it },
                        )
                }
            }
        }
    }
}

@Composable
private fun SplashRoute(navigator: Navigator) {
    val koinScope = currentKoinScope()
    val viewModel: SplashViewModel = remember { koinScope.get() }
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
    val koinScope = currentKoinScope()
    val viewModel: LoginViewModel = remember { koinScope.get() }
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
    val koinScope = currentKoinScope()
    val viewModel: OnboardingViewModel = remember { koinScope.get() }
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
