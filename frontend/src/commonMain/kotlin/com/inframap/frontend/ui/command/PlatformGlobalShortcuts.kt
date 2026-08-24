package com.inframap.frontend.ui.command

import androidx.compose.runtime.Composable

@Composable
expect fun PlatformGlobalShortcuts(onToggleCommandPalette: () -> Unit)
