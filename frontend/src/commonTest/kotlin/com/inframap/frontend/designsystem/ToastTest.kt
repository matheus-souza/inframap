package com.inframap.frontend.designsystem

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.runComposeUiTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

class ToastTest {
    @BeforeTest
    fun setup() {
        InfraMapToastManager.clear()
    }

    @Test
    fun testToastManager_showToast() {
        InfraMapToastManager.showToast("Test Toast", ToastType.INFO, 3000L)

        val toasts = InfraMapToastManager.toasts.value
        assertEquals(1, toasts.size)
        assertEquals("Test Toast", toasts.last().message)
        assertEquals(ToastType.INFO, toasts.last().type)
        assertEquals(3000L, toasts.last().durationMs)
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun testToastHost_rendersToast() =
        runComposeUiTest {
            setContent {
                InfraMapToastHost()
            }

            InfraMapToastManager.showToast("UI Test Toast", ToastType.SUCCESS, 10000L)

            waitForIdle()
            onNodeWithText("UI Test Toast").assertExists()
        }
}
