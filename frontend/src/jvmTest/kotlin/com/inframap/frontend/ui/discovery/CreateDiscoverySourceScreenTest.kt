package com.inframap.frontend.ui.discovery

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.runComposeUiTest
import com.inframap.frontend.designsystem.InfraMapTheme
import com.inframap.frontend.domain.model.SubnetSummary
import com.inframap.frontend.ui.util.UiText
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

@OptIn(ExperimentalTestApi::class)
class CreateDiscoverySourceScreenTest {
    private fun defaultActions(
        onNameChanged: (String) -> Unit = {},
        onScheduleCronChanged: (String) -> Unit = {},
        onConfigCidrChanged: (String) -> Unit = {},
        onEnabledChanged: (Boolean) -> Unit = {},
        onSubnetSelected: (SubnetSummary) -> Unit = {},
        onCollectorsChanged: (Set<String>) -> Unit = {},
        onSubmitClicked: () -> Unit = {},
        onCancelClicked: () -> Unit = {},
    ) = CreateDiscoverySourceActions(
        onNameChanged = onNameChanged,
        onScheduleCronChanged = onScheduleCronChanged,
        onConfigCidrChanged = onConfigCidrChanged,
        onEnabledChanged = onEnabledChanged,
        onSubnetSelected = onSubnetSelected,
        onCollectorsChanged = onCollectorsChanged,
        onSubmitClicked = onSubmitClicked,
        onCancelClicked = onCancelClicked,
    )

    @Test
    fun rendersHeaderAndMainFormFields() =
        runComposeUiTest {
            setContent {
                InfraMapTheme {
                    CreateDiscoverySourceScreen(
                        state = CreateDiscoverySourceUiState(),
                        actions = defaultActions(),
                    )
                }
            }

            onNodeWithText("New Discovery Source").assertIsDisplayed()
            onNodeWithText("Source Name *").assertIsDisplayed()
            onNodeWithText("Discovery Collectors *").assertIsDisplayed()
            onNodeWithText("Network Sweep").assertIsDisplayed()
            onNodeWithText("Infrastructure Providers").assertIsDisplayed()
            onNodeWithText("Target CIDR (e.g. 192.168.1.0/24) *").performScrollTo().assertIsDisplayed()
            onNodeWithText("Schedule").performScrollTo().assertIsDisplayed()
            onNodeWithText("Source enabled").performScrollTo().assertIsDisplayed()
            onNodeWithText("Create Source").performScrollTo().assertIsDisplayed()
            onNodeWithText("Cancel").performScrollTo().assertIsDisplayed()
        }

    @Test
    fun rendersAllCollectorChipsAndDefaultSelection() =
        runComposeUiTest {
            var selectedCollectors by mutableStateOf(setOf("icmp_sweep", "arp_sweep", "mdns", "reverse_dns"))
            setContent {
                InfraMapTheme {
                    CreateDiscoverySourceScreen(
                        state = CreateDiscoverySourceUiState(selectedCollectors = selectedCollectors),
                        actions = defaultActions(onCollectorsChanged = { selectedCollectors = it }),
                    )
                }
            }

            onNodeWithText("ICMP Ping").performScrollTo().assertIsDisplayed()
            onNodeWithText("ARP Sweep").performScrollTo().assertIsDisplayed()
            onNodeWithText("mDNS / Bonjour").performScrollTo().assertIsDisplayed()
            onNodeWithText("Reverse DNS").performScrollTo().assertIsDisplayed()
            onNodeWithText("SNMP").performScrollTo().assertIsDisplayed()
            onNodeWithText("Proxmox VE").performScrollTo().assertIsDisplayed()
            onNodeWithText("Docker").performScrollTo().assertIsDisplayed()
            onNodeWithText("UniFi").performScrollTo().assertIsDisplayed()
        }

    @Test
    fun togglingCollectorChipUpdatesSelection() =
        runComposeUiTest {
            var selectedCollectors by mutableStateOf(setOf("icmp_sweep", "arp_sweep", "mdns", "reverse_dns"))
            setContent {
                InfraMapTheme {
                    CreateDiscoverySourceScreen(
                        state = CreateDiscoverySourceUiState(selectedCollectors = selectedCollectors),
                        actions = defaultActions(onCollectorsChanged = { selectedCollectors = it }),
                    )
                }
            }

            // Click SNMP to add
            onNodeWithText("SNMP").performScrollTo().performClick()
            assertTrue(selectedCollectors.contains("snmp"))

            // Click ICMP Ping to remove
            onNodeWithText("ICMP Ping").performScrollTo().performClick()
            assertTrue(!selectedCollectors.contains("icmp_sweep"))
        }

    @Test
    fun implementedProviderChipsAreSelectableAndUnifiIsNot() =
        runComposeUiTest {
            // Proxmox and Docker have real provider implementations behind them now; UniFi
            // has none, so its chip stays disabled.
            var selectedCollectors by mutableStateOf(setOf("icmp_sweep", "arp_sweep"))
            setContent {
                InfraMapTheme {
                    CreateDiscoverySourceScreen(
                        state = CreateDiscoverySourceUiState(selectedCollectors = selectedCollectors),
                        actions = defaultActions(onCollectorsChanged = { selectedCollectors = it }),
                    )
                }
            }

            onNodeWithText("UniFi").performScrollTo().assertIsNotEnabled()
            onNodeWithText("UniFi").performClick()
            assertEquals(setOf("icmp_sweep", "arp_sweep"), selectedCollectors)

            onNodeWithText("Proxmox VE").performScrollTo().performClick()
            assertEquals(setOf("icmp_sweep", "arp_sweep", "proxmox"), selectedCollectors)

            onNodeWithText("Docker").performScrollTo().performClick()
            assertEquals(setOf("icmp_sweep", "arp_sweep", "proxmox", "docker"), selectedCollectors)
        }

    @Test
    fun providerConfigurationAppearsOnlyForSelectedProviders() =
        runComposeUiTest {
            setContent {
                InfraMapTheme {
                    CreateDiscoverySourceScreen(
                        state =
                            CreateDiscoverySourceUiState(
                                selectedCollectors = setOf("docker"),
                                connectionTests = mapOf("docker" to ConnectionTest.Healthy),
                            ),
                        actions = defaultActions(),
                    )
                }
            }

            onNodeWithTag("provider_field_docker_socket_path").performScrollTo().assertIsDisplayed()
            onNodeWithTag("test_connection_docker").performScrollTo().assertIsDisplayed()
            onNodeWithText("Conexão estabelecida").performScrollTo().assertIsDisplayed()
        }

    @Test
    fun testConnectionButtonIsDisabledWhileTheCheckRuns() =
        runComposeUiTest {
            setContent {
                InfraMapTheme {
                    CreateDiscoverySourceScreen(
                        state =
                            CreateDiscoverySourceUiState(
                                selectedCollectors = setOf("proxmox"),
                                connectionTests = mapOf("proxmox" to ConnectionTest.Testing),
                            ),
                        actions = defaultActions(),
                    )
                }
            }

            onNodeWithTag("test_connection_proxmox").performScrollTo().assertIsNotEnabled()
        }

    @Test
    fun rendersAllScheduleChipsAndSelectsPresets() =
        runComposeUiTest {
            var selectedCron by mutableStateOf("")
            setContent {
                InfraMapTheme {
                    CreateDiscoverySourceScreen(
                        state = CreateDiscoverySourceUiState(scheduleCron = selectedCron),
                        actions = defaultActions(onScheduleCronChanged = { selectedCron = it }),
                    )
                }
            }

            onNodeWithText("Manual").performScrollTo().assertIsDisplayed()
            onNodeWithText("5 min").performScrollTo().assertIsDisplayed()
            onNodeWithText("15 min").performScrollTo().assertIsDisplayed()
            onNodeWithText("1 hour").performScrollTo().assertIsDisplayed()
            onNodeWithText("6 hours").performScrollTo().assertIsDisplayed()
            onNodeWithText("Daily").performScrollTo().assertIsDisplayed()
            onNodeWithText("Custom").performScrollTo().assertIsDisplayed()

            onNodeWithText("15 min").performClick()
            assertEquals("*/15 * * * *", selectedCron)

            onNodeWithText("6 hours").performClick()
            assertEquals("0 */6 * * *", selectedCron)
        }

    @Test
    fun customScheduleChipExpandsAndAllowsEnteringCron() =
        runComposeUiTest {
            var selectedCron by mutableStateOf("")
            setContent {
                InfraMapTheme {
                    CreateDiscoverySourceScreen(
                        state = CreateDiscoverySourceUiState(scheduleCron = selectedCron),
                        actions = defaultActions(onScheduleCronChanged = { selectedCron = it }),
                    )
                }
            }

            onNodeWithText("Custom").performScrollTo().performClick()
            onNodeWithText("Cron Expression").performScrollTo().assertIsDisplayed()
            onNodeWithText("5-field cron format").performScrollTo().assertIsDisplayed()

            onNodeWithText("Cron Expression").performTextInput("*/20 * * * *")
            assertTrue(selectedCron.contains("*/20"))
        }

    @Test
    fun rendersSubnetSuggestionChipsAndTriggersSelection() =
        runComposeUiTest {
            val subnets =
                listOf(
                    SubnetSummary(id = "sub-1", name = "Home LAN", cidr = "192.168.1.0/24"),
                    SubnetSummary(id = "sub-2", name = "DMZ", cidr = "10.0.0.0/24"),
                )
            var selectedSubnet: SubnetSummary? = null

            setContent {
                InfraMapTheme {
                    CreateDiscoverySourceScreen(
                        state = CreateDiscoverySourceUiState(subnets = subnets),
                        actions = defaultActions(onSubnetSelected = { selectedSubnet = it }),
                    )
                }
            }

            onNodeWithText("Sub-redes cadastradas").performScrollTo().assertIsDisplayed()
            onNodeWithText("Home LAN").performScrollTo().assertIsDisplayed()
            onNodeWithText("192.168.1.0/24").performScrollTo().assertIsDisplayed()
            onNodeWithText("DMZ").performScrollTo().assertIsDisplayed()
            onNodeWithText("10.0.0.0/24").performScrollTo().assertIsDisplayed()

            onNodeWithText("Home LAN").performClick()
            assertEquals(subnets[0], selectedSubnet)
        }

    @Test
    fun togglingCheckboxRowTriggersCallback() =
        runComposeUiTest {
            var enabledState by mutableStateOf(true)
            setContent {
                InfraMapTheme {
                    CreateDiscoverySourceScreen(
                        state = CreateDiscoverySourceUiState(enabled = enabledState),
                        actions = defaultActions(onEnabledChanged = { enabledState = it }),
                    )
                }
            }

            onNodeWithText("Source enabled").performScrollTo().performClick()
            assertEquals(false, enabledState)
        }

    @Test
    fun rendersValidationErrorsAndErrorMessage() =
        runComposeUiTest {
            val errors =
                mapOf(
                    "name" to UiText.DynamicString("Source name is required"),
                    "collectors" to UiText.DynamicString("At least one collector required"),
                    "cidr" to UiText.DynamicString("Invalid CIDR"),
                )
            setContent {
                InfraMapTheme {
                    CreateDiscoverySourceScreen(
                        state =
                            CreateDiscoverySourceUiState(
                                validationErrors = errors,
                                errorMessage = UiText.DynamicString("Server error"),
                            ),
                        actions = defaultActions(),
                    )
                }
            }

            onNodeWithText("Source name is required").performScrollTo().assertIsDisplayed()
            onNodeWithText("At least one collector required").performScrollTo().assertIsDisplayed()
            onNodeWithText("Invalid CIDR").performScrollTo().assertIsDisplayed()
            onNodeWithText("Server error").performScrollTo().assertIsDisplayed()
        }

    @Test
    fun submittingStateShowsLoadingText() =
        runComposeUiTest {
            setContent {
                InfraMapTheme {
                    CreateDiscoverySourceScreen(
                        state = CreateDiscoverySourceUiState(isSubmitting = true),
                        actions = defaultActions(),
                    )
                }
            }

            onNodeWithText("Creating...").performScrollTo().assertIsDisplayed()
        }
}
