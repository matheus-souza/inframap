package com.inframap.frontend.ui.dashboard

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.runComposeUiTest
import com.inframap.frontend.designsystem.InfraMapTheme
import com.inframap.frontend.domain.model.Device
import org.junit.jupiter.api.Test

@OptIn(ExperimentalTestApi::class)
class DashboardWidgetsTest {
    @Test
    fun dashboardKpiRowRenders4CardsWithTitlesAndBadges() =
        runComposeUiTest {
            val state =
                DashboardUiState(
                    totalActiveDevices = 42L,
                    onlineDevicesCount = 36L,
                    totalSubnetsMonitored = 5L,
                    discoveryEngineStatus = DiscoveryEngineStatus.RUNNING,
                    totalStagedDevices = 12L,
                    isLoading = false,
                )

            setContent {
                InfraMapTheme {
                    DashboardKpiRow(state = state)
                }
            }

            onNodeWithText("Total Dispositivos").assertIsDisplayed()
            onNodeWithText("42").assertIsDisplayed()
            onNodeWithText("85% online").assertIsDisplayed()

            onNodeWithText("Subredes Monitoradas").assertIsDisplayed()
            onNodeWithText("5").assertIsDisplayed()

            onNodeWithText("Discovery Engine").assertIsDisplayed()
            onNodeWithText("Running").assertIsDisplayed()
            onNodeWithText("SCANNING").assertIsDisplayed()

            onNodeWithText("Fila de Staging").assertIsDisplayed()
            onNodeWithText("12").assertIsDisplayed()
            onNodeWithText("12 Pendentes").assertIsDisplayed()
        }

    @Test
    fun recentDevicesWidgetRendersTableAndStatusPills() =
        runComposeUiTest {
            val devices =
                listOf(
                    Device(id = "1", hostname = "core-router-01", ipAddress = "192.168.1.1", deviceType = "ROUTER", status = "ACTIVE"),
                    Device(id = "2", hostname = "edge-switch-02", ipAddress = "192.168.1.2", deviceType = "SWITCH", status = "OFFLINE"),
                )

            setContent {
                InfraMapTheme {
                    RecentDevicesWidget(devices = devices)
                }
            }

            onNodeWithText("Dispositivos Recentes").assertIsDisplayed()
            onNodeWithText("core-router-01").assertIsDisplayed()
            onNodeWithText("192.168.1.1").assertIsDisplayed()
            onNodeWithText("Online").assertIsDisplayed()

            onNodeWithText("edge-switch-02").assertIsDisplayed()
            onNodeWithText("192.168.1.2").assertIsDisplayed()
            onNodeWithText("Offline").assertIsDisplayed()
        }

    @Test
    fun recentDevicesWidgetDisplaysEmptyState() =
        runComposeUiTest {
            setContent {
                InfraMapTheme {
                    RecentDevicesWidget(devices = emptyList())
                }
            }

            onNodeWithText("Dispositivos Recentes").assertIsDisplayed()
            onNodeWithText("Nenhum dispositivo encontrado no inventário.").assertIsDisplayed()
        }

    @Test
    fun liveEventsWidgetRendersStreamEvents() =
        runComposeUiTest {
            val events =
                listOf(
                    DashboardEventItem(
                        id = "evt_1",
                        timestamp = "19:20:15",
                        eventType = "DiscoveryProgress",
                        message = "Scan started on 192.168.1.0/24",
                    ),
                    DashboardEventItem(
                        id = "evt_2",
                        timestamp = "19:20:18",
                        eventType = "DeviceCreated",
                        message = "New switch discovered at 192.168.1.50",
                    ),
                )

            setContent {
                InfraMapTheme {
                    LiveEventsWidget(events = events)
                }
            }

            onNodeWithText("Live SSE Stream").assertIsDisplayed()
            onNodeWithText("19:20:15").assertIsDisplayed()
            onNodeWithText("DiscoveryProgress").assertIsDisplayed()
            onNodeWithText("Scan started on 192.168.1.0/24").assertIsDisplayed()

            onNodeWithText("19:20:18").assertIsDisplayed()
            onNodeWithText("DeviceCreated").assertIsDisplayed()
            onNodeWithText("New switch discovered at 192.168.1.50").assertIsDisplayed()
        }

    @Test
    fun dashboardScreenRendersCompleteOverviewLayout() =
        runComposeUiTest {
            val state =
                DashboardUiState(
                    totalActiveDevices = 100L,
                    onlineDevicesCount = 90L,
                    totalSubnetsMonitored = 8L,
                    discoveryEngineStatus = DiscoveryEngineStatus.IDLE,
                    totalStagedDevices = 3L,
                    recentDevices =
                        listOf(
                            Device(id = "1", hostname = "gateway-01", ipAddress = "10.0.0.1", deviceType = "GATEWAY", status = "ACTIVE"),
                        ),
                    liveEvents =
                        listOf(
                            DashboardEventItem(id = "1", timestamp = "19:00:00", eventType = "Connected", message = "Connected"),
                        ),
                    isLoading = false,
                )

            setContent {
                InfraMapTheme {
                    DashboardScreen(state = state, onRefresh = {})
                }
            }

            onNodeWithText("Dashboard Overview").assertIsDisplayed()
            onNodeWithText("Total Dispositivos").assertIsDisplayed()
            onNodeWithText("Dispositivos Recentes").assertExists()
            onNodeWithText("gateway-01").assertExists()
            onNodeWithText("Live SSE Stream").assertExists()
        }
}
