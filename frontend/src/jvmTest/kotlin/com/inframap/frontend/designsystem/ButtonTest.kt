package com.inframap.frontend.designsystem

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runComposeUiTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

@OptIn(ExperimentalTestApi::class)
class ButtonTest {
    @Test
    fun primaryButtonRendersText() =
        runComposeUiTest {
            setContent {
                InfraMapTheme {
                    InfraMapButton(text = "Save", onClick = {})
                }
            }
            onNodeWithText("Save").assertIsDisplayed()
        }

    @Test
    fun primaryButtonClickTriggersCallback() =
        runComposeUiTest {
            var clicked = false
            setContent {
                InfraMapTheme {
                    InfraMapButton(text = "Save", onClick = { clicked = true })
                }
            }
            onNodeWithText("Save").performClick()
            assertEquals(true, clicked)
        }

    @Test
    fun disabledButtonDoesNotTriggerCallback() =
        runComposeUiTest {
            var clicked = false
            setContent {
                InfraMapTheme {
                    InfraMapButton(text = "Save", onClick = { clicked = true }, enabled = false)
                }
            }
            onNodeWithText("Save").assertIsNotEnabled()
            assertEquals(false, clicked)
        }

    @Test
    fun outlinedButtonRendersText() =
        runComposeUiTest {
            setContent {
                InfraMapTheme {
                    InfraMapOutlinedButton(text = "Cancel", onClick = {})
                }
            }
            onNodeWithText("Cancel").assertIsDisplayed()
        }

    @Test
    fun outlinedButtonClickTriggersCallback() =
        runComposeUiTest {
            var clicked = false
            setContent {
                InfraMapTheme {
                    InfraMapOutlinedButton(text = "Cancel", onClick = { clicked = true })
                }
            }
            onNodeWithText("Cancel").performClick()
            assertEquals(true, clicked)
        }

    @Test
    fun enabledButtonIsEnabled() =
        runComposeUiTest {
            setContent {
                InfraMapTheme {
                    InfraMapButton(text = "Save", onClick = {}, enabled = true)
                }
            }
            onNodeWithText("Save").assertIsEnabled()
        }
}
