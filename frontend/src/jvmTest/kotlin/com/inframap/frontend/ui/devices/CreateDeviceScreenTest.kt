package com.inframap.frontend.ui.devices

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runComposeUiTest
import com.inframap.frontend.designsystem.InfraMapTheme
import com.inframap.frontend.generated.resources.Res
import com.inframap.frontend.generated.resources.create_device_cancel_button
import com.inframap.frontend.generated.resources.create_device_form_title
import com.inframap.frontend.generated.resources.create_device_header_title
import com.inframap.frontend.generated.resources.create_device_hostname_label
import com.inframap.frontend.generated.resources.create_device_submit_button
import com.inframap.frontend.generated.resources.create_device_type_label
import kotlinx.coroutines.runBlocking
import org.jetbrains.compose.resources.getString
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

            onNodeWithText(runBlocking { getString(Res.string.create_device_header_title) }).assertIsDisplayed()
            onNodeWithText(runBlocking { getString(Res.string.create_device_form_title) }).assertIsDisplayed()
            onNodeWithText(runBlocking { getString(Res.string.create_device_hostname_label) }).assertIsDisplayed()
            onNodeWithText(runBlocking { getString(Res.string.create_device_type_label) }).assertIsDisplayed()

            onNodeWithText(runBlocking { getString(Res.string.create_device_cancel_button) }).performClick()
            assertTrue(cancelClicked)

            onNodeWithText(runBlocking { getString(Res.string.create_device_submit_button) }).performClick()
            assertTrue(submitClicked)
        }
}
