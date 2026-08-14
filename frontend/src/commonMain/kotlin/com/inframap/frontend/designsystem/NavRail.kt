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
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
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
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.width(56.dp).fillMaxHeight(),
        color = MaterialTheme.colorScheme.surface,
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(modifier = Modifier.height(12.dp))
            items.forEach { item ->
                val isSelected = item.route == selectedRoute
                SlimNavRailItem(
                    item = item,
                    isSelected = isSelected,
                    onClick = { onItemSelected(item.route) },
                )
                Spacer(modifier = Modifier.height(8.dp))
            }
        }
    }
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
