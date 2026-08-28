package com.inframap.frontend.app

import com.inframap.frontend.generated.resources.Res
import com.inframap.frontend.generated.resources.create_device_title
import com.inframap.frontend.generated.resources.create_discovery_source_title
import com.inframap.frontend.generated.resources.create_subnet_title
import com.inframap.frontend.generated.resources.dashboard_title
import com.inframap.frontend.generated.resources.device_detail_title
import com.inframap.frontend.generated.resources.devices_title
import com.inframap.frontend.generated.resources.discovery_title
import com.inframap.frontend.generated.resources.edit_device_title
import com.inframap.frontend.generated.resources.staging_header
import com.inframap.frontend.generated.resources.subnets_title
import com.inframap.frontend.generated.resources.topology_title
import com.inframap.frontend.navigation.Route
import com.inframap.frontend.platform.updateBrowserTitle
import com.inframap.frontend.ui.app.resolveScreenTitleResource
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class BrowserTitleTest {
    @Test
    fun resolveScreenTitleReturnsExpectedTitleForDashboard() {
        assertEquals(Res.string.dashboard_title, resolveScreenTitleResource(Route.Dashboard))
    }

    @Test
    fun resolveScreenTitleReturnsExpectedTitleForDevices() {
        assertEquals(Res.string.devices_title, resolveScreenTitleResource(Route.Devices))
    }

    @Test
    fun resolveScreenTitleReturnsExpectedTitleForDeviceDetail() {
        assertEquals(Res.string.device_detail_title, resolveScreenTitleResource(Route.DeviceDetail("device-123")))
    }

    @Test
    fun resolveScreenTitleReturnsExpectedTitleForCreateDevice() {
        assertEquals(Res.string.create_device_title, resolveScreenTitleResource(Route.CreateDevice))
    }

    @Test
    fun resolveScreenTitleReturnsExpectedTitleForEditDevice() {
        assertEquals(Res.string.edit_device_title, resolveScreenTitleResource(Route.EditDevice("device-123")))
    }

    @Test
    fun resolveScreenTitleReturnsExpectedTitleForStaging() {
        assertEquals(Res.string.staging_header, resolveScreenTitleResource(Route.Staging))
    }

    @Test
    fun resolveScreenTitleReturnsExpectedTitleForSubnets() {
        assertEquals(Res.string.subnets_title, resolveScreenTitleResource(Route.Subnets))
    }

    @Test
    fun resolveScreenTitleReturnsExpectedTitleForCreateSubnet() {
        assertEquals(Res.string.create_subnet_title, resolveScreenTitleResource(Route.CreateSubnet()))
        assertEquals(
            Res.string.create_subnet_title,
            resolveScreenTitleResource(Route.CreateSubnet(prefilledCidr = "192.168.1.0/24", prefilledName = "Office")),
        )
    }

    @Test
    fun resolveScreenTitleReturnsExpectedTitleForDiscoverySources() {
        assertEquals(Res.string.discovery_title, resolveScreenTitleResource(Route.DiscoverySources))
    }

    @Test
    fun resolveScreenTitleReturnsExpectedTitleForCreateDiscoverySource() {
        assertEquals(Res.string.create_discovery_source_title, resolveScreenTitleResource(Route.CreateDiscoverySource))
    }

    @Test
    fun resolveScreenTitleReturnsExpectedTitleForTopology() {
        assertEquals(Res.string.topology_title, resolveScreenTitleResource(Route.Topology))
    }

    @Test
    fun resolveScreenTitleReturnsNullForNonScaffoldRoutes() {
        assertNull(resolveScreenTitleResource(Route.Splash))
        assertNull(resolveScreenTitleResource(Route.Login))
        assertNull(resolveScreenTitleResource(Route.Onboarding))
    }

    @Test
    fun updateBrowserTitleExecutesWithoutErrorOnJvm() {
        updateBrowserTitle("Dashboard")
        updateBrowserTitle(null)
    }
}
