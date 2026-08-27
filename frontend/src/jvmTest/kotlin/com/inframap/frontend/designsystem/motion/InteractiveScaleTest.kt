package com.inframap.frontend.designsystem.motion

import androidx.compose.animation.core.Spring
import androidx.compose.foundation.interaction.HoverInteraction
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.dp
import com.inframap.frontend.designsystem.InfraMapTheme
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

@OptIn(ExperimentalTestApi::class)
class InteractiveScaleTest {
    @Test
    fun constantsMatchSpecification() {
        assertEquals(0.96f, DEFAULT_PRESS_SCALE)
        assertEquals(1.015f, DEFAULT_HOVER_SCALE)
        assertEquals(0.7f, SPRING_DAMPING_RATIO)
        assertEquals(Spring.StiffnessMedium, SPRING_STIFFNESS)
    }

    @Test
    fun interactiveScaleRendersContent() =
        runComposeUiTest {
            val interactionSource = MutableInteractionSource()
            setContent {
                InfraMapTheme {
                    Box(
                        modifier =
                            Modifier
                                .size(100.dp)
                                .m3InteractiveScale(interactionSource),
                    ) {
                        Text("Scalable Element")
                    }
                }
            }
            onNodeWithText("Scalable Element").assertIsDisplayed()
        }

    @Test
    fun interactiveScaleHandlesPressAndRelease() =
        runComposeUiTest {
            val interactionSource = MutableInteractionSource()
            setContent {
                InfraMapTheme {
                    Box(
                        modifier =
                            Modifier
                                .size(100.dp)
                                .m3InteractiveScale(interactionSource),
                    ) {
                        Text("Pressed Element")
                    }
                }
            }
            onNodeWithText("Pressed Element").assertIsDisplayed()

            val press = PressInteraction.Press(Offset.Zero)
            runBlocking {
                interactionSource.emit(press)
            }
            waitForIdle()
            onNodeWithText("Pressed Element").assertIsDisplayed()

            runBlocking {
                interactionSource.emit(PressInteraction.Release(press))
            }
            waitForIdle()
            onNodeWithText("Pressed Element").assertIsDisplayed()
        }

    @Test
    fun interactiveScaleHandlesPressAndCancel() =
        runComposeUiTest {
            val interactionSource = MutableInteractionSource()
            setContent {
                InfraMapTheme {
                    Box(
                        modifier =
                            Modifier
                                .size(100.dp)
                                .m3InteractiveScale(interactionSource),
                    ) {
                        Text("Canceled Element")
                    }
                }
            }
            onNodeWithText("Canceled Element").assertIsDisplayed()

            val press = PressInteraction.Press(Offset.Zero)
            runBlocking {
                interactionSource.emit(press)
            }
            waitForIdle()

            runBlocking {
                interactionSource.emit(PressInteraction.Cancel(press))
            }
            waitForIdle()
            onNodeWithText("Canceled Element").assertIsDisplayed()
        }

    @Test
    fun interactiveScaleHandlesHoverEnterAndExit() =
        runComposeUiTest {
            val interactionSource = MutableInteractionSource()
            setContent {
                InfraMapTheme {
                    Box(
                        modifier =
                            Modifier
                                .size(100.dp)
                                .m3InteractiveScale(interactionSource),
                    ) {
                        Text("Hovered Element")
                    }
                }
            }
            onNodeWithText("Hovered Element").assertIsDisplayed()

            val enter = HoverInteraction.Enter()
            runBlocking {
                interactionSource.emit(enter)
            }
            waitForIdle()
            onNodeWithText("Hovered Element").assertIsDisplayed()

            runBlocking {
                interactionSource.emit(HoverInteraction.Exit(enter))
            }
            waitForIdle()
            onNodeWithText("Hovered Element").assertIsDisplayed()
        }

    @Test
    fun interactiveScaleWithCustomScales() =
        runComposeUiTest {
            val interactionSource = MutableInteractionSource()
            setContent {
                InfraMapTheme {
                    Box(
                        modifier =
                            Modifier
                                .size(100.dp)
                                .m3InteractiveScale(
                                    interactionSource = interactionSource,
                                    pressScale = 0.90f,
                                    hoverScale = 1.05f,
                                ),
                    ) {
                        Text("Custom Scaled Element")
                    }
                }
            }
            onNodeWithText("Custom Scaled Element").assertIsDisplayed()

            val press = PressInteraction.Press(Offset.Zero)
            runBlocking {
                interactionSource.emit(press)
            }
            waitForIdle()
            onNodeWithText("Custom Scaled Element").assertIsDisplayed()

            runBlocking {
                interactionSource.emit(PressInteraction.Release(press))
            }
            waitForIdle()
            onNodeWithText("Custom Scaled Element").assertIsDisplayed()
        }
}
