package com.inframap.frontend.designsystem

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runComposeUiTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

@OptIn(ExperimentalTestApi::class)
class CheckboxTest {
    @Test
    fun checkboxRowRendersLabel() =
        runComposeUiTest {
            setContent {
                InfraMapTheme {
                    InfraMapCheckboxRow(
                        checked = false,
                        onCheckedChange = {},
                        label = "Enable discovery",
                    )
                }
            }
            onNodeWithText("Enable discovery").assertIsDisplayed()
        }

    @Test
    fun checkboxRowRendersDescriptionWhenProvided() =
        runComposeUiTest {
            setContent {
                InfraMapTheme {
                    InfraMapCheckboxRow(
                        checked = false,
                        onCheckedChange = {},
                        label = "Auto-scan",
                        description = "Scan network periodically",
                    )
                }
            }
            onNodeWithText("Auto-scan").assertIsDisplayed()
            onNodeWithText("Scan network periodically").assertIsDisplayed()
        }

    @Test
    fun checkboxRowWithoutDescriptionDoesNotRenderDescription() =
        runComposeUiTest {
            setContent {
                InfraMapTheme {
                    InfraMapCheckboxRow(
                        checked = false,
                        onCheckedChange = {},
                        label = "Only Label",
                        description = null,
                    )
                }
            }
            onNodeWithText("Only Label").assertIsDisplayed()
            onNodeWithText("Scan network periodically").assertDoesNotExist()
        }

    @Test
    fun checkboxRowCheckedStateChecked() =
        runComposeUiTest {
            setContent {
                InfraMapTheme {
                    InfraMapCheckboxRow(
                        checked = true,
                        onCheckedChange = {},
                        label = "Active status",
                    )
                }
            }
            onNode(isToggleable()).assertIsOn()
        }

    @Test
    fun checkboxRowCheckedStateUnchecked() =
        runComposeUiTest {
            setContent {
                InfraMapTheme {
                    InfraMapCheckboxRow(
                        checked = false,
                        onCheckedChange = {},
                        label = "Active status",
                    )
                }
            }
            onNode(isToggleable()).assertIsOff()
        }

    @Test
    fun checkboxRowToggleTriggersCallback() =
        runComposeUiTest {
            var toggledValue: Boolean? = null
            setContent {
                InfraMapTheme {
                    InfraMapCheckboxRow(
                        checked = false,
                        onCheckedChange = { toggledValue = it },
                        label = "Toggle Me",
                    )
                }
            }
            onNode(isToggleable()).performClick()
            assertEquals(true, toggledValue)
        }

    @Test
    fun disabledCheckboxRowDoesNotToggle() =
        runComposeUiTest {
            var toggledValue: Boolean? = null
            setContent {
                InfraMapTheme {
                    InfraMapCheckboxRow(
                        checked = false,
                        onCheckedChange = { toggledValue = it },
                        label = "Disabled Row",
                        enabled = false,
                    )
                }
            }
            onNode(isToggleable()).assertIsNotEnabled()
            assertEquals(null, toggledValue)
        }

    @Test
    fun enabledCheckboxRowIsEnabled() =
        runComposeUiTest {
            setContent {
                InfraMapTheme {
                    InfraMapCheckboxRow(
                        checked = true,
                        onCheckedChange = {},
                        label = "Enabled Row",
                        enabled = true,
                    )
                }
            }
            onNode(isToggleable()).assertIsEnabled()
        }
}
