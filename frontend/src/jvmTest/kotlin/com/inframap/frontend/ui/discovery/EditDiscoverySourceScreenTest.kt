package com.inframap.frontend.ui.discovery

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.runComposeUiTest
import com.inframap.frontend.designsystem.InfraMapTheme
import com.inframap.frontend.domain.model.DiscoveryTarget
import com.inframap.frontend.domain.model.SubnetSummary
import com.inframap.frontend.generated.resources.Res
import com.inframap.frontend.generated.resources.edit_discovery_source_error_load
import com.inframap.frontend.generated.resources.edit_discovery_source_error_update
import com.inframap.frontend.generated.resources.provider_validation_field_required
import com.inframap.frontend.ui.util.UiText
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

@OptIn(ExperimentalTestApi::class)
class EditDiscoverySourceScreenTest {
    private fun actions(
        targets: DiscoveryTargetsEditorActions = DiscoveryTargetsEditorActions(),
        onRetryLoad: () -> Unit = {},
        onCollectorsChanged: (Set<String>) -> Unit = {},
    ) = EditDiscoverySourceActions(
        onNameChanged = {},
        onScheduleCronChanged = {},
        onEnabledChanged = {},
        onCollectorsChanged = onCollectorsChanged,
        onProviderFieldChanged = { _, _, _ -> },
        onProviderTabSelected = {},
        targets = targets,
        onSubmitClicked = {},
        onDeleteClicked = {},
        onConfirmDeleteClicked = {},
        onDismissDeleteDialog = {},
        onRetryLoad = onRetryLoad,
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

    @Test
    fun refusedSaveIsShownInlineWithoutTheLoadRetry() =
        runComposeUiTest {
            setContent {
                InfraMapTheme {
                    EditDiscoverySourceScreen(
                        state =
                            sweepState.copy(
                                submitError = UiText.Resource(Res.string.edit_discovery_source_error_update),
                                validationErrors =
                                    mapOf(TARGETS_ERROR_KEY to TargetRuleViolation.SubnetNotFound.message),
                            ),
                        actions = actions(),
                    )
                }
            }

            onNodeWithTag("edit_source_submit_error").performScrollTo().assertIsDisplayed()
            onNodeWithText("Failed to update discovery source.").assertIsDisplayed()
            onNodeWithTag("edit_source_name_input").assertExists()
            onNodeWithTag("edit_source_load_error").assertDoesNotExist()
            onNodeWithText("Try Again").assertDoesNotExist()
        }

    @Test
    fun loadFailureOffersRetryInsteadOfTheForm() =
        runComposeUiTest {
            var retried = 0
            setContent {
                InfraMapTheme {
                    EditDiscoverySourceScreen(
                        state =
                            EditDiscoverySourceUiState(
                                id = "src-lan",
                                isLoading = false,
                                loadError = UiText.Resource(Res.string.edit_discovery_source_error_load),
                            ),
                        actions = actions(onRetryLoad = { retried++ }),
                    )
                }
            }

            onNodeWithText("Failed to load discovery source.").assertIsDisplayed()
            onNodeWithTag("edit_source_name_input").assertDoesNotExist()
            onNodeWithTag("edit_source_submit_error").assertDoesNotExist()
            onNodeWithTag("edit_source_load_retry").performClick()
            assertEquals(1, retried)
        }

    @Test
    fun collectorChipsShowThePlanAndChangeIt() =
        runComposeUiTest {
            var collectors by mutableStateOf(setOf("icmp_sweep", "proxmox"))
            setContent {
                InfraMapTheme {
                    EditDiscoverySourceScreen(
                        state =
                            sweepState.copy(
                                selectedCollectors = collectors,
                                initialCollectors = setOf("icmp_sweep", "proxmox"),
                                configuredSecrets = mapOf("proxmox" to setOf("token_secret")),
                            ),
                        actions = actions(onCollectorsChanged = { collectors = it }),
                    )
                }
            }

            onNodeWithText("Discovery Collectors", substring = true).performScrollTo().assertIsDisplayed()
            onNodeWithText("ICMP Ping").performScrollTo().assertIsSelected()
            onNodeWithText("ARP Sweep").performScrollTo().assertIsNotSelected()
            onNodeWithText("Proxmox VE").performScrollTo().assertIsSelected()
            onNodeWithTag("discovery_targets_editor").assertExists()

            // A plan left with providers only hides the targets editor.
            onNodeWithText("ICMP Ping").performScrollTo().performClick()
            assertEquals(setOf("proxmox"), collectors)
            onNodeWithTag("discovery_targets_editor").assertDoesNotExist()
            onNodeWithText("Leave blank to preserve current credential").performScrollTo().assertIsDisplayed()

            onNodeWithText("Docker").performScrollTo().performClick()
            assertEquals(setOf("proxmox", "docker"), collectors)
            onNodeWithTag("edit_provider_tab_docker").performScrollTo().assertIsDisplayed()
        }

    @Test
    fun anAddedProviderShowsItsFieldsAsRequired() =
        runComposeUiTest {
            setContent {
                InfraMapTheme {
                    EditDiscoverySourceScreen(
                        state =
                            sweepState.copy(
                                selectedCollectors = setOf("icmp_sweep", "proxmox"),
                                initialCollectors = setOf("icmp_sweep"),
                                validationErrors =
                                    mapOf(
                                        ProviderForms.labelKey("proxmox") to
                                            UiText.Resource(Res.string.provider_validation_field_required),
                                    ),
                            ),
                        actions = actions(),
                    )
                }
            }

            onNodeWithTag("edit_provider_field_proxmox_token_secret").performScrollTo().assertIsDisplayed()
            onNodeWithText("Leave blank to preserve current credential").assertDoesNotExist()
            onNodeWithTag("edit_provider_error_proxmox").performScrollTo().assertIsDisplayed()
            onNodeWithText("Fill in required provider fields.").assertIsDisplayed()
            onNodeWithTag("test_connection_unsaved_hint_proxmox").performScrollTo().assertIsDisplayed()
        }

    @Test
    fun anEmptyPlanShowsTheCollectorError() =
        runComposeUiTest {
            setContent {
                InfraMapTheme {
                    EditDiscoverySourceScreen(
                        state =
                            sweepState.copy(
                                selectedCollectors = emptySet(),
                                validationErrors =
                                    mapOf(COLLECTORS_ERROR_KEY to checkNotNull(DiscoveryCollectorRules.selectionError(emptySet()))),
                            ),
                        actions = actions(),
                    )
                }
            }

            onNodeWithText("Select at least one discovery collector.").performScrollTo().assertIsDisplayed()
        }
}
