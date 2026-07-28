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
import com.inframap.frontend.designsystem.InfraMapTheme
import com.inframap.frontend.navigation.Navigator
import com.inframap.frontend.navigation.Route
import com.inframap.frontend.ui.splash.SplashEffect
import com.inframap.frontend.ui.splash.SplashScreen
import com.inframap.frontend.ui.splash.SplashViewModel

@Composable
fun InfraMapApp(apiClient: ApiClient) {
    val navigator = remember { Navigator() }
    val currentRoute by navigator.currentRoute.collectAsState()
    var isHealthy by remember { mutableStateOf<Boolean?>(null) }

    LaunchedEffect(Unit) {
        val result =
            apiClient.get<HealthDto>("/api/v1/health")
        isHealthy =
            when (result) {
                is ApiResult.Success -> result.data.status == "ok"
                else -> false
            }
    }

    InfraMapTheme {
        when (currentRoute) {
            Route.Splash -> {
                val scope = rememberCoroutineScope()
                val viewModel =
                    remember { SplashViewModel(apiClient, scope) }
                SplashScreen()
                LaunchedEffect(Unit) {
                    viewModel.checkAuthState()
                    viewModel.effects.collect { effect ->
                        when (effect) {
                            SplashEffect.NavigateToLogin ->
                                navigator.navigateTo(Route.Login)
                            SplashEffect.NavigateToOnboarding ->
                                navigator.navigateTo(Route.Onboarding)
                            SplashEffect.NavigateToDashboard ->
                                navigator.navigateTo(Route.Dashboard)
                        }
                    }
                }
            }
            Route.Login ->
                PlaceholderScreen("Login")
            Route.Onboarding ->
                PlaceholderScreen("Setup Wizard")
            else ->
                MainScaffold(currentRoute, navigator, isHealthy)
        }
    }
}
