package com.inframap.frontend.designsystem

import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.runtime.Composable
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.click
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performMouseInput
import androidx.compose.ui.test.runComposeUiTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

@OptIn(ExperimentalTestApi::class)
class ImpactConfirmationDialogTest {
    // The app wraps every screen in a SelectionContainer (MainScaffold), and a dialog opened from
    // a screen inherits its selection registrar, so the dialog is exercised inside one.
    private fun ComposeUiTest.setScreenContent(content: @Composable () -> Unit) {
        setContent {
            InfraMapTheme {
                SelectionContainer { content() }
            }
        }
    }

    private fun ComposeUiTest.mouseClick(text: String) {
        onNodeWithText(text).performMouseInput {
            moveTo(center)
            click(center)
        }
        waitForIdle()
    }

    @Test
    fun confirmButtonClickCallsOnConfirm() =
        runComposeUiTest {
            var confirmed = 0
            setScreenContent {
                ImpactConfirmationDialog(
                    onConfirm = { confirmed++ },
                    onDismiss = {},
                    description = "Delete the subnet?",
                    impactLines = listOf("1 linked item"),
                    confirmButtonText = "Confirm",
                    cancelButtonText = "Cancel",
                )
            }

            mouseClick("Confirm")

            assertEquals(1, confirmed)
        }

    @Test
    fun cancelButtonClickCallsOnDismiss() =
        runComposeUiTest {
            var dismissed = 0
            setScreenContent {
                ImpactConfirmationDialog(
                    onConfirm = {},
                    onDismiss = { dismissed++ },
                    confirmButtonText = "Confirm",
                    cancelButtonText = "Cancel",
                )
            }

            mouseClick("Cancel")

            assertEquals(1, dismissed)
        }

    @Test
    fun clickOnDialogTextKeepsButtonsWorking() =
        runComposeUiTest {
            var confirmed = 0
            setScreenContent {
                ImpactConfirmationDialog(
                    onConfirm = { confirmed++ },
                    onDismiss = {},
                    description = "Delete the subnet?",
                    impactLines = listOf("1 linked item"),
                    confirmButtonText = "Confirm",
                    cancelButtonText = "Cancel",
                )
            }

            mouseClick("Delete the subnet?")
            mouseClick("Confirm")

            assertEquals(1, confirmed)
        }
}
