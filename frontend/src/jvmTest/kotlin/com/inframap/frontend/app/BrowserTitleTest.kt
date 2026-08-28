package com.inframap.frontend.app

import com.inframap.frontend.navigation.Route
import com.inframap.frontend.platform.updateBrowserTitle
import com.inframap.frontend.ui.app.resolveScreenTitle
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class BrowserTitleTest {
    @Test
    fun resolveScreenTitleReturnsExpectedTitleForDashboard() {
        assertEquals("Dashboard", resolveScreenTitle(Route.Dashboard))
    }

    @Test
    fun resolveScreenTitleReturnsExpectedTitleForDevices() {
        assertEquals("Dispositivos", resolveScreenTitle(Route.Devices))
    }

    @Test
    fun resolveScreenTitleReturnsExpectedTitleForDeviceDetail() {
        assertEquals("Detalhe do Dispositivo", resolveScreenTitle(Route.DeviceDetail("device-123")))
    }

    @Test
    fun resolveScreenTitleReturnsExpectedTitleForCreateDevice() {
        assertEquals("Novo Dispositivo", resolveScreenTitle(Route.CreateDevice))
    }

    @Test
    fun resolveScreenTitleReturnsExpectedTitleForEditDevice() {
        assertEquals("Editar Dispositivo", resolveScreenTitle(Route.EditDevice("device-123")))
    }

    @Test
    fun resolveScreenTitleReturnsExpectedTitleForStaging() {
        assertEquals("Fila de Staging", resolveScreenTitle(Route.Staging))
    }

    @Test
    fun resolveScreenTitleReturnsExpectedTitleForSubnets() {
        assertEquals("Sub-redes", resolveScreenTitle(Route.Subnets))
    }

    @Test
    fun resolveScreenTitleReturnsExpectedTitleForCreateSubnet() {
        assertEquals("Nova Sub-rede", resolveScreenTitle(Route.CreateSubnet()))
        assertEquals("Nova Sub-rede", resolveScreenTitle(Route.CreateSubnet(prefilledCidr = "192.168.1.0/24", prefilledName = "Office")))
    }

    @Test
    fun resolveScreenTitleReturnsExpectedTitleForDiscoverySources() {
        assertEquals("Fontes de Descoberta", resolveScreenTitle(Route.DiscoverySources))
    }

    @Test
    fun resolveScreenTitleReturnsExpectedTitleForCreateDiscoverySource() {
        assertEquals("Nova Fonte de Descoberta", resolveScreenTitle(Route.CreateDiscoverySource))
    }

    @Test
    fun resolveScreenTitleReturnsExpectedTitleForTopology() {
        assertEquals("Topologia", resolveScreenTitle(Route.Topology))
    }

    @Test
    fun resolveScreenTitleReturnsNullForNonScaffoldRoutes() {
        assertNull(resolveScreenTitle(Route.Splash))
        assertNull(resolveScreenTitle(Route.Login))
        assertNull(resolveScreenTitle(Route.Onboarding))
    }

    @Test
    fun updateBrowserTitleExecutesWithoutErrorOnJvm() {
        updateBrowserTitle("Dashboard")
        updateBrowserTitle(null)
    }
}
