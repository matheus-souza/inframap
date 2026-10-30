package com.inframap.frontend.ui.devices

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.runComposeUiTest
import com.inframap.frontend.designsystem.InfraMapTheme
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

@OptIn(ExperimentalTestApi::class)
class EditDeviceScreenTest {
    @Test
    fun rendersEditFormAndTriggersActions() =
        runComposeUiTest {
            var submitClicked = false
            var cancelClicked = false

            setContent {
                InfraMapTheme {
                    EditDeviceScreen(
                        state =
                            EditDeviceUiState(
                                deviceId = "dev-100",
                                hostname = "switch-edge",
                                status = "active",
                                isLoading = false,
                            ),
                        actions =
                            EditDeviceActions(
                                onHostnameChanged = {},
                                onIpAddressChanged = {},
                                onIpAssignmentChanged = {},
                                onMacAddressChanged = {},
                                onDeviceTypeChanged = {},
                                onStatusChanged = {},
                                onSubmitClicked = { submitClicked = true },
                                onCancelClicked = { cancelClicked = true },
                                onRetryClicked = {},
                            ),
                    )
                }
            }

            onNodeWithTag("legend_required_fields").assertIsDisplayed()
            onNodeWithText("Edit Device").assertIsDisplayed()
            onNodeWithText("Device Details").assertIsDisplayed()
            onNodeWithText("Status").assertIsDisplayed()

            onNodeWithText("Cancel").performScrollTo().performClick()
            assertTrue(cancelClicked)

            onNodeWithText("Save Changes").performScrollTo().performClick()
            assertTrue(submitClicked)
        }

    @Test
    fun selectsCustomDeviceTypeFromStandardOption() =
        runComposeUiTest {
            var selectedType = "router"

            setContent {
                InfraMapTheme {
                    EditDeviceScreen(
                        state =
                            EditDeviceUiState(
                                deviceId = "dev-100",
                                hostname = "router-core",
                                deviceType = selectedType,
                                status = "active",
                                isLoading = false,
                            ),
                        actions =
                            EditDeviceActions(
                                onHostnameChanged = {},
                                onIpAddressChanged = {},
                                onIpAssignmentChanged = {},
                                onMacAddressChanged = {},
                                onDeviceTypeChanged = { selectedType = it },
                                onStatusChanged = {},
                                onSubmitClicked = {},
                                onCancelClicked = {},
                                onRetryClicked = {},
                            ),
                    )
                }
            }

            onNodeWithText("Other").performClick()
            assertEquals("other", selectedType)
        }

    @Test
    fun rendersDangerZoneAndTriggersDelete() =
        runComposeUiTest {
            var deleteClicked = false

            setContent {
                InfraMapTheme {
                    EditDeviceScreen(
                        state =
                            EditDeviceUiState(
                                deviceId = "dev-100",
                                hostname = "switch-edge",
                                status = "active",
                                isLoading = false,
                            ),
                        actions =
                            EditDeviceActions(
                                onHostnameChanged = {},
                                onIpAddressChanged = {},
                                onIpAssignmentChanged = {},
                                onMacAddressChanged = {},
                                onDeviceTypeChanged = {},
                                onStatusChanged = {},
                                onSubmitClicked = {},
                                onCancelClicked = {},
                                onRetryClicked = {},
                                onDeleteClicked = { deleteClicked = true },
                            ),
                    )
                }
            }

            onNodeWithText("Danger Zone").performScrollTo().assertIsDisplayed()
            onNodeWithText("This device will be moved to deleted state. It will no longer be actively monitored.")
                .performScrollTo()
                .assertIsDisplayed()
            onNodeWithText("Delete switch-edge").performScrollTo().assertIsDisplayed()
            onNodeWithText("Delete switch-edge").performClick()
            assertTrue(deleteClicked)
        }

    @Test
    fun rendersDeleteConfirmationDialogAndTriggersConfirm() =
        runComposeUiTest {
            var confirmDeleteClicked = false

            setContent {
                InfraMapTheme {
                    EditDeviceScreen(
                        state =
                            EditDeviceUiState(
                                deviceId = "dev-100",
                                hostname = "switch-edge",
                                status = "active",
                                isLoading = false,
                                showDeleteDialog = true,
                            ),
                        actions =
                            EditDeviceActions(
                                onHostnameChanged = {},
                                onIpAddressChanged = {},
                                onIpAssignmentChanged = {},
                                onMacAddressChanged = {},
                                onDeviceTypeChanged = {},
                                onStatusChanged = {},
                                onSubmitClicked = {},
                                onCancelClicked = {},
                                onRetryClicked = {},
                                onConfirmDeleteClicked = { confirmDeleteClicked = true },
                            ),
                    )
                }
            }

            onNodeWithText("Delete Device").assertIsDisplayed()
            onNodeWithText("Are you sure you want to delete device", substring = true)
                .assertIsDisplayed()
            onNodeWithText("Delete").performClick()
            assertTrue(confirmDeleteClicked)
        }

    @Test
    fun picksTheAddressAssignment() =
        runComposeUiTest {
            var selected = ""

            setContent {
                InfraMapTheme {
                    EditDeviceScreen(
                        state =
                            EditDeviceUiState(
                                deviceId = "dev-200",
                                hostname = "nas",
                                ipAddress = "192.0.2.20",
                                deviceType = "server",
                                status = "active",
                                isLoading = false,
                            ),
                        actions =
                            EditDeviceActions(
                                onHostnameChanged = {},
                                onIpAddressChanged = {},
                                onIpAssignmentChanged = { selected = it },
                                onMacAddressChanged = {},
                                onDeviceTypeChanged = {},
                                onStatusChanged = {},
                                onSubmitClicked = {},
                                onCancelClicked = {},
                                onRetryClicked = {},
                            ),
                    )
                }
            }

            onNodeWithText("IP assignment").assertIsDisplayed()
            onNodeWithText("DHCP reservation").performScrollTo().performClick()
            assertEquals("reserved", selected)
        }
}
