package com.inframap.frontend.navigation

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class Navigator(
    initialRoute: Route = Route.Splash,
) {
    private val _currentRoute = MutableStateFlow(initialRoute)
    val currentRoute: StateFlow<Route> = _currentRoute.asStateFlow()

    internal var expiredRoute: Route? = null
        private set

    fun navigateTo(route: Route) {
        _currentRoute.value = route
    }

    /**
     * Called when session expires (401). Remembers the current route if it is a restorable
     * work route (ignoring Splash, Login, and Onboarding), and only if no expired route
     * is already waiting. Then navigates to Login. (R19)
     */
    fun expireSession() {
        val current = _currentRoute.value
        if (expiredRoute == null && isRestorableWorkRoute(current)) {
            expiredRoute = current
        }
        _currentRoute.value = Route.Login
    }

    private fun isRestorableWorkRoute(route: Route): Boolean =
        when (route) {
            is Route.Splash,
            is Route.Login,
            is Route.Onboarding,
            -> false
            else -> true
        }

    /**
     * Called upon successful login completion. If [resumePrevious] is true and an expired
     * route was captured, resumes to that route; otherwise navigates to [Route.Dashboard].
     * Clears any remembered expired route. (R20)
     */
    fun completeLogin(resumePrevious: Boolean) {
        val target = if (resumePrevious) expiredRoute ?: Route.Dashboard else Route.Dashboard
        expiredRoute = null
        _currentRoute.value = target
    }
}
