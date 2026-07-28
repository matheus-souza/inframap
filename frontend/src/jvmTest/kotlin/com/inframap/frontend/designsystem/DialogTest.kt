package com.inframap.frontend.designsystem

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runComposeUiTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

@OptIn(ExperimentalTestApi::class)
class DialogTest {
    @Test
    fun dialogRendersTitleAndMessage() =
        runComposeUiTest {
            setContent {
                InfraMapTheme {
                    InfraMapConfirmDialog(
                        title = "Delete Device",
                        message = "Are you sure?",
                        onConfirm = {},
                        onDismiss = {},
                    )
                }
            }
            onNodeWithText("Delete Device").assertIsDisplayed()
            onNodeWithText("Are you sure?").assertIsDisplayed()
        }

    @Test
    fun dialogRendersDefaultButtonLabels() =
        runComposeUiTest {
            setContent {
                InfraMapTheme {
                    InfraMapConfirmDialog(
                        title = "Title",
                        message = "Message",
                        onConfirm = {},
                        onDismiss = {},
                    )
                }
            }
            onNodeWithText("Confirm").assertIsDisplayed()
            onNodeWithText("Cancel").assertIsDisplayed()
        }

    @Test
    fun dialogRendersCustomButtonLabels() =
        runComposeUiTest {
            setContent {
                InfraMapTheme {
                    InfraMapConfirmDialog(
                        title = "Title",
                        message = "Message",
                        confirmText = "Delete",
                        dismissText = "Keep",
                        onConfirm = {},
                        onDismiss = {},
                    )
                }
            }
            onNodeWithText("Delete").assertIsDisplayed()
            onNodeWithText("Keep").assertIsDisplayed()
        }

    @Test
    fun confirmButtonTriggersCallback() =
        runComposeUiTest {
            var confirmed = false
            setContent {
                InfraMapTheme {
                    InfraMapConfirmDialog(
                        title = "Title",
                        message = "Message",
                        onConfirm = { confirmed = true },
                        onDismiss = {},
                    )
                }
            }
            onNodeWithText("Confirm").performClick()
            assertEquals(true, confirmed)
        }

    @Test
    fun cancelButtonTriggersCallback() =
        runComposeUiTest {
            var dismissed = false
            setContent {
                InfraMapTheme {
                    InfraMapConfirmDialog(
                        title = "Title",
                        message = "Message",
                        onConfirm = {},
                        onDismiss = { dismissed = true },
                    )
                }
            }
            onNodeWithText("Cancel").performClick()
            assertEquals(true, dismissed)
        }
}
