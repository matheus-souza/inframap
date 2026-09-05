package com.inframap.frontend.designsystem

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.inframap.frontend.designsystem.motion.m3Clickable

private val SectionCornerRadius = 12.dp
private val SectionHeaderPadding = 16.dp
private val SectionIconSize = 18.dp
private val ChevronSize = 20.dp
private const val COLLAPSE_ANIMATION_MILLIS = 250
private const val CHEVRON_COLLAPSED_ROTATION = 0f
private const val CHEVRON_EXPANDED_ROTATION = 180f

/**
 * A titled block that can be folded away, and the one shape the app uses for "here are some
 * values you can pick from to fill the field below".
 *
 * Two screens used to answer that need differently — one painted a fixed card, the other a
 * bare primary-coloured header — so the same offer looked like two unrelated features. This
 * is the single pattern both now use.
 *
 * The container is deliberately not [InfraMapCard]. It paints `surfaceContainerLow`, one step
 * *below* the form card that holds it, so the block reads as a well the suggestions sit in
 * and the cards inside it can step up to `surfaceContainerHigh`. The first attempt did the
 * opposite — a `surfaceContainerHighest` parent with darker children — which inverts what
 * Material's elevation communicates and was the reason the block looked wrong.
 *
 * The chevron rotates rather than swapping icons. Swapping is instant, and an instant swap
 * in the middle of a 250ms container animation is what made the nav rail feel like it
 * snapped shut while opening smoothly.
 *
 * This overload keeps the expanded flag itself. Use the [expanded]/[onToggle] overload when
 * a ViewModel already owns the state.
 */
@Composable
fun CollapsibleSection(
    title: String,
    icon: ImageVector,
    modifier: Modifier = Modifier,
    initiallyExpanded: Boolean = true,
    content: @Composable () -> Unit,
) {
    var expanded by rememberSaveable { mutableStateOf(initiallyExpanded) }

    CollapsibleSection(
        title = title,
        icon = icon,
        expanded = expanded,
        onToggle = { expanded = !expanded },
        modifier = modifier,
        content = content,
    )
}

@Composable
fun CollapsibleSection(
    title: String,
    icon: ImageVector,
    expanded: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Surface(
        modifier = modifier,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        shape = RoundedCornerShape(SectionCornerRadius),
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            CollapsibleSectionHeader(
                title = title,
                icon = icon,
                expanded = expanded,
                onToggle = onToggle,
            )

            val foldSpec = tween<IntSize>(COLLAPSE_ANIMATION_MILLIS, easing = FastOutSlowInEasing)

            AnimatedVisibility(
                visible = expanded,
                enter = expandVertically(animationSpec = foldSpec),
                exit = shrinkVertically(animationSpec = foldSpec),
            ) {
                Column(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(
                                start = SectionHeaderPadding,
                                end = SectionHeaderPadding,
                                bottom = SectionHeaderPadding,
                            ),
                ) {
                    content()
                }
            }
        }
    }
}

@Composable
private fun CollapsibleSectionHeader(
    title: String,
    icon: ImageVector,
    expanded: Boolean,
    onToggle: () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val chevronRotation by animateFloatAsState(
        targetValue = if (expanded) CHEVRON_EXPANDED_ROTATION else CHEVRON_COLLAPSED_ROTATION,
        animationSpec = tween(COLLAPSE_ANIMATION_MILLIS, easing = FastOutSlowInEasing),
        label = "collapsible_section_chevron",
    )

    Surface(
        onClick = onToggle,
        modifier = Modifier.fillMaxWidth().m3Clickable(interactionSource, pressScale = 1f, hoverScale = 1f),
        color = Color.Transparent,
        interactionSource = interactionSource,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(SectionHeaderPadding),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(SectionIconSize),
                tint = MaterialTheme.colorScheme.primary,
            )
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f),
            )
            Icon(
                imageVector = Icons.Filled.KeyboardArrowDown,
                contentDescription = null,
                modifier = Modifier.size(ChevronSize).graphicsLayer { rotationZ = chevronRotation },
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
