package com.inframap.frontend.designsystem

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertDoesNotExist
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.runComposeUiTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

@OptIn(ExperimentalTestApi::class)
class ChipTest {
    private val standardOptions =
        listOf(
            ChipOption(
                value = "5m",
                label = "5 min",
                icon = InfraMapIcons.Timer,
                description = "Quick scan",
            ),
            ChipOption(
                value = "15m",
                label = "15 min",
                icon = InfraMapIcons.Schedule,
            ),
            ChipOption(
                value = "1h",
                label = "1 hora",
                icon = InfraMapIcons.Schedule,
            ),
            ChipOption(
                value = "1d",
                label = "Diário",
                icon = InfraMapIcons.NightsStay,
            ),
            ChipOption(
                value = "manual",
                label = "Manual",
                icon = InfraMapIcons.PauseCircle,
            ),
        )

    @Test
    fun choiceChipGroupRendersAllOptions() =
        runComposeUiTest {
            setContent {
                InfraMapTheme {
                    InfraMapChoiceChipGroup(
                        options = standardOptions,
                        selected = "5m",
                        onSelected = {},
                    )
                }
            }
            onNodeWithText("5 min").assertIsDisplayed()
            onNodeWithText("15 min").assertIsDisplayed()
            onNodeWithText("1 hora").assertIsDisplayed()
            onNodeWithText("Diário").assertIsDisplayed()
            onNodeWithText("Manual").assertIsDisplayed()
        }

    @Test
    fun choiceChipGroupWithLabelRendersHeader() =
        runComposeUiTest {
            setContent {
                InfraMapTheme {
                    InfraMapChoiceChipGroup(
                        options = standardOptions,
                        selected = "5m",
                        onSelected = {},
                        label = "Frequência de Varredura",
                    )
                }
            }
            onNodeWithText("Frequência de Varredura").assertIsDisplayed()
        }

    @Test
    fun choiceChipGroupSelectionTriggersCallback() =
        runComposeUiTest {
            var selectedValue by mutableStateOf("5m")
            setContent {
                InfraMapTheme {
                    InfraMapChoiceChipGroup(
                        options = standardOptions,
                        selected = selectedValue,
                        onSelected = { selectedValue = it },
                    )
                }
            }
            onNodeWithText("15 min").performClick()
            assertEquals("15m", selectedValue)
        }

    @Test
    fun choiceChipGroupRendersDescriptions() =
        runComposeUiTest {
            setContent {
                InfraMapTheme {
                    InfraMapChoiceChipGroup(
                        options = standardOptions,
                        selected = "5m",
                        onSelected = {},
                    )
                }
            }
            onNodeWithText("Quick scan").assertIsDisplayed()
        }

    @Test
    fun choiceChipGroupDisabledOptionsCannotBeClicked() =
        runComposeUiTest {
            var selectedValue by mutableStateOf("5m")
            setContent {
                InfraMapTheme {
                    InfraMapChoiceChipGroup(
                        options = standardOptions,
                        selected = selectedValue,
                        onSelected = { selectedValue = it },
                        enabled = false,
                    )
                }
            }
            onNodeWithText("15 min").assertIsNotEnabled()
            onNodeWithText("15 min").performClick()
            assertEquals("5m", selectedValue)
        }

    @Test
    fun choiceChipGroupWithCustomOptionRendersCustomChip() =
        runComposeUiTest {
            val custom =
                ChipCustomOption(
                    chipLabel = "Personalizado",
                    chipIcon = InfraMapIcons.Tune,
                    inputLabel = "Expressão Cron",
                    inputPlaceholder = "*/10 * * * *",
                    currentValue = "*/10 * * * *",
                    onValueChanged = {},
                    parseValue = { it },
                    formatValue = { it },
                    helperText = "Ex: */10 * * * *",
                    isCustom = { it.startsWith("*/") },
                )
            setContent {
                InfraMapTheme {
                    InfraMapChoiceChipGroup(
                        options = standardOptions,
                        selected = "5m",
                        onSelected = {},
                        customOption = custom,
                    )
                }
            }
            onNodeWithText("Personalizado").assertIsDisplayed()
            onNodeWithText("Expressão Cron").assertDoesNotExist()
        }

    @Test
    fun choiceChipGroupCustomOptionExpandsInputFieldWhenActive() =
        runComposeUiTest {
            val custom =
                ChipCustomOption(
                    chipLabel = "Personalizado",
                    chipIcon = InfraMapIcons.Tune,
                    inputLabel = "Expressão Cron",
                    inputPlaceholder = "*/10 * * * *",
                    currentValue = "*/30 * * * *",
                    onValueChanged = {},
                    parseValue = { it },
                    formatValue = { it },
                    helperText = "Expressão no formato padrão",
                    isCustom = { it.startsWith("*/") },
                )
            setContent {
                InfraMapTheme {
                    InfraMapChoiceChipGroup(
                        options = standardOptions,
                        selected = "*/30 * * * *",
                        onSelected = {},
                        customOption = custom,
                    )
                }
            }
            onNodeWithText("Personalizado").assertIsDisplayed()
            onNodeWithText("Expressão Cron").assertIsDisplayed()
            onNodeWithText("Expressão no formato padrão").assertIsDisplayed()
        }

    @Test
    fun choiceChipGroupCustomOptionClickTriggersParseAndSelect() =
        runComposeUiTest {
            var selectedValue by mutableStateOf("5m")
            val custom =
                ChipCustomOption(
                    chipLabel = "Personalizado",
                    chipIcon = InfraMapIcons.Tune,
                    inputLabel = "Expressão Cron",
                    currentValue = "*/20 * * * *",
                    onValueChanged = {},
                    parseValue = { it.ifBlank { null } },
                    formatValue = { it },
                    isCustom = { it.startsWith("*/") },
                )
            setContent {
                InfraMapTheme {
                    InfraMapChoiceChipGroup(
                        options = standardOptions,
                        selected = selectedValue,
                        onSelected = { selectedValue = it },
                        customOption = custom,
                    )
                }
            }
            onNodeWithText("Personalizado").performClick()
            assertEquals("*/20 * * * *", selectedValue)
        }

    @Test
    fun choiceChipGroupCustomInputTextEntryUpdatesValue() =
        runComposeUiTest {
            var changedValue = ""
            var selectedValue by mutableStateOf("*/10 * * * *")
            val custom =
                ChipCustomOption(
                    chipLabel = "Personalizado",
                    chipIcon = InfraMapIcons.Tune,
                    inputLabel = "Expressão Cron",
                    currentValue = "",
                    onValueChanged = { changedValue = it },
                    parseValue = { it.ifBlank { null } },
                    formatValue = { it },
                    isCustom = { it.startsWith("*/") },
                )
            setContent {
                InfraMapTheme {
                    InfraMapChoiceChipGroup(
                        options = standardOptions,
                        selected = selectedValue,
                        onSelected = { selectedValue = it },
                        customOption = custom,
                    )
                }
            }
            onNodeWithText("Expressão Cron").performTextInput("*/15 * * * *")
            assertEquals("*/15 * * * *", changedValue)
            assertEquals("*/15 * * * *", selectedValue)
        }

    @Test
    fun filterChipGroupRendersOptionsAndLabel() =
        runComposeUiTest {
            val filterOptions =
                listOf(
                    ChipOption(value = "docker", label = "Docker", icon = InfraMapIcons.ViewInAr),
                    ChipOption(value = "proxmox", label = "Proxmox", icon = InfraMapIcons.Cloud),
                    ChipOption(value = "unifi", label = "UniFi", icon = InfraMapIcons.Wifi),
                )
            setContent {
                InfraMapTheme {
                    InfraMapFilterChipGroup(
                        options = filterOptions,
                        selected = setOf("docker"),
                        onSelectionChanged = {},
                        label = "Filtro de Provedores",
                    )
                }
            }
            onNodeWithText("Filtro de Provedores").assertIsDisplayed()
            onNodeWithText("Docker").assertIsDisplayed()
            onNodeWithText("Proxmox").assertIsDisplayed()
            onNodeWithText("UniFi").assertIsDisplayed()
        }

    @Test
    fun filterChipGroupTogglingAddsAndRemovesSelection() =
        runComposeUiTest {
            val filterOptions =
                listOf(
                    ChipOption(
                        value = "ping",
                        label = "Ping / ICMP",
                        icon = InfraMapIcons.NetworkPing,
                        description = "Echo requests",
                    ),
                    ChipOption(value = "snmp", label = "SNMP", icon = InfraMapIcons.Layers),
                )
            var selection by mutableStateOf(setOf("ping"))
            setContent {
                InfraMapTheme {
                    InfraMapFilterChipGroup(
                        options = filterOptions,
                        selected = selection,
                        onSelectionChanged = { selection = it },
                    )
                }
            }
            onNodeWithText("Echo requests").assertIsDisplayed()

            // Toggle SNMP on
            onNodeWithText("SNMP").performClick()
            assertTrue(selection.contains("ping"))
            assertTrue(selection.contains("snmp"))

            // Toggle Ping off
            onNodeWithText("Ping / ICMP").performClick()
            assertEquals(setOf("snmp"), selection)
        }

    @Test
    fun filterChipGroupDisabledCannotBeToggled() =
        runComposeUiTest {
            val filterOptions =
                listOf(
                    ChipOption(value = "ping", label = "Ping", icon = InfraMapIcons.NetworkCheck),
                    ChipOption(value = "snmp", label = "SNMP", icon = InfraMapIcons.Check),
                )
            var selection by mutableStateOf(setOf("ping"))
            setContent {
                InfraMapTheme {
                    InfraMapFilterChipGroup(
                        options = filterOptions,
                        selected = selection,
                        onSelectionChanged = { selection = it },
                        enabled = false,
                    )
                }
            }
            onNodeWithText("SNMP").assertIsNotEnabled()
            onNodeWithText("SNMP").performClick()
            assertEquals(setOf("ping"), selection)
        }

    @Test
    fun verifyAllVectorIconsAreInitialized() {
        val icons =
            listOf(
                InfraMapIcons.Dashboard,
                InfraMapIcons.Dns,
                InfraMapIcons.MoveToInbox,
                InfraMapIcons.Lan,
                InfraMapIcons.Radar,
                InfraMapIcons.AccountTree,
                InfraMapIcons.KeyboardReturn,
                InfraMapIcons.Timer,
                InfraMapIcons.Schedule,
                InfraMapIcons.NightsStay,
                InfraMapIcons.PauseCircle,
                InfraMapIcons.NetworkCheck,
                InfraMapIcons.NetworkPing,
                InfraMapIcons.Cloud,
                InfraMapIcons.ViewInAr,
                InfraMapIcons.Layers,
                InfraMapIcons.Wifi,
                InfraMapIcons.Tune,
                InfraMapIcons.Settings,
                InfraMapIcons.Check,
            )
        icons.forEach { icon ->
            assertNotNull(icon)
            assertEquals(24f, icon.viewportWidth)
            assertEquals(24f, icon.viewportHeight)
        }
    }
}
