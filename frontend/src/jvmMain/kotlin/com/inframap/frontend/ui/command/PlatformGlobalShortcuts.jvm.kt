package com.inframap.frontend.ui.command

import androidx.compose.runtime.Composable

@Composable
actual fun PlatformGlobalShortcuts(onToggleCommandPalette: () -> Unit) {
    // Handled via onPreviewKeyEvent on Desktop JVM
}
