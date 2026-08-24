package com.inframap.frontend.ui.command

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.Modifier

@JsFun(
    """
function(callback) {
    var handler = function(e) {
        if ((e.metaKey || e.ctrlKey) && (e.key === 'k' || e.key === 'K')) {
            e.preventDefault();
            e.stopPropagation();
            callback();
        }
    };
    window.addEventListener('keydown', handler);
    return handler;
}
""",
)
private external fun jsAddGlobalShortcutListener(callback: () -> Unit): JsAny

@JsFun("function(handler) { window.removeEventListener('keydown', handler); }")
private external fun jsRemoveGlobalShortcutListener(handler: JsAny)

@Composable
actual fun CommandPaletteListener(
    onTogglePalette: () -> Unit,
    modifier: Modifier,
    content: @Composable () -> Unit,
) {
    DisposableEffect(onTogglePalette) {
        val handler = jsAddGlobalShortcutListener(onTogglePalette)
        onDispose {
            jsRemoveGlobalShortcutListener(handler)
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        content()
    }
}
