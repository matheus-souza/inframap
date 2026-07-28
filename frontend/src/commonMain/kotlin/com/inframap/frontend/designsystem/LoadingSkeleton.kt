package com.inframap.frontend.designsystem

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

@Composable
fun InfraMapLoadingSkeleton(
    modifier: Modifier = Modifier,
    lines: Int = 3,
    lineHeight: Dp = 16.dp,
    spacing: Dp = 12.dp,
) {
    val transition = rememberInfiniteTransition()
    val alpha by transition.animateFloat(
        initialValue = 0.3f,
        targetValue = 0.7f,
        animationSpec =
            infiniteRepeatable(
                animation = tween(durationMillis = 800, easing = LinearEasing),
                repeatMode = RepeatMode.Reverse,
            ),
    )
    Column(modifier = modifier) {
        repeat(lines) { index ->
            val widthFraction = if (index == lines - 1) 0.6f else 1.0f
            Box(
                modifier =
                    Modifier
                        .fillMaxWidth(widthFraction)
                        .height(lineHeight)
                        .clip(RoundedCornerShape(4.dp))
                        .background(
                            MaterialTheme.colorScheme.onSurface.copy(alpha = alpha),
                        ),
            )
            if (index < lines - 1) {
                Spacer(modifier = Modifier.height(spacing))
            }
        }
    }
}
