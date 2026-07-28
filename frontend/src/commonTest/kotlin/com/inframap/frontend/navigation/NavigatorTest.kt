package com.inframap.frontend.navigation

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class NavigatorTest {
    @Test
    fun defaultInitialRouteIsSplash() {
        val navigator = Navigator()
        assertIs<Route.Splash>(navigator.currentRoute.value)
    }

    @Test
    fun customInitialRoute() {
        val navigator = Navigator(initialRoute = Route.Login)
        assertIs<Route.Login>(navigator.currentRoute.value)
    }

    @Test
    fun navigateToUpdatesCurrentRoute() {
        val navigator = Navigator()
        navigator.navigateTo(Route.Dashboard)
        assertIs<Route.Dashboard>(navigator.currentRoute.value)
    }

    @Test
    fun multipleNavigationsUpdateCorrectly() {
        val navigator = Navigator()
        navigator.navigateTo(Route.Login)
        assertIs<Route.Login>(navigator.currentRoute.value)

        navigator.navigateTo(Route.Onboarding)
        assertIs<Route.Onboarding>(navigator.currentRoute.value)

        navigator.navigateTo(Route.Dashboard)
        assertIs<Route.Dashboard>(navigator.currentRoute.value)
    }

    @Test
    fun navigateToSameRouteIsIdempotent() {
        val navigator = Navigator()
        navigator.navigateTo(Route.Devices)
        navigator.navigateTo(Route.Devices)
        assertIs<Route.Devices>(navigator.currentRoute.value)
    }

    @Test
    fun allRoutesAreNavigable() {
        val navigator = Navigator()
        val routes =
            listOf(
                Route.Splash,
                Route.Login,
                Route.Onboarding,
                Route.Dashboard,
                Route.Devices,
                Route.Staging,
                Route.Subnets,
                Route.Topology,
            )
        routes.forEach { route ->
            navigator.navigateTo(route)
            assertEquals(route, navigator.currentRoute.value)
        }
    }
}
