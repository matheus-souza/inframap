package com.inframap.frontend.designsystem

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp

private const val DEFAULT_SHIMMER_DURATION = 1200
private const val DEFAULT_PULSE_DURATION = 800
private const val SHIMMER_OFFSET_DELTA = 300f
private const val SHIMMER_ANIMATION_TARGET = 1000f

/**
 * Material Design 3 45-degree linear gradient shimmer wave modifier.
 *
 * Animates a linear gradient highlight band traveling along a 45-degree diagonal vector.
 */
fun Modifier.m3Shimmer(
    shape: Shape = RoundedCornerShape(8.dp),
    baseColor: Color = Color.Unspecified,
    highlightColor: Color = Color.Unspecified,
    durationMillis: Int = DEFAULT_SHIMMER_DURATION,
    visible: Boolean = true,
): Modifier =
    composed {
        if (!visible) return@composed this

        val actualBaseColor =
            if (baseColor != Color.Unspecified) {
                baseColor
            } else {
                MaterialTheme.colorScheme.surfaceContainer
            }
        val actualHighlightColor =
            if (highlightColor != Color.Unspecified) {
                highlightColor
            } else {
                MaterialTheme.colorScheme.surfaceContainerHigh
            }

        val transition = rememberInfiniteTransition(label = "m3_shimmer_transition")
        val translateAnim by transition.animateFloat(
            initialValue = 0f,
            targetValue = SHIMMER_ANIMATION_TARGET,
            animationSpec =
                infiniteRepeatable(
                    animation = tween(durationMillis = durationMillis, easing = LinearEasing),
                    repeatMode = RepeatMode.Restart,
                ),
            label = "m3_shimmer_translate_anim",
        )

        val shimmerColors =
            listOf(
                actualBaseColor,
                actualHighlightColor,
                actualBaseColor,
            )

        val brush =
            Brush.linearGradient(
                colors = shimmerColors,
                start = Offset(x = translateAnim - SHIMMER_OFFSET_DELTA, y = translateAnim - SHIMMER_OFFSET_DELTA),
                end = Offset(x = translateAnim, y = translateAnim),
            )

        this
            .clip(shape)
            .background(brush)
    }

/**
 * Material Design 3 pulse skeleton modifier.
 *
 * Smoothly oscillates opacity between [minAlpha] and [maxAlpha] with [LinearEasing].
 */
fun Modifier.m3PulseSkeleton(
    shape: Shape = RoundedCornerShape(8.dp),
    color: Color = Color.Unspecified,
    minAlpha: Float = 0.3f,
    maxAlpha: Float = 0.7f,
    durationMillis: Int = DEFAULT_PULSE_DURATION,
    visible: Boolean = true,
): Modifier =
    composed {
        if (!visible) return@composed this

        val actualColor =
            if (color != Color.Unspecified) {
                color
            } else {
                MaterialTheme.colorScheme.surfaceContainerHigh
            }

        val transition = rememberInfiniteTransition(label = "m3_pulse_transition")
        val alpha by transition.animateFloat(
            initialValue = minAlpha,
            targetValue = maxAlpha,
            animationSpec =
                infiniteRepeatable(
                    animation = tween(durationMillis = durationMillis, easing = LinearEasing),
                    repeatMode = RepeatMode.Reverse,
                ),
            label = "m3_pulse_alpha_anim",
        )

        this
            .clip(shape)
            .background(actualColor.copy(alpha = alpha))
    }

/**
 * Backward-compatible alias for [m3Shimmer].
 */
fun Modifier.shimmerPlaceholder(
    shape: Shape = RoundedCornerShape(8.dp),
    visible: Boolean = true,
): Modifier = m3Shimmer(shape = shape, visible = visible)
