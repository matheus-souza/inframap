package com.inframap.frontend.designsystem

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runComposeUiTest
import com.inframap.frontend.domain.model.Subnet
import com.inframap.frontend.domain.model.SubnetSummary
import com.inframap.frontend.domain.model.toSummary
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

@OptIn(ExperimentalTestApi::class)
class SubnetSuggestionChipsTest {
    private val sampleSubnets =
        listOf(
            SubnetSummary(
                id = "1",
                name = "Production LAN",
                cidr = "10.0.0.0/16",
                discoveryEnabled = true,
            ),
            SubnetSummary(
                id = "2",
                name = "DMZ Network",
                cidr = "192.168.1.0/24",
                discoveryEnabled = false,
            ),
        )

    @Test
    fun rendersTitleAndSubnetChips() =
        runComposeUiTest {
            setContent {
                InfraMapTheme {
                    SubnetSuggestionChips(
                        subnets = sampleSubnets,
                        onSubnetSelected = {},
                    )
                }
            }

            onNodeWithText("Sub-redes cadastradas").assertIsDisplayed()
            onNodeWithText("Production LAN").assertIsDisplayed()
            onNodeWithText("10.0.0.0/16").assertIsDisplayed()
            onNodeWithText("DMZ Network").assertIsDisplayed()
            onNodeWithText("192.168.1.0/24").assertIsDisplayed()
        }

    @Test
    fun clickingChipTriggersOnSubnetSelected() =
        runComposeUiTest {
            var selectedSubnet: SubnetSummary? = null
            setContent {
                InfraMapTheme {
                    SubnetSuggestionChips(
                        subnets = sampleSubnets,
                        onSubnetSelected = { selectedSubnet = it },
                    )
                }
            }

            onNodeWithText("Production LAN").performClick()
            assertEquals(sampleSubnets[0], selectedSubnet)

            onNodeWithText("192.168.1.0/24").performClick()
            assertEquals(sampleSubnets[1], selectedSubnet)
        }

    @Test
    fun rendersSelectedChipWhenSelectedCidrMatches() =
        runComposeUiTest {
            var selectedSubnet: SubnetSummary? = null
            setContent {
                InfraMapTheme {
                    SubnetSuggestionChips(
                        subnets = sampleSubnets,
                        selectedCidr = "10.0.0.0/16",
                        onSubnetSelected = { selectedSubnet = it },
                    )
                }
            }

            onNodeWithText("Production LAN").assertIsDisplayed()
            onNodeWithText("10.0.0.0/16").assertIsDisplayed()
            onNodeWithText("DMZ Network").assertIsDisplayed()

            onNodeWithText("DMZ Network").performClick()
            assertEquals(sampleSubnets[1], selectedSubnet)
        }

    @Test
    fun rendersShimmerWhenLoading() =
        runComposeUiTest {
            setContent {
                InfraMapTheme {
                    SubnetSuggestionChips(
                        subnets = sampleSubnets,
                        isLoading = true,
                        onSubnetSelected = {},
                    )
                }
            }

            onNodeWithText("Sub-redes cadastradas").assertIsDisplayed()
            // When loading, chips are not rendered
            onNodeWithText("Production LAN").assertDoesNotExist()
            onNodeWithText("DMZ Network").assertDoesNotExist()
        }

    @Test
    fun rendersHelperTipWhenEmptyAndNotLoading() =
        runComposeUiTest {
            setContent {
                InfraMapTheme {
                    SubnetSuggestionChips(
                        subnets = emptyList(),
                        isLoading = false,
                        onSubnetSelected = {},
                    )
                }
            }

            onNodeWithText("Sub-redes cadastradas").assertIsDisplayed()
            onNodeWithText("Nenhuma sub-rede cadastrada para sugestão.").assertIsDisplayed()
        }

    @Test
    fun subnetToSummaryExtensionMapsCorrectly() {
        val subnet =
            Subnet(
                id = "sub-100",
                name = "Management VLAN",
                cidr = "172.16.0.0/24",
                vlanId = 100,
                gatewayIp = "172.16.0.1",
                description = "VLAN for switch management",
                discoveryEnabled = true,
                createdAt = "2026-08-28T10:00:00Z",
            )

        val summary = subnet.toSummary()

        assertEquals("sub-100", summary.id)
        assertEquals("Management VLAN", summary.name)
        assertEquals("172.16.0.0/24", summary.cidr)
        assertTrue(summary.discoveryEnabled)
    }

    @Test
    fun titleFoldsTheChipsAway() =
        runComposeUiTest {
            setContent {
                InfraMapTheme {
                    SubnetSuggestionChips(
                        subnets = sampleSubnets,
                        onSubnetSelected = {},
                    )
                }
            }

            // Same fold as the detected-interface block on the subnet screen: both offer a
            // value to fill the field below, so both are the same component.
            onNodeWithText("Production LAN").assertIsDisplayed()

            onNodeWithText("Sub-redes cadastradas").performClick()
            waitForIdle()
            onNodeWithText("Production LAN").assertDoesNotExist()

            onNodeWithText("Sub-redes cadastradas").performClick()
            waitForIdle()
            onNodeWithText("Production LAN").assertIsDisplayed()
        }
}
