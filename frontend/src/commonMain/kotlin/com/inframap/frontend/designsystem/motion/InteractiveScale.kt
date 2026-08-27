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
