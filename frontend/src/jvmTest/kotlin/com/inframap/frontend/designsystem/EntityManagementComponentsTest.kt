package com.inframap.frontend.designsystem

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runComposeUiTest
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

@OptIn(ExperimentalTestApi::class)
class EntityManagementComponentsTest {
    @Test
    fun dangerZoneRendersContentAndHandlesClick() =
        runComposeUiTest {
            var clicked = false
            setContent {
                InfraMapTheme {
                    DangerZone(
                        title = "Danger Area",
                        description = "Irreversible action warning",
                        deleteLabel = "Delete Subnet",
                        onDelete = { clicked = true },
                    )
                }
            }

            onNodeWithText("Danger Area").assertIsDisplayed()
            onNodeWithText("Irreversible action warning").assertIsDisplayed()
            onNodeWithText("Delete Subnet").assertIsDisplayed()

            onNodeWithText("Delete Subnet").performClick()
            assertTrue(clicked)
        }

    @Test
    fun impactConfirmationDialogRendersLinesAndHandlesCallbacks() =
        runComposeUiTest {
            var confirmed = false
            var dismissed = false
            setContent {
                InfraMapTheme {
                    ImpactConfirmationDialog(
                        title = "Delete Confirmation",
                        description = "Impact summary details",
                        impactLines = listOf("5 devices affected", "2 collectors stopped"),
                        onConfirm = { confirmed = true },
                        onDismiss = { dismissed = true },
                    )
                }
            }

            onNodeWithText("Delete Confirmation").assertIsDisplayed()
            onNodeWithText("Impact summary details").assertIsDisplayed()
            onNodeWithText("5 devices affected").assertIsDisplayed()
            onNodeWithText("2 collectors stopped").assertIsDisplayed()

            onNodeWithText("Confirm Deletion").performClick()
            assertTrue(confirmed)

            onNodeWithText("Cancel").performClick()
            assertTrue(dismissed)
        }

    @Test
    fun impactConfirmationDialogDisablesConfirmWhileLoading() =
        runComposeUiTest {
            setContent {
                InfraMapTheme {
                    ImpactConfirmationDialog(
                        title = "Calculating Impact",
                        isLoadingImpact = true,
                        onConfirm = {},
                        onDismiss = {},
                    )
                }
            }

            onNodeWithText("Calculating impact…").assertIsDisplayed()
            onNodeWithText("Confirm Deletion").assertIsNotEnabled()
        }

    @Test
    fun rowOverflowMenuOpensAndRendersActions() =
        runComposeUiTest {
            var editClicked = false
            var deleteClicked = false

            setContent {
                InfraMapTheme {
                    RowOverflowMenu(
                        onEdit = { editClicked = true },
                        onDelete = { deleteClicked = true },
                    )
                }
            }

            onNodeWithContentDescription("More options").assertIsDisplayed()
            onNodeWithContentDescription("More options").performClick()

            onNodeWithText("Edit").assertIsDisplayed()
            onNodeWithText("Delete").assertIsDisplayed()

            onNodeWithText("Edit").performClick()
            assertTrue(editClicked)
        }
}
