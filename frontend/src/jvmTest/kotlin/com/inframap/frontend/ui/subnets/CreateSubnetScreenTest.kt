package com.inframap.frontend.ui.subnets

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runComposeUiTest
import com.inframap.frontend.designsystem.InfraMapTheme
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

            onNodeWithText("New Subnet").assertIsDisplayed()
            onNodeWithText("Subnet Name *").assertIsDisplayed()
            onNodeWithText("CIDR (e.g. 192.168.1.0/24) *").assertIsDisplayed()
            onNodeWithText("VLAN ID (Optional)").assertIsDisplayed()
            onNodeWithText("Enable Automatic Discovery Scanning").assertIsDisplayed()

            onNodeWithText("Cancel").performClick()
            assertTrue(cancelClicked)

            onNodeWithText("Create Subnet").performClick()
            assertTrue(submitClicked)
        }
}
