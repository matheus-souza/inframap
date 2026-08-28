package com.inframap.frontend.ui.devices

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runComposeUiTest
import com.inframap.frontend.designsystem.InfraMapTheme
import com.inframap.frontend.generated.resources.Res
import com.inframap.frontend.generated.resources.create_device_cancel_button
import com.inframap.frontend.generated.resources.edit_device_form_title
import com.inframap.frontend.generated.resources.edit_device_header_title
import com.inframap.frontend.generated.resources.edit_device_status_label
import com.inframap.frontend.generated.resources.edit_device_submit_button
import kotlinx.coroutines.runBlocking
import org.jetbrains.compose.resources.getString
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

            onNodeWithText(runBlocking { getString(Res.string.edit_device_header_title) }).assertIsDisplayed()
            onNodeWithText(runBlocking { getString(Res.string.edit_device_form_title) }).assertIsDisplayed()
            onNodeWithText(runBlocking { getString(Res.string.edit_device_status_label) }).assertIsDisplayed()

            onNodeWithText(runBlocking { getString(Res.string.create_device_cancel_button) }).performClick()
            assertTrue(cancelClicked)

            onNodeWithText(runBlocking { getString(Res.string.edit_device_submit_button) }).performClick()
            assertTrue(submitClicked)
        }
}
