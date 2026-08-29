package com.inframap.frontend.ui.devices

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runComposeUiTest
import com.inframap.frontend.designsystem.InfraMapTheme
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

@OptIn(ExperimentalTestApi::class)
class CreateDeviceScreenTest {
    @Test
    fun rendersFormFieldsAndButtons() =
        runComposeUiTest {
            var submitClicked = false
            var cancelClicked = false

            setContent {
                InfraMapTheme {
                    CreateDeviceScreen(
                        state = CreateDeviceUiState(),
                        actions =
                            CreateDeviceActions(
                                onHostnameChanged = {},
                                onIpAddressChanged = {},
                                onMacAddressChanged = {},
                                onDeviceTypeChanged = {},
                                onSubmitClicked = { submitClicked = true },
                                onCancelClicked = { cancelClicked = true },
                            ),
                    )
                }
            }

            onNodeWithText("New Device").assertIsDisplayed()
            onNodeWithText("Device Details").assertIsDisplayed()
            onNodeWithText("Hostname *").assertIsDisplayed()
            onNodeWithText("Device Type").assertIsDisplayed()

            onNodeWithText("Cancel").performClick()
            assertTrue(cancelClicked)

            onNodeWithText("Save Device").performClick()
            assertTrue(submitClicked)
        }
}
