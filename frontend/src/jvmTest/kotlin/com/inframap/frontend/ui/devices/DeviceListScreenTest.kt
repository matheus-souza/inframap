package com.inframap.frontend.ui.devices

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runComposeUiTest
import com.inframap.frontend.designsystem.InfraMapTheme
import com.inframap.frontend.domain.model.Device
import com.inframap.frontend.generated.resources.Res
import com.inframap.frontend.generated.resources.devices_action_delete
import com.inframap.frontend.generated.resources.devices_action_edit
import com.inframap.frontend.generated.resources.devices_action_view
import com.inframap.frontend.generated.resources.devices_empty_title
import com.inframap.frontend.generated.resources.devices_new_button
import com.inframap.frontend.generated.resources.devices_title
import kotlinx.coroutines.runBlocking
import org.jetbrains.compose.resources.getString
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
        onDeviceClicked = onDeviceClicked,
        onCreateDeviceClicked = {},
        onEditDeviceClicked = onEditDeviceClicked,
        onDeleteDeviceClicked = onDeleteDeviceClicked,
        onConfirmDelete = {},
        onCancelDelete = {},
        onRetryClicked = {},
        onDismissToast = {},
        onDismissDeleteError = {},
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

            onNodeWithText(runBlocking { getString(Res.string.devices_title) }).assertIsDisplayed()
            onNodeWithText(runBlocking { getString(Res.string.devices_new_button) }).assertIsDisplayed()
            onNodeWithText(runBlocking { getString(Res.string.devices_empty_title) }).assertIsDisplayed()
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
            onNodeWithText(runBlocking { getString(Res.string.devices_action_view) }).performClick()
            assertEquals("dev-1", clickedDeviceId)

            onNodeWithText(runBlocking { getString(Res.string.devices_action_edit) }).performClick()
            assertEquals("dev-1", editedDeviceId)

            onNodeWithText(runBlocking { getString(Res.string.devices_action_delete) }).performClick()
            assertEquals("dev-1", deletedDevice?.id)
        }
}
