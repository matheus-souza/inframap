package com.inframap.frontend.ui.command

import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isMetaPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type

fun isCommandPaletteShortcut(event: KeyEvent): Boolean =
    event.type == KeyEventType.KeyDown &&
        event.key == Key.K &&
        (event.isMetaPressed || event.isCtrlPressed)

fun isEscapeShortcut(event: KeyEvent): Boolean = event.type == KeyEventType.KeyDown && event.key == Key.Escape

@Composable
fun CommandPaletteListener(
    onTogglePalette: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val focusRequester = remember { FocusRequester() }

    Box(
        modifier =
            modifier
                .focusRequester(focusRequester)
                .focusable()
                .onPreviewKeyEvent { event ->
                    if (isCommandPaletteShortcut(event)) {
                        onTogglePalette()
                        true
                    } else {
                        false
                    }
                },
    ) {
        content()
    }

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }
}
