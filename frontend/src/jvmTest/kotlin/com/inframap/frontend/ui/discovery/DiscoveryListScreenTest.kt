package com.inframap.frontend.ui.discovery

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performMouseInput
import androidx.compose.ui.test.runComposeUiTest
import com.inframap.frontend.designsystem.InfraMapTheme
import com.inframap.frontend.domain.model.CollectorRunDetail
import com.inframap.frontend.domain.model.CollectorRunSummary
import com.inframap.frontend.domain.model.DiscoverySource
import com.inframap.frontend.domain.model.DiscoverySourceDeletionImpact
import com.inframap.frontend.domain.model.DiscoveryTarget
import com.inframap.frontend.domain.model.SourceCollector
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

@OptIn(ExperimentalTestApi::class)
class DiscoveryListScreenTest {
    private companion object {
        const val TOOLTIP_SETTLE_MILLIS = 200L
    }

    private val moreTargetsSuffix =
        SemanticsMatcher("text is a +N suffix") { node ->
            node.config.getOrNull(SemanticsProperties.Text).orEmpty().any {
                it.text.matches(Regex("""\+\d+"""))
            }
        }

    private val noopActions =
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
        )

    @Test
    fun singleTargetShowsItsCidrWithoutMoreSuffix() =
        runComposeUiTest {
            val source =
                DiscoverySource(
                    id = "disc-single",
                    name = "Single Target",
                    targets = listOf(DiscoveryTarget(cidr = "10.0.10.0/24")),
                )

            setContent {
                InfraMapTheme {
                    DiscoveryListScreen(
                        state = DiscoveryListUiState(sources = listOf(source), isLoading = false),
                        actions = noopActions,
                    )
                }
            }

            onNodeWithText("10.0.10.0/24").assertIsDisplayed()
            onNode(moreTargetsSuffix).assertDoesNotExist()
        }

    @Test
    fun multipleTargetsShowFirstWithMoreSuffixAndFullListOnHover() =
        runComposeUiTest {
            mainClock.autoAdvance = false
            val source =
                DiscoverySource(
                    id = "disc-multi-target",
                    name = "Multi Target",
                    targets =
                        listOf(
                            DiscoveryTarget(cidr = "10.0.10.0/24", subnetId = "subnet-1", subnetName = "Servers"),
                            DiscoveryTarget(cidr = "192.0.2.0/24"),
                            DiscoveryTarget(cidr = "198.51.100.0/24"),
                        ),
                )

            setContent {
                InfraMapTheme {
                    DiscoveryListScreen(
                        state = DiscoveryListUiState(sources = listOf(source), isLoading = false),
                        actions = noopActions,
                    )
                }
            }
            mainClock.advanceTimeBy(TOOLTIP_SETTLE_MILLIS)

            onNodeWithText("10.0.10.0/24").assertIsDisplayed()
            onNodeWithText("+2").assertIsDisplayed()
            onNodeWithText("192.0.2.0/24", substring = true).assertDoesNotExist()

            onNodeWithText("10.0.10.0/24").performMouseInput { moveTo(Offset(4f, 4f)) }
            mainClock.advanceTimeBy(TOOLTIP_SETTLE_MILLIS)
            onNodeWithText("10.0.10.0/24\n192.0.2.0/24\n198.51.100.0/24").assertIsDisplayed()
        }

    @Test
    fun sourceWithoutTargetsShowsDash() =
        runComposeUiTest {
            val source =
                DiscoverySource(
                    id = "disc-provider",
                    name = "Provider Only",
                    collectors = listOf(SourceCollector(id = "c1", collectorType = "proxmox", enabled = true)),
                )

            setContent {
                InfraMapTheme {
                    DiscoveryListScreen(
                        state = DiscoveryListUiState(sources = listOf(source), isLoading = false),
                        actions = noopActions,
                    )
                }
            }

            onNodeWithText("Provider Only").assertIsDisplayed()
            onNodeWithText("-").assertIsDisplayed()
        }

    @Test
    fun hoveringStatusListsLastRunRowsWithTheirTargets() =
        runComposeUiTest {
            mainClock.autoAdvance = false
            val source =
                DiscoverySource(
                    id = "disc-partial",
                    name = "Partial Source",
                    targets = listOf(DiscoveryTarget(cidr = "192.0.2.0/24"), DiscoveryTarget(cidr = "198.51.100.0/24")),
                    lastStatus = "partial",
                    lastRun =
                        CollectorRunSummary(
                            status = "partial",
                            collectors =
                                listOf(
                                    CollectorRunDetail(
                                        collectorType = "icmp_sweep",
                                        status = "success",
                                        targetCidr = "192.0.2.0/24",
                                    ),
                                    CollectorRunDetail(
                                        collectorType = "icmp_sweep",
                                        status = "error",
                                        errorMessage = "network unreachable",
                                        targetCidr = "198.51.100.0/24",
                                    ),
                                    CollectorRunDetail(collectorType = "mdns", status = "success"),
                                ),
                        ),
                )

            setContent {
                InfraMapTheme {
                    DiscoveryListScreen(
                        state = DiscoveryListUiState(sources = listOf(source), isLoading = false),
                        actions = noopActions,
                    )
                }
            }
            mainClock.advanceTimeBy(TOOLTIP_SETTLE_MILLIS)

            onNodeWithText("network unreachable").assertDoesNotExist()

            onNodeWithText("Partial").performMouseInput { moveTo(Offset(4f, 4f)) }
            mainClock.advanceTimeBy(TOOLTIP_SETTLE_MILLIS)
            onNodeWithText("network unreachable").assertIsDisplayed()
            onNodeWithText("198.51.100.0/24").assertIsDisplayed()
            onNodeWithText("mDNS / Bonjour").assertIsDisplayed()
        }

    @Test
    fun runRowShowsTargetCidrWhenPresent() =
        runComposeUiTest {
            setContent {
                InfraMapTheme {
                    DiscoveryRunRow(
                        detail =
                            CollectorRunDetail(
                                collectorType = "icmp_sweep",
                                status = "error",
                                errorMessage = "timeout",
                                targetCidr = "10.0.20.0/24",
                            ),
                    )
                }
            }

            onNodeWithText("ICMP Ping").assertIsDisplayed()
            onNodeWithText("10.0.20.0/24").assertIsDisplayed()
            onNodeWithText("timeout").assertIsDisplayed()
        }

    @Test
    fun runRowWithoutTargetCidrShowsOnlyTheCollector() =
        runComposeUiTest {
            setContent {
                InfraMapTheme {
                    DiscoveryRunRow(
                        detail = CollectorRunDetail(collectorType = "arp_sweep", status = "skipped"),
                    )
                }
            }

            onNodeWithText("ARP Sweep").assertIsDisplayed()
            onNodeWithText("/", substring = true).assertDoesNotExist()
        }

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
                    collectors = listOf(SourceCollector(id = "c1", collectorType = "icmp_sweep", enabled = true)),
                    targets = listOf(DiscoveryTarget(cidr = "192.168.1.0/24")),
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

            onNodeWithContentDescription("More options").performClick()
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
                    targets = listOf(DiscoveryTarget(cidr = "10.0.0.0/16")),
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

    @Test
    fun rendersEmptyCollectorsAsDash() =
        runComposeUiTest {
            val source =
                DiscoverySource(
                    id = "disc-empty-col",
                    name = "Empty Collectors Plan",
                    targets = listOf(DiscoveryTarget(cidr = "10.1.0.0/24")),
                    collectors = emptyList(),
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

            onNodeWithText("Empty Collectors Plan").assertIsDisplayed()
            onNodeWithText("10.1.0.0/24").assertIsDisplayed()
            onNodeWithText("-").assertIsDisplayed()
        }

    @Test
    fun rendersDeletionImpactDialogWhenSourceToDeleteIsSet() =
        runComposeUiTest {
            val source =
                DiscoverySource(
                    id = "disc-1",
                    name = "LAN Ping Scan",
                    sourceType = "icmp_sweep",
                )
            var confirmed = false

            setContent {
                InfraMapTheme {
                    DiscoveryListScreen(
                        state =
                            DiscoveryListUiState(
                                sources = listOf(source),
                                isLoading = false,
                                sourceToDelete = source,
                                deleteImpact =
                                    DiscoverySourceDeletionImpact(
                                        collectorsHalted = 3,
                                        devicesUnlinked = 7,
                                    ),
                            ),
                        actions =
                            DiscoveryListActions(
                                onCreateSourceClicked = {},
                                onTriggerRunClicked = {},
                                onDeleteSourceClicked = {},
                                onConfirmDelete = { confirmed = true },
                                onCancelDelete = {},
                                onRetryClicked = {},
                                onDismissToast = {},
                                onDismissDeleteError = {},
                                onDismissTriggerRunError = {},
                            ),
                    )
                }
            }

            onNodeWithText("Delete Discovery Source").assertIsDisplayed()
            onNodeWithText("3 collector(s) to deactivate").assertIsDisplayed()
            onNodeWithText("7 associated device(s)").assertIsDisplayed()

            onNodeWithText("Confirm Deletion").performClick()
            assertTrue(confirmed)
        }
}
