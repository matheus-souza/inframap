package com.inframap.frontend.ui.discovery

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
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
        onSourceTypeChanged: (String) -> Unit = {},
        onScheduleCronChanged: (String) -> Unit = {},
        onConfigCidrChanged: (String) -> Unit = {},
        onEnabledChanged: (Boolean) -> Unit = {},
        onSubnetSelected: (SubnetSummary) -> Unit = {},
        onSubmitClicked: () -> Unit = {},
        onCancelClicked: () -> Unit = {},
    ) = CreateDiscoverySourceActions(
        onNameChanged = onNameChanged,
        onSourceTypeChanged = onSourceTypeChanged,
        onScheduleCronChanged = onScheduleCronChanged,
        onConfigCidrChanged = onConfigCidrChanged,
        onEnabledChanged = onEnabledChanged,
        onSubnetSelected = onSubnetSelected,
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

            onNodeWithText("Nova Fonte de Descoberta").assertIsDisplayed()
            onNodeWithText("Nome da Fonte *").assertIsDisplayed()
            onNodeWithText("Tipo de Fonte *").assertIsDisplayed()
            onNodeWithText("CIDR Alvo (ex: 192.168.1.0/24)").assertIsDisplayed()
            onNodeWithText("Agendamento").assertIsDisplayed()
            onNodeWithText("Fonte habilitada").assertIsDisplayed()
            onNodeWithText("Criar Fonte").assertIsDisplayed()
            onNodeWithText("Cancelar").assertIsDisplayed()
        }

    @Test
    fun rendersAllSourceTypeChipsAndSelects() =
        runComposeUiTest {
            var selectedType by mutableStateOf("")
            setContent {
                InfraMapTheme {
                    CreateDiscoverySourceScreen(
                        state = CreateDiscoverySourceUiState(sourceType = selectedType),
                        actions = defaultActions(onSourceTypeChanged = { selectedType = it }),
                    )
                }
            }

            onNodeWithText("ICMP Ping").assertIsDisplayed()
            onNodeWithText("ARP Sweep").assertIsDisplayed()
            onNodeWithText("mDNS").assertIsDisplayed()
            onNodeWithText("Proxmox").assertIsDisplayed()
            onNodeWithText("Docker").assertIsDisplayed()
            onNodeWithText("UniFi").assertIsDisplayed()

            onNodeWithText("ARP Sweep").performClick()
            assertEquals("arp_sweep", selectedType)

            onNodeWithText("Docker").performClick()
            assertEquals("docker", selectedType)
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

            onNodeWithText("Manual").assertIsDisplayed()
            onNodeWithText("5 min").assertIsDisplayed()
            onNodeWithText("15 min").assertIsDisplayed()
            onNodeWithText("1 hora").assertIsDisplayed()
            onNodeWithText("6 horas").assertIsDisplayed()
            onNodeWithText("Diário").assertIsDisplayed()
            onNodeWithText("Personalizado").assertIsDisplayed()

            onNodeWithText("15 min").performClick()
            assertEquals("*/15 * * * *", selectedCron)

            onNodeWithText("6 horas").performClick()
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

            onNodeWithText("Personalizado").performClick()
            onNodeWithText("Expressão Cron").assertIsDisplayed()
            onNodeWithText("Formato cron de 5 campos").assertIsDisplayed()

            onNodeWithText("Expressão Cron").performTextInput("*/20 * * * *")
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

            onNodeWithText("Sub-redes cadastradas").assertIsDisplayed()
            onNodeWithText("Home LAN").assertIsDisplayed()
            onNodeWithText("192.168.1.0/24").assertIsDisplayed()
            onNodeWithText("DMZ").assertIsDisplayed()
            onNodeWithText("10.0.0.0/24").assertIsDisplayed()

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

            onNodeWithText("Fonte habilitada").performClick()
            assertEquals(false, enabledState)
        }

    @Test
    fun rendersValidationErrorsAndErrorMessage() =
        runComposeUiTest {
            val errors =
                mapOf(
                    "name" to UiText.DynamicString("Nome obrigatorio"),
                    "type" to UiText.DynamicString("Tipo obrigatorio"),
                    "cidr" to UiText.DynamicString("CIDR invalido"),
                )
            setContent {
                InfraMapTheme {
                    CreateDiscoverySourceScreen(
                        state =
                            CreateDiscoverySourceUiState(
                                validationErrors = errors,
                                errorMessage = UiText.DynamicString("Erro no servidor"),
                            ),
                        actions = defaultActions(),
                    )
                }
            }

            onNodeWithText("Nome obrigatorio").assertIsDisplayed()
            onNodeWithText("Tipo obrigatorio").assertIsDisplayed()
            onNodeWithText("CIDR invalido").assertIsDisplayed()
            onNodeWithText("Erro no servidor").assertIsDisplayed()
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

            onNodeWithText("Criando...").assertIsDisplayed()
        }
}
