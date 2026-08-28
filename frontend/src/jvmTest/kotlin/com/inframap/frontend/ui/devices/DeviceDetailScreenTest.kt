package com.inframap.frontend.ui.devices

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runComposeUiTest
import com.inframap.frontend.designsystem.InfraMapTheme
import com.inframap.frontend.domain.model.Device
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

@OptIn(ExperimentalTestApi::class)
class DeviceDetailScreenTest {
    @Test
    fun rendersDeviceDetailsAndInteractions() =
        runComposeUiTest {
            val device =
                Device(
                    id = "dev-42",
                    hostname = "core-switch-01",
                    ipAddress = "10.0.0.1",
                    macAddress = "00:11:22:33:44:55",
                    deviceType = "Switch",
                    status = "active",
                    manufacturer = "Cisco",
                    model = "Catalyst 9300",
                    serialNumber = "SN123456",
                    createdAt = "2026-01-01",
                    updatedAt = "2026-01-02",
                )
            var backClicked = false
            var editClickedId: String? = null
            var deleteClicked = false

            setContent {
                InfraMapTheme {
                    DeviceDetailScreen(
                        state = DeviceDetailUiState(device = device, isLoading = false),
                        actions =
                            DeviceDetailActions(
                                onBackClicked = { backClicked = true },
                                onEditClicked = { editClickedId = it },
                                onDeleteClicked = { deleteClicked = true },
                                onConfirmDelete = {},
                                onCancelDelete = {},
                                onRetryClicked = {},
                            ),
                    )
                }
            }

            onAllNodesWithText("core-switch-01")[0].assertIsDisplayed()
            onNodeWithText("ID: dev-42").assertIsDisplayed()
            onNodeWithText("Main Information").assertIsDisplayed()
            onNodeWithText("Hardware & Manufacturer").assertIsDisplayed()
            onNodeWithText("Cisco").assertIsDisplayed()
            onNodeWithText("Catalyst 9300").assertIsDisplayed()

            onNodeWithText("Back").performClick()
            assertTrue(backClicked)

            onNodeWithText("Edit").performClick()
            assertEquals("dev-42", editClickedId)

            onNodeWithText("Delete").performClick()
            assertTrue(deleteClicked)
        }
}
