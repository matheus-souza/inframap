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

    @Test
    fun expireSessionNavigatesToLogin() {
        val navigator = Navigator(initialRoute = Route.Subnets)
        navigator.expireSession()

        assertEquals(Route.Login, navigator.currentRoute.value)
        assertEquals(Route.Subnets, navigator.expiredRoute)
    }

    @Test
    fun completeLoginResumingReturnsToExpiredRoute() {
        val targetRoute = Route.CreateSubnet("10.0.0.0/24", "eth0")
        val navigator = Navigator(initialRoute = targetRoute)

        navigator.expireSession()
        assertEquals(Route.Login, navigator.currentRoute.value)

        navigator.completeLogin(resumePrevious = true)
        assertEquals(targetRoute, navigator.currentRoute.value)
        assertEquals(null, navigator.expiredRoute)
    }

    @Test
    fun completeLoginWithoutResumeGoesToDashboardAndForgetsRoute() {
        val navigator = Navigator(initialRoute = Route.CreateDiscoverySource)
        navigator.expireSession()

        navigator.completeLogin(resumePrevious = false)
        assertEquals(Route.Dashboard, navigator.currentRoute.value)
        assertEquals(null, navigator.expiredRoute)
    }

    @Test
    fun completeLoginResumingWithoutStoredRouteGoesToDashboard() {
        val navigator = Navigator(initialRoute = Route.Login)

        navigator.completeLogin(resumePrevious = true)
        assertEquals(Route.Dashboard, navigator.currentRoute.value)
    }

    @Test
    fun repeatedExpiryKeepsFirstRoute() {
        val navigator = Navigator(initialRoute = Route.CreateSubnet())
        navigator.expireSession()

        // Suppose another 401 triggers while navigating or on login screen
        navigator.navigateTo(Route.Login)
        navigator.expireSession()

        assertEquals(Route.CreateSubnet(), navigator.expiredRoute)
    }

    @Test
    fun expiryWhileOnLoginSplashOrOnboardingStoresNothing() {
        val splashNav = Navigator(initialRoute = Route.Splash)
        splashNav.expireSession()
        assertEquals(null, splashNav.expiredRoute)

        val loginNav = Navigator(initialRoute = Route.Login)
        loginNav.expireSession()
        assertEquals(null, loginNav.expiredRoute)

        val onboardingNav = Navigator(initialRoute = Route.Onboarding)
        onboardingNav.expireSession()
        assertEquals(null, onboardingNav.expiredRoute)
    }

    @Test
    fun returnRouteIsConsumedOnce() {
        val navigator = Navigator(initialRoute = Route.CreateSubnet())
        navigator.expireSession()

        navigator.completeLogin(resumePrevious = true)
        assertEquals(Route.CreateSubnet(), navigator.currentRoute.value)

        // Subsequent login without expire goes to dashboard
        navigator.navigateTo(Route.Login)
        navigator.completeLogin(resumePrevious = true)
        assertEquals(Route.Dashboard, navigator.currentRoute.value)
    }
}
