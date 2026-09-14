package com.inframap.frontend.data.storage

import com.inframap.frontend.fakes.FakeEpochClock
import com.inframap.frontend.fakes.FakeLocalStorage
import com.inframap.frontend.navigation.Route
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class InterruptedRouteStoreTest {
    private val storage = FakeLocalStorage()
    private val clock = FakeEpochClock(now = 1_000_000L)
    private val sessionOwner = SessionOwnerStore(storage)
    private val store = InterruptedRouteStore(storage, clock, sessionOwner)

    @Test
    fun saveThenRestoreReturnsSameRoute() {
        sessionOwner.setOwner("user-1")
        val route = Route.CreateSubnet(prefilledCidr = "10.0.0.0/24", prefilledName = "Main")

        store.save(route)
        val restored = store.restore()

        assertEquals(route, restored)
    }

    @Test
    fun saveWritesEnvelopeWithOwnerVersionAndTimestamp() {
        sessionOwner.setOwner("user-1")
        clock.now = 5_000_000L

        store.save(Route.CreateDiscoverySource)
        val raw = storage.get(InterruptedRouteStore.KEY_INTERRUPTED_ROUTE)

        assertNotNull(raw)
        assertTrue(raw.contains("\"version\":1"))
        assertTrue(raw.contains("\"owner_user_id\":\"user-1\""))
        assertTrue(raw.contains("\"saved_at_ms\":5000000"))
        assertTrue(raw.contains("\"type\":\"create_discovery_source\""))

        val restored = store.restore()
        assertEquals(Route.CreateDiscoverySource, restored)
    }

    @Test
    fun restoreAtomicallyConsumesRoute() {
        sessionOwner.setOwner("user-1")
        store.save(Route.Topology)

        val first = store.restore()
        val second = store.restore()

        assertEquals(Route.Topology, first)
        assertNull(second)
        assertNull(storage.get(InterruptedRouteStore.KEY_INTERRUPTED_ROUTE))
    }

    @Test
    fun saveIsNoOpWhenOwnerUnknown() {
        // No owner set
        store.save(Route.CreateSubnet())

        assertNull(storage.get(InterruptedRouteStore.KEY_INTERRUPTED_ROUTE))
        assertNull(store.restore())
    }

    @Test
    fun restorePurgesWhenOwnerDiffers() {
        sessionOwner.setOwner("user-1")
        store.save(Route.CreateSubnet())

        // Owner changes
        sessionOwner.setOwner("user-2")
        val restored = store.restore()

        assertNull(restored)
        assertNull(storage.get(InterruptedRouteStore.KEY_INTERRUPTED_ROUTE))
    }

    @Test
    fun restorePurgesWhenExpired() {
        sessionOwner.setOwner("user-1")
        clock.now = 1_000_000L
        store.save(Route.CreateSubnet())

        // Advance clock past 30-min TTL
        clock.now = 1_000_000L + ROUTE_TTL_MS + 1L
        val restored = store.restore()

        assertNull(restored)
        assertNull(storage.get(InterruptedRouteStore.KEY_INTERRUPTED_ROUTE))
    }

    @Test
    fun clearRemovesStoredRoute() {
        sessionOwner.setOwner("user-1")
        store.save(Route.Devices)

        store.clear()

        assertNull(store.restore())
        assertNull(storage.get(InterruptedRouteStore.KEY_INTERRUPTED_ROUTE))
    }

    @Test
    fun polymorphicRouteVariantsSerializeAccurately() {
        sessionOwner.setOwner("user-1")

        val routes =
            listOf(
                Route.Splash,
                Route.Login,
                Route.Onboarding,
                Route.Dashboard,
                Route.Devices,
                Route.DeviceDetail(id = "dev-42"),
                Route.CreateDevice,
                Route.EditDevice(id = "dev-99"),
                Route.Staging,
                Route.Subnets,
                Route.CreateSubnet(prefilledCidr = "192.168.1.0/24", prefilledName = "Office"),
                Route.DiscoverySources,
                Route.CreateDiscoverySource,
                Route.Topology,
            )

        routes.forEach { route ->
            store.save(route)
            val restored = store.restore()
            assertEquals(route, restored, "Failed to round-trip route: $route")
        }
    }

    @Test
    fun restoreReturnsNullForMalformedJson() {
        sessionOwner.setOwner("user-1")
        storage.set(InterruptedRouteStore.KEY_INTERRUPTED_ROUTE, "{not valid json}")

        val restored = store.restore()

        assertNull(restored)
        assertNull(storage.get(InterruptedRouteStore.KEY_INTERRUPTED_ROUTE))
    }

    @Test
    fun restoreReturnsNullForVersionMismatch() {
        sessionOwner.setOwner("user-1")
        val raw =
            """{"version":999,"owner_user_id":"user-1","saved_at_ms":${clock.now},"route":{"type":"topology"}}"""
        storage.set(InterruptedRouteStore.KEY_INTERRUPTED_ROUTE, raw)

        val restored = store.restore()

        assertNull(restored)
        assertNull(storage.get(InterruptedRouteStore.KEY_INTERRUPTED_ROUTE))
    }

    @Test
    fun restoreReturnsNullForFutureTimestamp() {
        sessionOwner.setOwner("user-1")
        clock.now = 1_000_000L
        store.save(Route.Topology)

        clock.now = 999_999L
        val restored = store.restore()

        assertNull(restored)
        assertNull(storage.get(InterruptedRouteStore.KEY_INTERRUPTED_ROUTE))
    }

    @Test
    fun exactTtlBoundaryBehavior() {
        sessionOwner.setOwner("user-1")
        clock.now = 1_000_000L
        store.save(Route.CreateSubnet())

        // TTL - 1 ms: still valid!
        clock.now = 1_000_000L + ROUTE_TTL_MS - 1L
        val stillValid = store.restore()
        assertNotNull(stillValid)

        // Save again and test exact TTL boundary: age == ROUTE_TTL_MS rejected
        clock.now = 2_000_000L
        store.save(Route.CreateSubnet())
        clock.now = 2_000_000L + ROUTE_TTL_MS
        val expired = store.restore()
        assertNull(expired)
        assertNull(storage.get(InterruptedRouteStore.KEY_INTERRUPTED_ROUTE))
    }

    @Test
    fun restorePurgesOnUnknownRouteType() {
        sessionOwner.setOwner("user-1")
        val raw =
            """{"version":1,"owner_user_id":"user-1","saved_at_ms":${clock.now},"route":{"type":"unknown_route_xyz"}}"""
        storage.set(InterruptedRouteStore.KEY_INTERRUPTED_ROUTE, raw)

        val restored = store.restore()
        assertNull(restored)
        assertNull(storage.get(InterruptedRouteStore.KEY_INTERRUPTED_ROUTE))
    }

    @Test
    fun restoreReturnsNullWhenOwnerIsBlankOrUnset() {
        val json =
            """{"version":1,"owner_user_id":"user-1","saved_at_ms":${clock.now},"route":{"type":"topology"}}"""
        storage.set(InterruptedRouteStore.KEY_INTERRUPTED_ROUTE, json)
        sessionOwner.clearOwner()

        val restored = store.restore()

        assertNull(restored)
        assertNull(storage.get(InterruptedRouteStore.KEY_INTERRUPTED_ROUTE))
    }
}
