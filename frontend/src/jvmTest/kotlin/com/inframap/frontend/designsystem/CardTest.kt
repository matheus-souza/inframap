package com.inframap.frontend.designsystem

import androidx.compose.material3.Text
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runComposeUiTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

@OptIn(ExperimentalTestApi::class)
class CardTest {
    @Test
    fun cardRendersContent() =
        runComposeUiTest {
            setContent {
                InfraMapTheme {
                    InfraMapCard {
                        Text("Card Content")
                    }
                }
            }
            onNodeWithText("Card Content").assertIsDisplayed()
        }

    @Test
    fun clickableCardTriggersCallback() =
        runComposeUiTest {
            var clicked = false
            setContent {
                InfraMapTheme {
                    InfraMapCard(onClick = { clicked = true }) {
                        Text("Clickable Card")
                    }
                }
            }
            onNodeWithText("Clickable Card").performClick()
            assertEquals(true, clicked)
        }

    @Test
    fun nonClickableCardRendersWithoutClick() =
        runComposeUiTest {
            setContent {
                InfraMapTheme {
                    InfraMapCard(onClick = null) {
                        Text("Static Card")
                    }
                }
            }
            onNodeWithText("Static Card").assertIsDisplayed()
        }
}
