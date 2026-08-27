package com.inframap.frontend.designsystem.motion

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.runComposeUiTest
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test

@OptIn(ExperimentalTestApi::class)
class ModalMotionTransitionsTest {
    @Test
    fun dialogTransitionsAreNotNull() {
        val enter = MotionTransitions.dialogEnter()
        val exit = MotionTransitions.dialogExit()
        val scrimEnter = MotionTransitions.dialogScrimEnter()
        val scrimExit = MotionTransitions.dialogScrimExit()

        assertNotNull(enter)
        assertNotNull(exit)
        assertNotNull(scrimEnter)
        assertNotNull(scrimExit)
    }

    @Test
    fun dialogEnterAndExitAnimatesVisibility() =
        runComposeUiTest {
            var isVisible by mutableStateOf(true)

            setContent {
                AnimatedVisibility(
                    visible = isVisible,
                    enter = MotionTransitions.dialogEnter(),
                    exit = MotionTransitions.dialogExit(),
                ) {
                    Text("Modal Container Content")
                }
            }

            onNodeWithText("Modal Container Content").assertIsDisplayed()

            isVisible = false
            waitForIdle()
        }

    @Test
    fun scrimEnterAndExitAnimatesVisibility() =
        runComposeUiTest {
            var isVisible by mutableStateOf(true)

            setContent {
                AnimatedVisibility(
                    visible = isVisible,
                    enter = MotionTransitions.dialogScrimEnter(),
                    exit = MotionTransitions.dialogScrimExit(),
                ) {
                    Text("Modal Scrim Backdrop")
                }
            }

            onNodeWithText("Modal Scrim Backdrop").assertIsDisplayed()

            isVisible = false
            waitForIdle()
        }
}
