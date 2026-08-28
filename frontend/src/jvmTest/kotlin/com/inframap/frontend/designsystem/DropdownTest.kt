package com.inframap.frontend.designsystem

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertDoesNotExist
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runComposeUiTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

@OptIn(ExperimentalTestApi::class, ExperimentalMaterial3Api::class)
class DropdownTest {
    private val sampleOptions =
        listOf(
            "icmp" to "ICMP Ping",
            "arp" to "ARP Scan",
            "snmp" to "SNMP Collector",
        )

    @Test
    fun dropdownRendersLabelAndSelectedValue() =
        runComposeUiTest {
            setContent {
                InfraMapTheme {
                    InfraMapDropdown(
                        label = "Scan Protocol",
                        options = sampleOptions,
                        selectedValue = "icmp",
                        onSelected = {},
                    )
                }
            }
            onNodeWithText("Scan Protocol").assertIsDisplayed()
            onNodeWithText("ICMP Ping").assertIsDisplayed()
        }

    @Test
    fun dropdownRendersErrorMessageWhenProvided() =
        runComposeUiTest {
            setContent {
                InfraMapTheme {
                    InfraMapDropdown(
                        label = "Scan Protocol",
                        options = sampleOptions,
                        selectedValue = "icmp",
                        onSelected = {},
                        error = "Protocol is required",
                    )
                }
            }
            onNodeWithText("Protocol is required").assertIsDisplayed()
        }

    @Test
    fun dropdownWithoutErrorDoesNotRenderErrorMessage() =
        runComposeUiTest {
            setContent {
                InfraMapTheme {
                    InfraMapDropdown(
                        label = "Scan Protocol",
                        options = sampleOptions,
                        selectedValue = "icmp",
                        onSelected = {},
                        error = null,
                    )
                }
            }
            onNodeWithText("Protocol is required").assertDoesNotExist()
        }

    @Test
    fun dropdownExpandsAndSelectsOption() =
        runComposeUiTest {
            var selected: String? = null
            setContent {
                InfraMapTheme {
                    InfraMapDropdown(
                        label = "Scan Protocol",
                        options = sampleOptions,
                        selectedValue = "icmp",
                        onSelected = { selected = it },
                    )
                }
            }
            onNodeWithText("ICMP Ping").performClick()
            onNodeWithText("ARP Scan").assertIsDisplayed()
            onNodeWithText("ARP Scan").performClick()
            assertEquals("arp", selected)
        }

    @Test
    fun disabledDropdownIsNotEnabled() =
        runComposeUiTest {
            setContent {
                InfraMapTheme {
                    InfraMapDropdown(
                        label = "Scan Protocol",
                        options = sampleOptions,
                        selectedValue = "icmp",
                        onSelected = {},
                        enabled = false,
                    )
                }
            }
            onNodeWithText("ICMP Ping").assertIsNotEnabled()
        }

    @Test
    fun dropdownWithGenericTypeWorks() =
        runComposeUiTest {
            val intOptions =
                listOf(
                    1 to "First Level",
                    2 to "Second Level",
                )
            var selectedInt: Int? = null
            setContent {
                InfraMapTheme {
                    InfraMapDropdown(
                        label = "Level",
                        options = intOptions,
                        selectedValue = 1,
                        onSelected = { selectedInt = it },
                    )
                }
            }
            onNodeWithText("Level").assertIsDisplayed()
            onNodeWithText("First Level").assertIsDisplayed()
            onNodeWithText("First Level").performClick()
            onNodeWithText("Second Level").performClick()
            assertEquals(2, selectedInt)
        }
}
