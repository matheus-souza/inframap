package com.inframap.frontend.designsystem

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runComposeUiTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

@OptIn(ExperimentalTestApi::class)
class SuggestionCardTest {
    @Test
    fun showsBothLines() =
        runComposeUiTest {
            setContent {
                InfraMapTheme {
                    SuggestionCard(
                        title = "eth0 — 172.25.0.0/16",
                        detail = "172.25.0.3 · 5a:e1:99:9b:76:be",
                        onClick = {},
                    )
                }
            }

            onNodeWithText("eth0 — 172.25.0.0/16").assertIsDisplayed()
            onNodeWithText("172.25.0.3 · 5a:e1:99:9b:76:be").assertIsDisplayed()
        }

    @Test
    fun clickingEitherLineSelectsTheCard() =
        runComposeUiTest {
            var clicks = 0
            setContent {
                InfraMapTheme {
                    SuggestionCard(
                        title = "Rede Casa",
                        detail = "192.168.18.0/24",
                        onClick = { clicks++ },
                    )
                }
            }

            // The detail line is part of the same clickable surface, not a separate target.
            onNodeWithText("192.168.18.0/24").performClick()
            assertEquals(1, clicks)
        }

    @Test
    fun reportsSelectionToAccessibility() =
        runComposeUiTest {
            setContent {
                InfraMapTheme {
                    SuggestionCard(
                        title = "Rede Casa",
                        detail = "192.168.18.0/24",
                        onClick = {},
                        isSelected = true,
                    )
                }
            }

            onNodeWithText("Rede Casa").assertIsSelected()
        }

    @Test
    fun isNotSelectedByDefault() =
        runComposeUiTest {
            setContent {
                InfraMapTheme {
                    SuggestionCard(
                        title = "Rede Casa",
                        detail = "192.168.18.0/24",
                        onClick = {},
                    )
                }
            }

            onNodeWithText("Rede Casa").assertIsNotSelected()
        }
}
