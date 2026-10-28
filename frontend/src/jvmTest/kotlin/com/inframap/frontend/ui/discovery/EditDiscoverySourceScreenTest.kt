package com.inframap.frontend.ui.discovery

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.runComposeUiTest
import com.inframap.frontend.designsystem.InfraMapTheme
import com.inframap.frontend.domain.model.DiscoveryTarget
import com.inframap.frontend.domain.model.SubnetSummary
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

@OptIn(ExperimentalTestApi::class)
class EditDiscoverySourceScreenTest {
    private fun actions(targets: DiscoveryTargetsEditorActions = DiscoveryTargetsEditorActions()) =
        EditDiscoverySourceActions(
            onNameChanged = {},
            onScheduleCronChanged = {},
            onEnabledChanged = {},
            onProviderFieldChanged = { _, _, _ -> },
            onProviderTabSelected = {},
            targets = targets,
            onSubmitClicked = {},
            onDeleteClicked = {},
            onConfirmDeleteClicked = {},
            onDismissDeleteDialog = {},
            onDismissError = {},
            onBackClicked = {},
        )

    private val sweepState =
        EditDiscoverySourceUiState(
            id = "src-lan",
            name = "LAN sweep",
            isLoading = false,
            selectedCollectors = setOf("icmp_sweep"),
            targets =
                listOf(
                    DiscoveryTarget("10.0.10.0/24", subnetId = "sub-old", subnetName = "Old", subnetDeleted = true),
                    DiscoveryTarget("192.0.2.0/24"),
                ),
            subnets = listOf(SubnetSummary(id = "sub-20", name = "Lab", cidr = "198.51.100.0/24")),
        )

    @Test
    fun targetOfADeletedSubnetIsFlaggedAndRemovable() =
        runComposeUiTest {
            var removed = -1
            setContent {
                InfraMapTheme {
                    EditDiscoverySourceScreen(
                        state = sweepState,
                        actions = actions(DiscoveryTargetsEditorActions(onRemoveTarget = { removed = it })),
                    )
                }
            }

            onNodeWithTag("discovery_target_subnet_deleted_0")
                .performScrollTo()
                .assertIsDisplayed()
            onNodeWithText("Subnet deleted — remove this target").assertIsDisplayed()
            onNodeWithTag("discovery_target_subnet_deleted_1").assertDoesNotExist()

            onNodeWithTag("discovery_target_remove_0").performScrollTo().performClick()
            assertEquals(0, removed)
        }

    @Test
    fun subnetChipLinksATarget() =
        runComposeUiTest {
            var toggled: SubnetSummary? = null
            setContent {
                InfraMapTheme {
                    EditDiscoverySourceScreen(
                        state = sweepState,
                        actions = actions(DiscoveryTargetsEditorActions(onSubnetToggled = { toggled = it })),
                    )
                }
            }

            onNodeWithTag("discovery_target_subnet_sub-20").performScrollTo().performClick()
            assertEquals("sub-20", toggled?.id)
        }

    @Test
    fun providerOnlySourceShowsNoTargetsEditor() =
        runComposeUiTest {
            setContent {
                InfraMapTheme {
                    EditDiscoverySourceScreen(
                        state = sweepState.copy(selectedCollectors = setOf("proxmox")),
                        actions = actions(),
                    )
                }
            }

            onNodeWithTag("discovery_targets_editor").assertDoesNotExist()
        }
}
