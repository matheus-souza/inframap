package com.inframap.frontend.designsystem

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.inframap.frontend.designsystem.motion.m3Clickable

private val SuggestionCornerRadius = 8.dp
private val SuggestionHorizontalPadding = 12.dp
private val SuggestionVerticalPadding = 8.dp

/**
 * One value the operator can take to fill the field below — a detected interface, a
 * registered subnet — as a small two-line card.
 *
 * There is exactly one of these on purpose. The previous round unified the *container* that
 * holds suggestions but left each screen with its own item: one built outlined pills, the
 * other a full-width slab, and within a single release they had drifted apart again in shape,
 * height and colour. A shared container with per-screen items is not a shared pattern.
 *
 * It sizes to its content rather than filling the width, so several suggestions flow beside
 * each other instead of stacking into a wall of bars.
 *
 * The colours step *up* from whatever holds them: `surfaceContainerHigh` against the recessed
 * `surfaceContainerLow` of [CollapsibleSection]. The inverse — a child darker than its parent
 * — is what made the block read as broken, since Material's elevation says nearer surfaces
 * are lighter.
 */
@Composable
fun SuggestionCard(
    title: String,
    detail: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    isSelected: Boolean = false,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val colors = suggestionCardColors(isSelected)

    Surface(
        onClick = onClick,
        modifier =
            modifier
                .m3Clickable(interactionSource)
                .semantics {
                    role = Role.Button
                    selected = isSelected
                },
        color = colors.container,
        border = BorderStroke(1.dp, colors.border),
        shape = RoundedCornerShape(SuggestionCornerRadius),
        interactionSource = interactionSource,
    ) {
        Column(
            modifier =
                Modifier.padding(
                    horizontal = SuggestionHorizontalPadding,
                    vertical = SuggestionVerticalPadding,
                ),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = colors.title,
            )
            Text(
                text = detail,
                style = MaterialTheme.typography.bodySmall,
                fontFamily = FontFamily.Monospace,
                color = colors.detail,
            )
        }
    }
}

private data class SuggestionCardColors(
    val container: Color,
    val border: Color,
    val title: Color,
    val detail: Color,
)

@Composable
private fun suggestionCardColors(isSelected: Boolean): SuggestionCardColors =
    if (isSelected) {
        SuggestionCardColors(
            container = MaterialTheme.colorScheme.primaryContainer,
            border = MaterialTheme.colorScheme.primary,
            title = MaterialTheme.colorScheme.onPrimaryContainer,
            detail = MaterialTheme.colorScheme.onPrimaryContainer,
        )
    } else {
        SuggestionCardColors(
            container = MaterialTheme.colorScheme.surfaceContainerHigh,
            border = MaterialTheme.colorScheme.outlineVariant,
            title = MaterialTheme.colorScheme.onSurface,
            detail = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
