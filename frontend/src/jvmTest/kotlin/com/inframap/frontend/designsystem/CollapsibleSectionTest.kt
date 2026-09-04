package com.inframap.frontend.designsystem

import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runComposeUiTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Test

@OptIn(ExperimentalTestApi::class)
class CollapsibleSectionTest {
    @Test
    fun opensExpandedByDefault() =
        runComposeUiTest {
            setContent {
                InfraMapTheme {
                    CollapsibleSection(title = "Sugestões", icon = InfraMapIcons.Lan) {
                        Text("conteúdo")
                    }
                }
            }

            onNodeWithText("Sugestões").assertIsDisplayed()
            onNodeWithText("conteúdo").assertIsDisplayed()
        }

    @Test
    fun headerClickHidesAndRestoresContent() =
        runComposeUiTest {
            setContent {
                InfraMapTheme {
                    CollapsibleSection(title = "Sugestões", icon = InfraMapIcons.Lan) {
                        Text("conteúdo")
                    }
                }
            }

            onNodeWithText("Sugestões").performClick()
            waitForIdle()
            onNodeWithText("conteúdo").assertDoesNotExist()

            // The title survives the fold: a collapsed section still has to say what it holds.
            onNodeWithText("Sugestões").assertIsDisplayed()

            onNodeWithText("Sugestões").performClick()
            waitForIdle()
            onNodeWithText("conteúdo").assertIsDisplayed()
        }

    @Test
    fun startsCollapsedWhenAsked() =
        runComposeUiTest {
            setContent {
                InfraMapTheme {
                    CollapsibleSection(
                        title = "Sugestões",
                        icon = InfraMapIcons.Lan,
                        initiallyExpanded = false,
                    ) {
                        Text("conteúdo")
                    }
                }
            }

            onNodeWithText("conteúdo").assertDoesNotExist()
        }

    @Test
    fun hoistedStateDrivesTheFoldAndReportsToggles() =
        runComposeUiTest {
            var expanded by mutableStateOf(true)
            var toggles = 0

            setContent {
                InfraMapTheme {
                    CollapsibleSection(
                        title = "Sugestões",
                        icon = InfraMapIcons.Lan,
                        expanded = expanded,
                        onToggle = {
                            toggles++
                            expanded = !expanded
                        },
                    ) {
                        Text("conteúdo")
                    }
                }
            }

            onNodeWithText("conteúdo").assertIsDisplayed()
            onNodeWithText("Sugestões").performClick()
            waitForIdle()

            assertEquals(1, toggles)
            assertFalse(expanded)
            onNodeWithText("conteúdo").assertDoesNotExist()
        }
}
