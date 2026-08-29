package com.inframap.frontend.ui.discovery

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runComposeUiTest
import com.inframap.frontend.designsystem.InfraMapTheme
import com.inframap.frontend.domain.model.DiscoverySource
import com.inframap.frontend.domain.model.SourceCollector
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

@OptIn(ExperimentalTestApi::class)
class DiscoveryListScreenTest {
    @Test
    fun rendersEmptyStateWhenNoSources() =
        runComposeUiTest {
            setContent {
                InfraMapTheme {
                    DiscoveryListScreen(
                        state = DiscoveryListUiState(sources = emptyList(), isLoading = false),
                        actions =
                            DiscoveryListActions(
                                onCreateSourceClicked = {},
                                onTriggerRunClicked = {},
                                onDeleteSourceClicked = {},
                                onConfirmDelete = {},
                                onCancelDelete = {},
                                onRetryClicked = {},
                                onDismissToast = {},
                                onDismissDeleteError = {},
                                onDismissTriggerRunError = {},
                            ),
                    )
                }
            }

            onNodeWithText("Discovery Sources").assertIsDisplayed()
            onNodeWithText("+ New Source").assertIsDisplayed()
            onNodeWithText("No discovery sources configured").assertIsDisplayed()
        }

    @Test
    fun rendersSourcesTableAndTriggersRun() =
        runComposeUiTest {
            val source =
                DiscoverySource(
                    id = "disc-1",
                    name = "LAN Ping Scan",
                    sourceType = "icmp_sweep",
                    configCidr = "192.168.1.0/24",
                    scheduleCron = "*/15 * * * *",
                    lastStatus = "idle",
                )
            var triggeredId: String? = null
            var deletedSource: DiscoverySource? = null

            setContent {
                InfraMapTheme {
                    DiscoveryListScreen(
                        state = DiscoveryListUiState(sources = listOf(source), isLoading = false),
                        actions =
                            DiscoveryListActions(
                                onCreateSourceClicked = {},
                                onTriggerRunClicked = { triggeredId = it },
                                onDeleteSourceClicked = { deletedSource = it },
                                onConfirmDelete = {},
                                onCancelDelete = {},
                                onRetryClicked = {},
                                onDismissToast = {},
                                onDismissDeleteError = {},
                                onDismissTriggerRunError = {},
                            ),
                    )
                }
            }

            onNodeWithText("LAN Ping Scan").assertIsDisplayed()
            onNodeWithText("192.168.1.0/24").assertIsDisplayed()
            onNodeWithText("ICMP Ping").assertIsDisplayed()

            onNodeWithText("Run").performClick()
            assertEquals("disc-1", triggeredId)

            onNodeWithText("Delete").performClick()
            assertEquals("disc-1", deletedSource?.id)
        }

    @Test
    fun rendersMultiCollectorBadgesAndPartialStatus() =
        runComposeUiTest {
            val source =
                DiscoverySource(
                    id = "disc-multi",
                    name = "Multi-Collector Plan",
                    configCidr = "10.0.0.0/16",
                    collectors =
                        listOf(
                            SourceCollector(id = "c1", collectorType = "icmp_sweep", enabled = true),
                            SourceCollector(id = "c2", collectorType = "arp_sweep", enabled = true),
                            SourceCollector(id = "c3", collectorType = "snmp", enabled = true),
                            SourceCollector(id = "c4", collectorType = "reverse_dns", enabled = true),
                        ),
                    lastStatus = "partial",
                )

            setContent {
                InfraMapTheme {
                    DiscoveryListScreen(
                        state = DiscoveryListUiState(sources = listOf(source), isLoading = false),
                        actions =
                            DiscoveryListActions(
                                onCreateSourceClicked = {},
                                onTriggerRunClicked = {},
                                onDeleteSourceClicked = {},
                                onConfirmDelete = {},
                                onCancelDelete = {},
                                onRetryClicked = {},
                                onDismissToast = {},
                                onDismissDeleteError = {},
                                onDismissTriggerRunError = {},
                            ),
                    )
                }
            }

            onNodeWithText("Multi-Collector Plan").assertIsDisplayed()
            onNodeWithText("10.0.0.0/16").assertIsDisplayed()
            onNodeWithText("ICMP Ping").assertIsDisplayed()
            onNodeWithText("ARP Sweep").assertIsDisplayed()
            onNodeWithText("SNMP").assertIsDisplayed()
            onNodeWithText("Reverse DNS").assertIsDisplayed()
            onNodeWithText("Partial").assertIsDisplayed()
        }
}
