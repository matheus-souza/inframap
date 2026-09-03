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
import com.inframap.frontend.designsystem.motion.m3ClickableCursor
import com.inframap.frontend.generated.resources.Res
import com.inframap.frontend.generated.resources.app_name
import com.inframap.frontend.generated.resources.topbar_restart_tour
import com.inframap.frontend.generated.resources.topbar_restart_tour_button_cd
import com.inframap.frontend.generated.resources.topbar_search_button_cd
import com.inframap.frontend.generated.resources.topbar_search_icon_cd
import com.inframap.frontend.generated.resources.topbar_search_placeholder
import com.inframap.frontend.generated.resources.topbar_search_shortcut_cd
import com.inframap.frontend.generated.resources.topbar_search_shortcut_icon_cd
import com.inframap.frontend.generated.resources.topbar_search_tooltip
import com.inframap.frontend.generated.resources.topbar_sse_disconnected
import com.inframap.frontend.generated.resources.topbar_sse_live
import com.inframap.frontend.generated.resources.topbar_sse_status_connected
import com.inframap.frontend.generated.resources.topbar_sse_status_disconnected
import com.inframap.frontend.generated.resources.topbar_sse_tooltip_active
import com.inframap.frontend.generated.resources.topbar_sse_tooltip_disconnected
import com.inframap.frontend.generated.resources.topbar_system_healthy
import com.inframap.frontend.generated.resources.topbar_system_unhealthy
import com.inframap.frontend.generated.resources.topbar_tour_icon_cd
import org.jetbrains.compose.resources.stringResource

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
    val appName = stringResource(Res.string.app_name)
    val healthyDescription = stringResource(Res.string.topbar_system_healthy)
    val unhealthyDescription = stringResource(Res.string.topbar_system_unhealthy)
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
                text = appName,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
            )
        } else {
            Text(
                text = appName,
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.primary,
            )
        }
        if (isHealthy != null) {
            Spacer(modifier = Modifier.width(8.dp))
            val dotColor = if (isHealthy) InfraMapEmeraldGreen else InfraMapRubyRed
            val description = if (isHealthy) healthyDescription else unhealthyDescription
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
    val buttonCd = stringResource(Res.string.topbar_restart_tour_button_cd)
    val tourIconCd = stringResource(Res.string.topbar_tour_icon_cd)
    val buttonText = stringResource(Res.string.topbar_restart_tour)
    Surface(
        onClick = onRestartTourClicked,
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)),
        modifier = Modifier.m3ClickableCursor().semantics { contentDescription = buttonCd },
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
        ) {
            Icon(
                imageVector = Icons.Default.Explore,
                contentDescription = tourIconCd,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(16.dp),
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = buttonText,
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
    val searchButtonCd = stringResource(Res.string.topbar_search_button_cd)
    val searchIconCd = stringResource(Res.string.topbar_search_icon_cd)
    val searchPlaceholder = stringResource(Res.string.topbar_search_placeholder)
    val searchTooltip = stringResource(Res.string.topbar_search_tooltip)

    Box {
        Surface(
            onClick = onSearchClicked,
            shape = RoundedCornerShape(8.dp),
            color = MaterialTheme.colorScheme.surfaceVariant,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)),
            modifier =
                Modifier
                    .m3ClickableCursor()
                    .hoverable(interactionSource)
                    .semantics { contentDescription = searchButtonCd },
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            ) {
                Icon(
                    imageVector = Icons.Default.Search,
                    contentDescription = searchIconCd,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(16.dp),
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = searchPlaceholder,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(modifier = Modifier.width(10.dp))
                SearchShortcutBadge()
            }
        }
        if (isHovered) {
            TopBarButtonHoverTooltip(
                text = searchTooltip,
                density = density,
            )
        }
    }
}

@Composable
private fun SearchShortcutBadge() {
    val shortcutCd = stringResource(Res.string.topbar_search_shortcut_cd)
    val shortcutIconCd = stringResource(Res.string.topbar_search_shortcut_icon_cd)
    Surface(
        shape = RoundedCornerShape(4.dp),
        color = MaterialTheme.colorScheme.surface,
        modifier =
            Modifier
                .padding(2.dp)
                .semantics { contentDescription = shortcutCd },
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp),
        ) {
            Icon(
                imageVector = Icons.Default.Key,
                contentDescription = shortcutIconCd,
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

    val sseDescription =
        if (isSseConnected) {
            stringResource(Res.string.topbar_sse_status_connected)
        } else {
            stringResource(Res.string.topbar_sse_status_disconnected)
        }
    val statusTooltip =
        if (isSseConnected) {
            stringResource(Res.string.topbar_sse_tooltip_active)
        } else {
            stringResource(Res.string.topbar_sse_tooltip_disconnected)
        }

    Box {
        SseBadgeContent(
            isSseConnected = isSseConnected,
            pulseAlpha = pulseAlpha,
            sseDescription = sseDescription,
            interactionSource = interactionSource,
        )
        if (isHovered) {
            TopBarButtonHoverTooltip(text = statusTooltip, density = density)
        }
    }
}

@Composable
private fun SseBadgeContent(
    isSseConnected: Boolean,
    pulseAlpha: Float,
    sseDescription: String,
    interactionSource: MutableInteractionSource,
) {
    val sseText =
        if (isSseConnected) {
            stringResource(Res.string.topbar_sse_live)
        } else {
            stringResource(Res.string.topbar_sse_disconnected)
        }
    val dotColor = if (isSseConnected) InfraMapEmeraldGreen else InfraMapRubyRed

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
                text = sseText,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurface,
            )
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
                modifier = Modifier.semantics { contentDescription = text },
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
