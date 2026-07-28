package com.inframap.frontend.navigation

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class Navigator(
    initialRoute: Route = Route.Splash,
) {
    private val _currentRoute = MutableStateFlow(initialRoute)
    val currentRoute: StateFlow<Route> = _currentRoute.asStateFlow()

    fun navigateTo(route: Route) {
        _currentRoute.value = route
    }
}
