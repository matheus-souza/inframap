package com.inframap.frontend.ui.command

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.onPreviewKeyEvent

@Composable
actual fun CommandPaletteListener(
    onTogglePalette: () -> Unit,
    modifier: Modifier,
    content: @Composable () -> Unit,
) {
    Box(
        modifier =
            modifier
                .fillMaxSize()
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
}
