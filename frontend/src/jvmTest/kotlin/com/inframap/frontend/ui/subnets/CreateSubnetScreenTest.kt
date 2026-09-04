package com.inframap.frontend.ui.subnets

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runComposeUiTest
import com.inframap.frontend.designsystem.InfraMapTheme
import com.inframap.frontend.domain.model.NetworkInterface
import org.junit.jupiter.api.Assertions.assertFalse
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

    @Test
    fun detectedInterfacePanelOpensExpandedAndFolds() =
        runComposeUiTest {
            val iface =
                NetworkInterface(
                    name = "eth0",
                    cidr = "172.25.0.0/16",
                    ip = "172.25.0.3",
                    mac = "d6:b2:d6:b4:fd:15",
                    gateway = "172.25.0.1",
                )
            // The screen takes expansion from state, so the test has to own that state and
            // feed the toggle back in. Asserting only that the callback fired would leave a
            // test named "...AndFolds" that passes with a panel that never folds.
            var expanded by mutableStateOf(true)

            setContent {
                InfraMapTheme {
                    CreateSubnetScreen(
                        state =
                            CreateSubnetUiState(
                                detectedInterfaces = listOf(iface),
                                showInterfaceSuggestions = expanded,
                            ),
                        actions = noopActions(onToggleSuggestions = { expanded = !expanded }),
                    )
                }
            }

            // Open on arrival: the panel exists to offer a value, and one that opens closed
            // is a button nobody presses. Same rule as the subnet suggestion block.
            onNodeWithText("eth0 — 172.25.0.0/16").assertIsDisplayed()

            onNodeWithText("Fill from detected interface").performClick()
            waitForIdle()
            assertFalse(expanded)
            onNodeWithText("eth0 — 172.25.0.0/16").assertDoesNotExist()

            onNodeWithText("Fill from detected interface").performClick()
            waitForIdle()
            onNodeWithText("eth0 — 172.25.0.0/16").assertIsDisplayed()
        }

    private fun noopActions(onToggleSuggestions: () -> Unit = {}) =
        CreateSubnetActions(
            onNameChanged = {},
            onCidrChanged = {},
            onVlanIdChanged = {},
            onGatewayIpChanged = {},
            onDescriptionChanged = {},
            onDiscoveryEnabledChanged = {},
            onToggleSuggestions = onToggleSuggestions,
            onInterfaceSelected = {},
            onSubmitClicked = {},
            onCancelClicked = {},
        )
}
