package com.inframap.frontend.designsystem

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.DisableSelection
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

private val TooltipCornerRadius = 8.dp

/**
 * The explanation that hangs off a control on hover.
 *
 * Three Material defaults had to be overridden, each visible on screen. `PlainTooltip`'s
 * default colours are `inverseSurface`/`inverseOnSurface`, which in a dark theme is a light
 * box belonging to no other surface in the app. `caretSize` gives it the pointer that ties
 * it to the control it describes. And `rememberTooltipState` defaults to `isPersistent = false`,
 * which hides the tooltip after roughly a second and a half even while the pointer is still
 * resting on the control — the reader was being timed out mid-sentence.
 *
 * Persistent removes only that timeout. `TooltipBox` still dismisses on the pointer's Exit
 * event, and the fade out is its own `animateTooltip`.
 *
 * `focusable = false` is the fourth override and the one that is not cosmetic. `TooltipBox`
 * defaults it to `true`, which renders the tooltip in a Popup that takes focus and
 * intercepts pointer input. Combined with the persistence above — which is what stops the
 * popup from timing itself out — clicking a chip left that popup standing and the whole
 * screen stopped accepting clicks. A tooltip explains; it must never take focus.
 * The JVM test harness does not reproduce that freeze: `performClick` on the anchor dismisses
 * the tooltip there, so the blocking state never forms and any assertion written around it
 * passes with or without this argument. It is verified by clicking chips in a running build.
 *
 * `DisableSelection` is the fifth override. When the root container uses `SelectionContainer`,
 * the `Text` inside the popup inherits `LocalSelectionRegistrar`. Clicking a control while the
 * tooltip is visible causes `SelectionManager` to evaluate cross-hierarchy coordinates,
 * throwing `IllegalArgumentException: layouts are not part of the same hierarchy` (CMP-9161).
 * Disabling selection on the tooltip popup prevents registration with the outer window.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InfraMapTooltip(
    tooltip: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    TooltipBox(
        positionProvider = TooltipDefaults.rememberPlainTooltipPositionProvider(),
        tooltip = {
            DisableSelection {
                PlainTooltip(
                    caretSize = TooltipDefaults.caretSize,
                    shape = RoundedCornerShape(TooltipCornerRadius),
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    contentColor = MaterialTheme.colorScheme.onSurface,
                    content = tooltip,
                )
            }
        },
        state = rememberTooltipState(isPersistent = true),
        modifier = modifier,
        focusable = false,
        content = content,
    )
}
