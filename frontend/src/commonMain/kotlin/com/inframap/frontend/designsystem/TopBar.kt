package com.inframap.frontend.designsystem

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InfraMapTopBar(
    screenTitle: String? = null,
    isHealthy: Boolean? = null,
    isSseConnected: Boolean = true,
    onSearchClicked: () -> Unit = {},
    onRestartTourClicked: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    TopAppBar(
        modifier = modifier,
        title = { TopBarTitle(screenTitle = screenTitle, isHealthy = isHealthy) },
        colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface),
        actions = {
            TopBarActions(
                isSseConnected = isSseConnected,
                onSearchClicked = onSearchClicked,
                onRestartTourClicked = onRestartTourClicked,
            )
        },
    )
}

@Composable
private fun TopBarTitle(
    screenTitle: String?,
    isHealthy: Boolean?,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        if (screenTitle != null) {
            Text(
                text = screenTitle,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = "·",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = "InfraMap",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
            )
        } else {
            Text(
                text = "InfraMap",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.primary,
            )
        }
        if (isHealthy != null) {
            Spacer(modifier = Modifier.width(8.dp))
            val dotColor = if (isHealthy) InfraMapEmeraldGreen else InfraMapRubyRed
            val description = if (isHealthy) "System healthy" else "System unhealthy"
            Box(
                modifier =
                    Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(dotColor)
                        .semantics { contentDescription = description },
            )
        }
    }
}

@Composable
private fun TopBarActions(
    isSseConnected: Boolean,
    onSearchClicked: () -> Unit,
    onRestartTourClicked: (() -> Unit)? = null,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(end = 16.dp),
    ) {
        if (onRestartTourClicked != null) {
            RestartTourButton(onRestartTourClicked = onRestartTourClicked)
            Spacer(modifier = Modifier.width(12.dp))
        }
        SearchTriggerButton(onSearchClicked = onSearchClicked)
        Spacer(modifier = Modifier.width(12.dp))
        SseConnectionBadge(isSseConnected = isSseConnected)
    }
}

@Composable
private fun RestartTourButton(onRestartTourClicked: () -> Unit) {
    Surface(
        onClick = onRestartTourClicked,
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)),
        modifier = Modifier.semantics { contentDescription = "Refazer Tour Guiado button" },
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
        ) {
            Icon(
                imageVector = Icons.Default.Explore,
                contentDescription = "Tour icon",
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(16.dp),
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = "Refazer Tour Guiado",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

@Composable
private fun SearchTriggerButton(onSearchClicked: () -> Unit) {
    val interactionSource = remember { MutableInteractionSource() }
    val isHovered by interactionSource.collectIsHoveredAsState()
    val density = LocalDensity.current

    Box {
        Surface(
            onClick = onSearchClicked,
            shape = RoundedCornerShape(8.dp),
            color = MaterialTheme.colorScheme.surfaceVariant,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)),
            modifier =
                Modifier
                    .hoverable(interactionSource)
                    .semantics { contentDescription = "Search trigger button" },
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            ) {
                Icon(
                    imageVector = Icons.Default.Search,
                    contentDescription = "Search icon",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(16.dp),
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "Search...",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(modifier = Modifier.width(10.dp))
                SearchShortcutBadge()
            }
        }
        if (isHovered) {
            TopBarButtonHoverTooltip(text = "Search infrastructure (Cmd+K / Ctrl+K)", density = density)
        }
    }
}

@Composable
private fun SearchShortcutBadge() {
    Surface(
        shape = RoundedCornerShape(4.dp),
        color = MaterialTheme.colorScheme.surface,
        modifier =
            Modifier
                .padding(2.dp)
                .semantics { contentDescription = "Search shortcut key K" },
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp),
        ) {
            Icon(
                imageVector = Icons.Default.Key,
                contentDescription = "Key shortcut icon",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(12.dp),
            )
            Spacer(modifier = Modifier.width(2.dp))
            Text(
                text = "K",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun SseConnectionBadge(isSseConnected: Boolean) {
    val interactionSource = remember { MutableInteractionSource() }
    val isHovered by interactionSource.collectIsHoveredAsState()
    val density = LocalDensity.current

    val infiniteTransition = rememberInfiniteTransition(label = "ssePulse")
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.4f,
        targetValue = 1.0f,
        animationSpec =
            infiniteRepeatable(
                animation = tween(durationMillis = 1000),
                repeatMode = RepeatMode.Reverse,
            ),
        label = "pulseAlpha",
    )

    val sseDescription = if (isSseConnected) "SSE Status: Connected" else "SSE Status: Disconnected"

    Box {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)),
            modifier =
                Modifier
                    .hoverable(interactionSource)
                    .semantics { contentDescription = sseDescription },
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
            ) {
                val dotColor = if (isSseConnected) InfraMapEmeraldGreen else InfraMapRubyRed
                Box(
                    modifier =
                        Modifier
                            .size(8.dp)
                            .alpha(if (isSseConnected) pulseAlpha else 1.0f)
                            .clip(CircleShape)
                            .background(dotColor),
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = if (isSseConnected) "Live SSE" else "Disconnected",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
        }
        if (isHovered) {
            val statusTooltip = if (isSseConnected) "Real-time SSE connection active" else "Real-time SSE disconnected"
            TopBarButtonHoverTooltip(text = statusTooltip, density = density)
        }
    }
}

@Composable
private fun TopBarButtonHoverTooltip(
    text: String,
    density: androidx.compose.ui.unit.Density,
) {
    val offsetY = with(density) { 36.dp.roundToPx() }
    Popup(
        alignment = Alignment.TopCenter,
        offset = IntOffset(x = 0, y = offsetY),
    ) {
        AnimatedVisibility(
            visible = true,
            enter = fadeIn(),
            exit = fadeOut(),
        ) {
            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant,
                shape = RoundedCornerShape(6.dp),
                shadowElevation = 6.dp,
                modifier = Modifier.semantics { contentDescription = "$text tooltip" },
            ) {
                Text(
                    text = text,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                )
            }
        }
    }
}
