package com.inframap.frontend.ui.devices

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runComposeUiTest
import com.inframap.frontend.designsystem.InfraMapTheme
import com.inframap.frontend.domain.model.Device
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

@OptIn(ExperimentalTestApi::class)
class DeviceListScreenTest {
    private fun testActions(
        onDeviceClicked: (String) -> Unit = {},
        onEditDeviceClicked: (String) -> Unit = {},
        onDeleteDeviceClicked: (Device) -> Unit = {},
    ) = DeviceListActions(
        onSearchQueryChanged = {},
        onPageChanged = {},
        onCreateDeviceClicked = {},
        onDeviceClicked = onDeviceClicked,
        onEditDeviceClicked = onEditDeviceClicked,
        onDeleteDeviceClicked = onDeleteDeviceClicked,
        onConfirmDelete = {},
        onCancelDelete = {},
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
            var clickedDeviceId: String? = null
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
                                onDeviceClicked = { clickedDeviceId = it },
                                onEditDeviceClicked = { editedDeviceId = it },
                                onDeleteDeviceClicked = { deletedDevice = it },
                            ),
                    )
                }
            }

            onNodeWithText("core-router-01").assertIsDisplayed()
            onNodeWithText("192.168.1.1").assertIsDisplayed()
            onNodeWithText("View").performClick()
            assertEquals("dev-1", clickedDeviceId)

            onNodeWithText("Edit").performClick()
            assertEquals("dev-1", editedDeviceId)

            onNodeWithText("Delete").performClick()
            assertEquals("dev-1", deletedDevice?.id)
        }
}
