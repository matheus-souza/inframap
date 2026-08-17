@file:Suppress("MatchingDeclarationName")

package com.inframap.frontend.designsystem

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup

data class NavRailItem(
    val label: String,
    val icon: ImageVector,
    val route: String,
)

@Composable
fun InfraMapNavRail(
    items: List<NavRailItem>,
    selectedRoute: String,
    onItemSelected: (String) -> Unit,
    isExpanded: Boolean = true,
    onToggleExpanded: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val railWidth = if (isExpanded) 200.dp else 56.dp

    Surface(
        modifier = modifier.width(railWidth).fillMaxHeight(),
        color = MaterialTheme.colorScheme.surface,
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = if (isExpanded) Alignment.Start else Alignment.CenterHorizontally,
        ) {
            NavRailToggleButton(isExpanded = isExpanded, onToggle = onToggleExpanded)
            items.forEach { item ->
                val isSelected = item.route == selectedRoute
                if (isExpanded) {
                    ExpandedNavRailItem(
                        item = item,
                        isSelected = isSelected,
                        onClick = { onItemSelected(item.route) },
                    )
                } else {
                    SlimNavRailItem(
                        item = item,
                        isSelected = isSelected,
                        onClick = { onItemSelected(item.route) },
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
            }
        }
    }
}

@Composable
private fun NavRailToggleButton(
    isExpanded: Boolean,
    onToggle: () -> Unit,
) {
    Box(
        modifier =
            Modifier
                .fillMaxWidth()
                .height(48.dp)
                .padding(horizontal = 8.dp),
        contentAlignment = if (isExpanded) Alignment.CenterEnd else Alignment.Center,
    ) {
        IconButton(onClick = onToggle) {
            Icon(
                imageVector =
                    if (isExpanded) {
                        Icons.AutoMirrored.Filled.KeyboardArrowLeft
                    } else {
                        Icons.Filled.Menu
                    },
                contentDescription = if (isExpanded) "Collapse menu" else "Expand menu",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun ExpandedNavRailItem(
    item: NavRailItem,
    isSelected: Boolean,
    onClick: () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isHovered by interactionSource.collectIsHoveredAsState()
    val accentColor = MaterialTheme.colorScheme.primary
    val backgroundColor =
        when {
            isSelected -> accentColor.copy(alpha = 0.15f)
            isHovered -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f)
            else -> Color.Transparent
        }
    val foreground = if (isSelected) accentColor else MaterialTheme.colorScheme.onSurfaceVariant

    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .height(44.dp)
                .padding(horizontal = 8.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(backgroundColor)
                .hoverable(interactionSource)
                .clickable(
                    interactionSource = interactionSource,
                    indication = null,
                    onClick = onClick,
                ).semantics { contentDescription = item.label },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Spacer(modifier = Modifier.width(12.dp))
        ExpandedNavRailItemContent(item = item, isSelected = isSelected, foreground = foreground)
    }
}

@Composable
private fun ExpandedNavRailItemContent(
    item: NavRailItem,
    isSelected: Boolean,
    foreground: Color,
) {
    if (isSelected) {
        Box(
            modifier =
                Modifier
                    .width(3.dp)
                    .height(20.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(MaterialTheme.colorScheme.primary),
        )
        Spacer(modifier = Modifier.width(9.dp))
    }
    Icon(
        imageVector = item.icon,
        contentDescription = null,
        modifier = Modifier.size(20.dp),
        tint = foreground,
    )
    Spacer(modifier = Modifier.width(12.dp))
    Text(
        text = item.label,
        style = MaterialTheme.typography.labelLarge,
        color = if (isSelected) foreground else MaterialTheme.colorScheme.onSurface,
    )
}

@Composable
private fun SlimNavRailItem(
    item: NavRailItem,
    isSelected: Boolean,
    onClick: () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isHovered by interactionSource.collectIsHoveredAsState()
    val density = LocalDensity.current

    Box(
        modifier =
            Modifier
                .fillMaxWidth()
                .height(56.dp)
                .hoverable(interactionSource)
                .clickable(
                    interactionSource = interactionSource,
                    indication = null,
                    onClick = onClick,
                ).semantics { contentDescription = item.label },
        contentAlignment = Alignment.Center,
    ) {
        if (isSelected) {
            NavRailActiveIndicator()
        }
        NavRailIconContainer(item = item, isSelected = isSelected)
        if (isHovered) {
            NavRailHoverTooltip(item = item, density = density)
        }
    }
}

@Composable
private fun BoxScope.NavRailActiveIndicator() {
    Box(
        modifier =
            Modifier
                .align(Alignment.CenterStart)
                .width(4.dp)
                .height(24.dp)
                .clip(RoundedCornerShape(topEnd = 2.dp, bottomEnd = 2.dp))
                .background(MaterialTheme.colorScheme.primary),
    )
}

@Composable
private fun NavRailIconContainer(
    item: NavRailItem,
    isSelected: Boolean,
) {
    Box(
        modifier =
            Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(
                    if (isSelected) {
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                    } else {
                        Color.Transparent
                    },
                ),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = item.icon,
            contentDescription = item.label,
            tint =
                if (isSelected) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
        )
    }
}

@Composable
private fun NavRailHoverTooltip(
    item: NavRailItem,
    density: androidx.compose.ui.unit.Density,
) {
    val offsetX = with(density) { 60.dp.roundToPx() }
    Popup(
        alignment = Alignment.CenterStart,
        offset = IntOffset(x = offsetX, y = 0),
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
                modifier = Modifier.semantics { contentDescription = "${item.label} tooltip" },
            ) {
                Text(
                    text = item.label,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                )
            }
        }
    }
}
