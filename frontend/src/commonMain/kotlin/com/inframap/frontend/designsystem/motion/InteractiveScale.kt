package com.inframap.frontend.designsystem.motion

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon

const val DEFAULT_PRESS_SCALE: Float = 0.96f
const val DEFAULT_HOVER_SCALE: Float = 1.015f
const val SPRING_DAMPING_RATIO: Float = 0.7f
val SPRING_STIFFNESS: Float = Spring.StiffnessMedium

fun Modifier.m3InteractiveScale(
    interactionSource: MutableInteractionSource,
    pressScale: Float = DEFAULT_PRESS_SCALE,
    hoverScale: Float = DEFAULT_HOVER_SCALE,
): Modifier =
    composed {
        val isPressed by interactionSource.collectIsPressedAsState()
        val isHovered by interactionSource.collectIsHoveredAsState()

        val targetScale =
            when {
                isPressed -> pressScale
                isHovered -> hoverScale
                else -> 1f
            }

        val animatedScale by animateFloatAsState(
            targetValue = targetScale,
            animationSpec =
                spring(
                    dampingRatio = SPRING_DAMPING_RATIO,
                    stiffness = SPRING_STIFFNESS,
                ),
            label = "m3_interactive_scale",
        )

        this.graphicsLayer {
            scaleX = animatedScale
            scaleY = animatedScale
        }
    }

/**
 * The interaction affordances every clickable surface should carry: the press and hover
 * scale, plus a hand cursor.
 *
 * The cursor half exists because the app wraps its content in a `SelectionContainer`, so
 * text keeps the I-beam it needs to be selectable — and a button labelled with text
 * inherited that I-beam, reading as "select me" rather than "click me".
 *
 * [overrideDescendants] is what makes that work, and it is not optional. `pointerHoverIcon`
 * defaults to letting the innermost declaration win, so the `Text` inside the button beats
 * the hand declared on the button itself: the border showed a hand and the label showed a
 * caret, which is exactly the bug this modifier was introduced to fix and did not. The flag
 * governs the cursor icon only — text inside remains selectable.
 *
 * Prefer this over calling [m3InteractiveScale] directly: a component that only scales is a
 * component whose cursor someone forgot.
 */
fun Modifier.m3Clickable(
    interactionSource: MutableInteractionSource,
    pressScale: Float = DEFAULT_PRESS_SCALE,
    hoverScale: Float = DEFAULT_HOVER_SCALE,
): Modifier =
    this
        .m3InteractiveScale(interactionSource, pressScale = pressScale, hoverScale = hoverScale)
        .pointerHoverIcon(PointerIcon.Hand, overrideDescendants = true)

/**
 * The hand cursor on its own, for clickables that deliberately opt out of the scale — a
 * whole table row, for instance, where scaling the row would shift the rows around it.
 *
 * Carries the same [overrideDescendants] reasoning as [m3Clickable]: on a clickable row the
 * hand has to win over every cell's text, or the affordance only exists in the gaps between
 * the columns.
 */
fun Modifier.m3ClickableCursor(): Modifier = this.pointerHoverIcon(PointerIcon.Hand, overrideDescendants = true)
