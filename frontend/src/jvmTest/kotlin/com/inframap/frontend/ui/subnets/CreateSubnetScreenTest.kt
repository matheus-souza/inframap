package com.inframap.frontend.ui.subnets

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runComposeUiTest
import com.inframap.frontend.designsystem.InfraMapTheme
import com.inframap.frontend.generated.resources.Res
import com.inframap.frontend.generated.resources.create_device_cancel_button
import com.inframap.frontend.generated.resources.create_subnet_cidr_label
import com.inframap.frontend.generated.resources.create_subnet_discovery_toggle
import com.inframap.frontend.generated.resources.create_subnet_header_title
import com.inframap.frontend.generated.resources.create_subnet_name_label
import com.inframap.frontend.generated.resources.create_subnet_submit
import com.inframap.frontend.generated.resources.create_subnet_vlan_label
import kotlinx.coroutines.runBlocking
import org.jetbrains.compose.resources.getString
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

@OptIn(ExperimentalTestApi::class)
class CreateSubnetScreenTest {
    @Test
    fun rendersFormFieldsAndButtons() =
        runComposeUiTest {
            var submitClicked = false
            var cancelClicked = false

            setContent {
                InfraMapTheme {
                    CreateSubnetScreen(
                        state = CreateSubnetUiState(),
                        actions =
                            CreateSubnetActions(
                                onNameChanged = {},
                                onCidrChanged = {},
                                onVlanIdChanged = {},
                                onGatewayIpChanged = {},
                                onDescriptionChanged = {},
                                onDiscoveryEnabledChanged = {},
                                onToggleSuggestions = {},
                                onInterfaceSelected = {},
                                onSubmitClicked = { submitClicked = true },
                                onCancelClicked = { cancelClicked = true },
                            ),
                    )
                }
            }

            onNodeWithText(runBlocking { getString(Res.string.create_subnet_header_title) }).assertIsDisplayed()
            onNodeWithText(runBlocking { getString(Res.string.create_subnet_name_label) }).assertIsDisplayed()
            onNodeWithText(runBlocking { getString(Res.string.create_subnet_cidr_label) }).assertIsDisplayed()
            onNodeWithText(runBlocking { getString(Res.string.create_subnet_vlan_label) }).assertIsDisplayed()
            onNodeWithText(runBlocking { getString(Res.string.create_subnet_discovery_toggle) }).assertIsDisplayed()

            onNodeWithText(runBlocking { getString(Res.string.create_device_cancel_button) }).performClick()
            assertTrue(cancelClicked)

            onNodeWithText(runBlocking { getString(Res.string.create_subnet_submit) }).performClick()
            assertTrue(submitClicked)
        }
}
