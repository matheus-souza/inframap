package com.inframap.frontend.designsystem

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.runComposeUiTest
import org.junit.jupiter.api.Test

@OptIn(ExperimentalTestApi::class)
class TopBarTest {
    @Test
    fun topBarRendersTitle() =
        runComposeUiTest {
            setContent {
                InfraMapTheme {
                    InfraMapTopBar(title = "InfraMap")
                }
            }
            onNodeWithText("InfraMap").assertIsDisplayed()
        }

    @Test
    fun topBarRendersCustomTitle() =
        runComposeUiTest {
            setContent {
                InfraMapTheme {
                    InfraMapTopBar(title = "Devices")
                }
            }
            onNodeWithText("Devices").assertIsDisplayed()
        }

    @Test
    fun topBarRendersWithHealthIndicator() =
        runComposeUiTest {
            setContent {
                InfraMapTheme {
                    InfraMapTopBar(title = "InfraMap", isHealthy = true)
                }
            }
            onNodeWithText("InfraMap").assertIsDisplayed()
        }

    @Test
    fun topBarRendersWithUnhealthyIndicator() =
        runComposeUiTest {
            setContent {
                InfraMapTheme {
                    InfraMapTopBar(title = "InfraMap", isHealthy = false)
                }
            }
            onNodeWithText("InfraMap").assertIsDisplayed()
        }

    @Test
    fun topBarRendersSearchBadgeAndSseStatus() =
        runComposeUiTest {
            setContent {
                InfraMapTheme {
                    InfraMapTopBar(title = "InfraMap", isSseConnected = true)
                }
            }
            onNodeWithText("K").assertIsDisplayed()
            onNodeWithText("Live SSE").assertIsDisplayed()
            onNodeWithContentDescription("Search trigger button").assertIsDisplayed()
        }

    @Test
    fun topBarRendersRestartTourButtonWhenCallbackProvided() =
        runComposeUiTest {
            setContent {
                InfraMapTheme {
                    InfraMapTopBar(
                        title = "InfraMap",
                        onRestartTourClicked = {},
                    )
                }
            }
            onNodeWithText("Refazer Tour Guiado").assertIsDisplayed()
            onNodeWithContentDescription("Refazer Tour Guiado button").assertIsDisplayed()
        }
}
