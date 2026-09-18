package com.inframap.frontend.ui.devices

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runComposeUiTest
import com.inframap.frontend.designsystem.InfraMapTheme
import com.inframap.frontend.domain.model.Device
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

@OptIn(ExperimentalTestApi::class)
class DeviceListScreenTest {
    private fun testActions(
        onDeviceClicked: (String) -> Unit = {},
        onEditDeviceClicked: (String) -> Unit = {},
        onDeleteDeviceClicked: (Device) -> Unit = {},
        onConfirmDelete: () -> Unit = {},
        onCancelDelete: () -> Unit = {},
    ) = DeviceListActions(
        onSearchQueryChanged = {},
        onPageChanged = {},
        onCreateDeviceClicked = {},
        onDeviceClicked = onDeviceClicked,
        onEditDeviceClicked = onEditDeviceClicked,
        onDeleteDeviceClicked = onDeleteDeviceClicked,
        onConfirmDelete = onConfirmDelete,
        onCancelDelete = onCancelDelete,
        onDismissDeleteError = {},
        onDismissToast = {},
        onRetryClicked = {},
    )

    @Test
    fun rendersEmptyStateWhenNoDevices() =
        runComposeUiTest {
            setContent {
                InfraMapTheme {
                    DeviceListScreen(
                        state = DeviceListUiState(devices = emptyList(), isLoading = false),
                        actions = testActions(),
                    )
                }
            }

            onNodeWithText("Devices").assertIsDisplayed()
            onNodeWithText("+ New Device").assertIsDisplayed()
            onNodeWithText("No devices in inventory").assertIsDisplayed()
        }

    @Test
    fun rendersDeviceTableAndTriggersActions() =
        runComposeUiTest {
            val sampleDevice =
                Device(
                    id = "dev-1",
                    hostname = "core-router-01",
                    ipAddress = "192.168.1.1",
                    macAddress = "AA:BB:CC:DD:EE:01",
                    deviceType = "Router",
                    status = "active",
                )
            var editedDeviceId: String? = null
            var deletedDevice: Device? = null

            setContent {
                InfraMapTheme {
                    DeviceListScreen(
                        state =
                            DeviceListUiState(
                                devices = listOf(sampleDevice),
                                totalItems = 1,
                                isLoading = false,
                            ),
                        actions =
                            testActions(
                                onEditDeviceClicked = { editedDeviceId = it },
                                onDeleteDeviceClicked = { deletedDevice = it },
                            ),
                    )
                }
            }

            onNodeWithText("core-router-01").assertIsDisplayed()
            onNodeWithText("192.168.1.1").assertIsDisplayed()

            // Clicking row navigates to edit
            onNodeWithText("core-router-01").performClick()
            assertEquals("dev-1", editedDeviceId)

            // Open overflow menu and click edit
            editedDeviceId = null
            onNodeWithContentDescription("More options").performClick()
            onNodeWithText("Edit").performClick()
            assertEquals("dev-1", editedDeviceId)

            // Open overflow menu and click delete
            onNodeWithContentDescription("More options").performClick()
            onNodeWithText("Delete").performClick()
            assertEquals("dev-1", deletedDevice?.id)
        }

    @Test
    fun rendersImpactConfirmationDialogAndTriggersConfirm() =
        runComposeUiTest {
            val sampleDevice =
                Device(
                    id = "dev-1",
                    hostname = "core-router-01",
                    ipAddress = "192.168.1.1",
                    macAddress = "AA:BB:CC:DD:EE:01",
                    deviceType = "Router",
                    status = "active",
                )
            var confirmDeleteCalled = false

            setContent {
                InfraMapTheme {
                    DeviceListScreen(
                        state =
                            DeviceListUiState(
                                devices = listOf(sampleDevice),
                                totalItems = 1,
                                isLoading = false,
                                deviceToDelete = sampleDevice,
                            ),
                        actions =
                            testActions(
                                onConfirmDelete = { confirmDeleteCalled = true },
                            ),
                    )
                }
            }

            onNodeWithText("Delete Device").assertIsDisplayed()
            onNodeWithText("Are you sure you want to delete device", substring = true)
                .assertIsDisplayed()
            onNodeWithText("Delete").performClick()
            assertTrue(confirmDeleteCalled)
        }

    @Test
    fun rendersInactiveSourceBadgeWhenDiscoverySourceInactive() =
        runComposeUiTest {
            val sampleDevice =
                Device(
                    id = "dev-1",
                    hostname = "core-router-01",
                    ipAddress = "192.168.1.1",
                    macAddress = "AA:BB:CC:DD:EE:01",
                    deviceType = "Router",
                    status = "active",
                    discoverySourceInactive = true,
                )

            setContent {
                InfraMapTheme {
                    DeviceListScreen(
                        state =
                            DeviceListUiState(
                                devices = listOf(sampleDevice),
                                totalItems = 1,
                                isLoading = false,
                            ),
                        actions = testActions(),
                    )
                }
            }

            onNodeWithTag("device_discovery_source_inactive_dev-1", useUnmergedTree = true)
                .assertIsDisplayed()
            onNodeWithText("Inactive source", useUnmergedTree = true)
                .assertIsDisplayed()
        }
}
