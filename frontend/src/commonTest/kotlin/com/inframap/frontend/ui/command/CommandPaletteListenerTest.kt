@file:OptIn(ExperimentalComposeUiApi::class, InternalComposeUiApi::class)

package com.inframap.frontend.ui.command

import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.InternalComposeUiApi
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CommandPaletteListenerTest {
    @Test
    fun isCommandPaletteShortcutDetectsMetaKAndCtrlK() {
        val metaKEvent = KeyEvent(key = Key.K, type = KeyEventType.KeyDown, isMetaPressed = true)
        assertTrue(isCommandPaletteShortcut(metaKEvent))

        val ctrlKEvent = KeyEvent(key = Key.K, type = KeyEventType.KeyDown, isCtrlPressed = true)
        assertTrue(isCommandPaletteShortcut(ctrlKEvent))

        val kWithoutModifier = KeyEvent(key = Key.K, type = KeyEventType.KeyDown)
        assertFalse(isCommandPaletteShortcut(kWithoutModifier))

        val metaAEvent = KeyEvent(key = Key.A, type = KeyEventType.KeyDown, isMetaPressed = true)
        assertFalse(isCommandPaletteShortcut(metaAEvent))
    }

    @Test
    fun isEscapeShortcutDetectsEscape() {
        val escapeEvent = KeyEvent(key = Key.Escape, type = KeyEventType.KeyDown)
        assertTrue(isEscapeShortcut(escapeEvent))

        val enterEvent = KeyEvent(key = Key.Enter, type = KeyEventType.KeyDown)
        assertFalse(isEscapeShortcut(enterEvent))
    }
}
