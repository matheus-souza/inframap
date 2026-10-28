package com.inframap.frontend.ui.subnets

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.runComposeUiTest
import com.inframap.frontend.data.dto.SubnetDeletionImpact
import com.inframap.frontend.data.dto.SubnetDiscoverySource
import com.inframap.frontend.designsystem.InfraMapTheme
import com.inframap.frontend.domain.model.Subnet
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

            onNodeWithText("Subnets").assertIsDisplayed()
            onNodeWithText("+ New Subnet").assertIsDisplayed()
            onNodeWithText("No subnets registered").assertIsDisplayed()
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

    private val subnetToDelete = Subnet(id = "sub-1", name = "Lab", cidr = "192.0.2.0/24")

    @Test
    fun deleteDialogListsTheDiscoverySourcesThatScanTheSubnet() =
        runComposeUiTest {
            val impact =
                SubnetDeletionImpact(
                    affectedDevices = 2,
                    unlinkedTopologyEdges = 1,
                    discoverySources =
                        listOf(
                            SubnetDiscoverySource(id = "src-1", name = "Local network sweep"),
                            SubnetDiscoverySource(id = "src-2", name = "Lab SNMP"),
                        ),
                )

            setContent {
                InfraMapTheme {
                    SubnetsScreen(
                        state =
                            SubnetsUiState(
                                subnets = listOf(subnetToDelete),
                                isLoading = false,
                                subnetToDelete = subnetToDelete,
                                deleteImpact = impact,
                            ),
                        actions = testActions(),
                    )
                }
            }

            onNodeWithText("2 contained device(s)").assertIsDisplayed()
            onNodeWithText(
                "2 discovery source(s) scanning this subnet: Local network sweep, Lab SNMP. " +
                    "The sources are kept and this target will be skipped on their next runs.",
            ).assertIsDisplayed()
        }

    @Test
    fun deleteDialogHidesTheSourcesLineWhenNoSourceScansTheSubnet() =
        runComposeUiTest {
            setContent {
                InfraMapTheme {
                    SubnetsScreen(
                        state =
                            SubnetsUiState(
                                subnets = listOf(subnetToDelete),
                                isLoading = false,
                                subnetToDelete = subnetToDelete,
                                deleteImpact = SubnetDeletionImpact(affectedDevices = 2, unlinkedTopologyEdges = 1),
                            ),
                        actions = testActions(),
                    )
                }
            }

            onNodeWithText("1 linked topology edge(s)").assertIsDisplayed()
            onNodeWithText("discovery source(s) scanning this subnet", substring = true).assertDoesNotExist()
        }
}
