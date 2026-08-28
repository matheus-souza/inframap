package com.inframap.frontend.ui.subnets

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.runComposeUiTest
import com.inframap.frontend.designsystem.InfraMapTheme
import com.inframap.frontend.domain.model.Subnet
import com.inframap.frontend.generated.resources.Res
import com.inframap.frontend.generated.resources.subnets_empty_title
import com.inframap.frontend.generated.resources.subnets_new_button
import com.inframap.frontend.generated.resources.subnets_title
import kotlinx.coroutines.runBlocking
import org.jetbrains.compose.resources.getString
import org.junit.jupiter.api.Test

@OptIn(ExperimentalTestApi::class)
class SubnetsScreenTest {
    private fun testActions() =
        SubnetsActions(
            onCreateSubnetClicked = {},
            onAddInterfaceClicked = {},
            onRetryClicked = {},
            onDismissToast = {},
        )

    @Test
    fun rendersEmptyStateWhenNoSubnets() =
        runComposeUiTest {
            setContent {
                InfraMapTheme {
                    SubnetsScreen(
                        state = SubnetsUiState(subnets = emptyList(), isLoading = false),
                        actions = testActions(),
                    )
                }
            }

            onNodeWithText(runBlocking { getString(Res.string.subnets_title) }).assertIsDisplayed()
            onNodeWithText(runBlocking { getString(Res.string.subnets_new_button) }).assertIsDisplayed()
            onNodeWithText(runBlocking { getString(Res.string.subnets_empty_title) }).assertIsDisplayed()
        }

    @Test
    fun rendersSubnetsTable() =
        runComposeUiTest {
            val subnet =
                Subnet(
                    id = "sub-1",
                    name = "VLAN 10 - Servers",
                    cidr = "10.10.10.0/24",
                    vlanId = 10,
                    gatewayIp = "10.10.10.1",
                    discoveryEnabled = true,
                    description = "Core production servers",
                )

            setContent {
                InfraMapTheme {
                    SubnetsScreen(
                        state = SubnetsUiState(subnets = listOf(subnet), isLoading = false),
                        actions = testActions(),
                    )
                }
            }

            onNodeWithText("VLAN 10 - Servers").assertIsDisplayed()
            onNodeWithText("10.10.10.0/24").assertIsDisplayed()
            onNodeWithText("10.10.10.1").assertIsDisplayed()
            onNodeWithText("Core production servers").assertIsDisplayed()
        }
}
