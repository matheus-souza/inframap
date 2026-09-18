package com.inframap.frontend.navigation

import com.inframap.frontend.data.storage.InterruptedRouteStore
import com.inframap.frontend.data.storage.ROUTE_TTL_MS
import com.inframap.frontend.data.storage.SessionOwnerStore
import com.inframap.frontend.fakes.FakeEpochClock
import com.inframap.frontend.fakes.FakeLocalStorage
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull

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
                Route.EditSubnet("sub-1"),
                Route.DiscoverySources,
                Route.CreateDiscoverySource,
                Route.EditDiscoverySource("src-1"),
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

    @Test
    fun completeLoginResumesRouteFromInterruptedRouteStoreAcrossNavigatorRecreation() {
        val storage = FakeLocalStorage()
        val clock = FakeEpochClock(now = 1_000_000L)
        val sessionOwner = SessionOwnerStore(storage)
        sessionOwner.setOwner("user-1")
        val store = InterruptedRouteStore(storage, clock, sessionOwner)

        val targetRoute = Route.CreateSubnet(prefilledCidr = "192.168.1.0/24")
        val navBeforeReload = Navigator(initialRoute = targetRoute, interruptedRouteStore = store)
        navBeforeReload.expireSession()
        assertEquals(Route.Login, navBeforeReload.currentRoute.value)

        // Simulating hard reload: new Navigator instance created with Route.Splash,
        // expiredRoute in-memory is null
        val navAfterReload = Navigator(initialRoute = Route.Splash, interruptedRouteStore = store)
        assertNull(navAfterReload.expiredRoute)

        navAfterReload.completeLogin(resumePrevious = true)
        assertEquals(targetRoute, navAfterReload.currentRoute.value)
    }

    @Test
    fun completeLoginWithFalseDoesNotResumeFromInterruptedRouteStore() {
        val storage = FakeLocalStorage()
        val clock = FakeEpochClock(now = 1_000_000L)
        val sessionOwner = SessionOwnerStore(storage)
        sessionOwner.setOwner("user-1")
        val store = InterruptedRouteStore(storage, clock, sessionOwner)

        val navBeforeReload = Navigator(initialRoute = Route.CreateDiscoverySource, interruptedRouteStore = store)
        navBeforeReload.expireSession()

        val navAfterReload = Navigator(initialRoute = Route.Splash, interruptedRouteStore = store)
        navAfterReload.completeLogin(resumePrevious = false)

        assertEquals(Route.Dashboard, navAfterReload.currentRoute.value)
        assertNull(store.restore())
    }

    @Test
    fun inMemoryExpiredRouteTakesPriorityOverStoredRoute() {
        val storage = FakeLocalStorage()
        val clock = FakeEpochClock(now = 1_000_000L)
        val sessionOwner = SessionOwnerStore(storage)
        sessionOwner.setOwner("user-1")
        val store = InterruptedRouteStore(storage, clock, sessionOwner)
        store.save(Route.Topology)

        val nav = Navigator(initialRoute = Route.CreateSubnet(), interruptedRouteStore = store)
        nav.expireSession()

        nav.completeLogin(resumePrevious = true)
        assertEquals(Route.CreateSubnet(), nav.currentRoute.value)
    }

    @Test
    fun expiredStoredRouteFallsBackToDashboard() {
        val storage = FakeLocalStorage()
        val clock = FakeEpochClock(now = 1_000_000L)
        val sessionOwner = SessionOwnerStore(storage)
        sessionOwner.setOwner("user-1")
        val store = InterruptedRouteStore(storage, clock, sessionOwner)
        store.save(Route.CreateSubnet())

        clock.now = 1_000_000L + ROUTE_TTL_MS + 10_000L

        val nav = Navigator(initialRoute = Route.Splash, interruptedRouteStore = store)
        nav.completeLogin(resumePrevious = true)

        assertEquals(Route.Dashboard, nav.currentRoute.value)
    }

    @Test
    fun splashLoginAndOnboardingAreNeverSavedToInterruptedRouteStore() {
        val storage = FakeLocalStorage()
        val clock = FakeEpochClock(now = 1_000_000L)
        val sessionOwner = SessionOwnerStore(storage)
        sessionOwner.setOwner("user-1")
        val store = InterruptedRouteStore(storage, clock, sessionOwner)

        val nav = Navigator(initialRoute = Route.Splash, interruptedRouteStore = store)
        nav.navigateTo(Route.Login)
        assertNull(storage.get(InterruptedRouteStore.KEY_INTERRUPTED_ROUTE))

        nav.navigateTo(Route.Onboarding)
        assertNull(storage.get(InterruptedRouteStore.KEY_INTERRUPTED_ROUTE))

        nav.navigateTo(Route.Splash)
        assertNull(storage.get(InterruptedRouteStore.KEY_INTERRUPTED_ROUTE))
    }
}
