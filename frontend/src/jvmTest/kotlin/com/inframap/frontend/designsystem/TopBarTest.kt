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
    fun topBarRendersDefaultTitle() =
        runComposeUiTest {
            setContent {
                InfraMapTheme {
                    InfraMapTopBar()
                }
            }
            onNodeWithText("InfraMap").assertIsDisplayed()
        }

    @Test
    fun topBarRendersContextualScreenTitle() =
        runComposeUiTest {
            setContent {
                InfraMapTheme {
                    InfraMapTopBar(screenTitle = "Dispositivos")
                }
            }
            onNodeWithText("Dispositivos").assertIsDisplayed()
            onNodeWithText("·").assertIsDisplayed()
            onNodeWithText("InfraMap").assertIsDisplayed()
        }

    @Test
    fun topBarRendersWithHealthIndicator() =
        runComposeUiTest {
            setContent {
                InfraMapTheme {
                    InfraMapTopBar(screenTitle = "Dashboard", isHealthy = true)
                }
            }
            onNodeWithText("Dashboard").assertIsDisplayed()
            onNodeWithText("InfraMap").assertIsDisplayed()
            onNodeWithContentDescription("Sistema saudável").assertIsDisplayed()
        }

    @Test
    fun topBarRendersWithUnhealthyIndicator() =
        runComposeUiTest {
            setContent {
                InfraMapTheme {
                    InfraMapTopBar(screenTitle = null, isHealthy = false)
                }
            }
            onNodeWithText("InfraMap").assertIsDisplayed()
            onNodeWithContentDescription("Sistema não saudável").assertIsDisplayed()
        }

    @Test
    fun topBarRendersSearchBadgeAndSseStatus() =
        runComposeUiTest {
            setContent {
                InfraMapTheme {
                    InfraMapTopBar(isSseConnected = true)
                }
            }
            onNodeWithText("K").assertIsDisplayed()
            onNodeWithText("Live SSE").assertIsDisplayed()
            onNodeWithContentDescription("Botão de busca").assertIsDisplayed()
        }

    @Test
    fun topBarRendersRestartTourButtonWhenCallbackProvided() =
        runComposeUiTest {
            setContent {
                InfraMapTheme {
                    InfraMapTopBar(
                        onRestartTourClicked = {},
                    )
                }
            }
            onNodeWithText("Refazer Tour Guiado").assertIsDisplayed()
            onNodeWithContentDescription("Refazer Tour Guiado").assertIsDisplayed()
        }
}
