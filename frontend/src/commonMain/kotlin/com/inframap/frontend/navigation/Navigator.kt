package com.inframap.frontend.navigation

import com.inframap.frontend.data.storage.InterruptedRouteStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class Navigator(
    initialRoute: Route = Route.Splash,
    private val interruptedRouteStore: InterruptedRouteStore? = null,
) {
    private val _currentRoute = MutableStateFlow(initialRoute)
    val currentRoute: StateFlow<Route> = _currentRoute.asStateFlow()

    internal var expiredRoute: Route? = null
        private set

    init {
        if (isRestorableWorkRoute(initialRoute)) {
            interruptedRouteStore?.save(initialRoute)
        }
    }

    fun navigateTo(route: Route) {
        if (isRestorableWorkRoute(route)) {
            interruptedRouteStore?.save(route)
        }
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
            interruptedRouteStore?.save(current)
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
        val restored = if (resumePrevious) (expiredRoute ?: interruptedRouteStore?.restore()) else null
        val target = restored ?: Route.Dashboard
        expiredRoute = null
        interruptedRouteStore?.clear()
        _currentRoute.value = target
    }
}
