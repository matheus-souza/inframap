package com.inframap.frontend

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.CanvasBasedWindow

private val InfraMapPurple = Color(0xFFbd93f9)
private val InfraMapCyan = Color(0xFF8be9fd)
private val InfraMapBackground = Color(0xFF1e1f29)
private val InfraMapSurface = Color(0xFF282a36)
private val InfraMapForeground = Color(0xFFf8f8f2)
private val InfraMapRed = Color(0xFFff5555)

private val InfraMapColorScheme = darkColorScheme(
    primary = InfraMapPurple,
    secondary = InfraMapCyan,
    background = InfraMapBackground,
    surface = InfraMapSurface,
    error = InfraMapRed,
    onPrimary = InfraMapBackground,
    onSecondary = InfraMapBackground,
    onBackground = InfraMapForeground,
    onSurface = InfraMapForeground,
)

@OptIn(ExperimentalComposeUiApi::class)
fun main() {
    CanvasBasedWindow(canvasElementId = "inframap-canvas", title = "InfraMap") {
        InfraMapApp()
    }
}

@Composable
fun InfraMapApp() {
    MaterialTheme(colorScheme = InfraMapColorScheme) {
        Surface(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = "InfraMap",
                    style = MaterialTheme.typography.headlineLarge,
                    color = MaterialTheme.colorScheme.primary,
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Infrastructure Discovery & Mapping",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
                )
            }
        }
    }
}
