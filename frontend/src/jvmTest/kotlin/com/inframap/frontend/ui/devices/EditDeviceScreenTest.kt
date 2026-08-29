package com.inframap.frontend.ui.devices

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
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

            onNodeWithText("Edit Device").assertIsDisplayed()
            onNodeWithText("Device Details").assertIsDisplayed()
            onNodeWithText("Status").assertIsDisplayed()

            onNodeWithText("Cancel").performClick()
            assertTrue(cancelClicked)

            onNodeWithText("Save Changes").performClick()
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
}
