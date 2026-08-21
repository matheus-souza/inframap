package com.inframap.frontend.designsystem

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp

fun Modifier.shimmerPlaceholder(
    shape: Shape = RoundedCornerShape(8.dp),
    visible: Boolean = true,
): Modifier =
    composed {
        if (!visible) return@composed this

        val transition = rememberInfiniteTransition(label = "shimmer_transition")
        val translateAnim by transition.animateFloat(
            initialValue = 0f,
            targetValue = 1000f,
            animationSpec =
                infiniteRepeatable(
                    animation = tween(durationMillis = 1200, easing = FastOutSlowInEasing),
                    repeatMode = RepeatMode.Restart,
                ),
            label = "shimmer_translate_anim",
        )

        val shimmerColors =
            listOf(
                surfaceContainerHigh.copy(alpha = 0.6f),
                outlineSubtle.copy(alpha = 0.8f),
                surfaceContainerHigh.copy(alpha = 0.6f),
            )

        val brush =
            Brush.linearGradient(
                colors = shimmerColors,
                start = Offset.Zero,
                end = Offset(x = translateAnim, y = translateAnim),
            )

        this
            .clip(shape)
            .background(brush)
    }
