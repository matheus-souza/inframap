package com.inframap.frontend.ui.command

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect

@JsFun(
    """
function(callback) {
    var handler = function(e) {
        if ((e.metaKey || e.ctrlKey) && (e.key === 'k' || e.key === 'K')) {
            e.preventDefault();
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
actual fun PlatformGlobalShortcuts(onToggleCommandPalette: () -> Unit) {
    DisposableEffect(onToggleCommandPalette) {
        val handler = jsAddGlobalShortcutListener(onToggleCommandPalette)
        onDispose {
            jsRemoveGlobalShortcutListener(handler)
        }
    }
}
