@file:Suppress("ktlint:standard:property-naming", "MayBeConst")

package com.inframap.frontend.designsystem.motion

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.SpringSpec
import androidx.compose.animation.core.TweenSpec
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween

/**
 * Material Design 3 Motion Tokens for InfraMap.
 *
 * Implements M3 Easing Curves, Duration Scale, Spring Physics, and standard Tween Animation Specs.
 */
object MotionTokens {
    /**
     * Material Design 3 Easing curves.
     */
    object EasingTokens {
        /** Standard emphasized curve for dominant transitions (0.2, 0.0, 0.0, 1.0). */
        val Emphasized = CubicBezierEasing(0.2f, 0.0f, 0.0f, 1.0f)

        /** Emphasized decelerate curve for incoming / entering elements (0.05, 0.7, 0.1, 1.0). */
        val EmphasizedDecelerate = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1.0f)

        /** Emphasized accelerate curve for outgoing / exiting elements (0.3, 0.0, 0.8, 0.15). */
        val EmphasizedAccelerate = CubicBezierEasing(0.3f, 0.0f, 0.8f, 0.15f)

        /** Standard easing curve for standard transitions (0.2, 0.0, 0.0, 1.0). */
        val Standard = CubicBezierEasing(0.2f, 0.0f, 0.0f, 1.0f)

        /** Standard decelerate curve for simple entrances (0.0, 0.0, 0.0, 1.0). */
        val StandardDecelerate = CubicBezierEasing(0.0f, 0.0f, 0.0f, 1.0f)

        /** Standard accelerate curve for simple exits (0.3, 0.0, 1.0, 1.0). */
        val StandardAccelerate = CubicBezierEasing(0.3f, 0.0f, 1.0f, 1.0f)

        /** Linear easing curve (0.0, 0.0, 1.0, 1.0). */
        val Linear = CubicBezierEasing(0.0f, 0.0f, 1.0f, 1.0f)
    }

    /**
     * Material Design 3 Duration scale in milliseconds.
     */
    object DurationTokens {
        const val Short1 = 50
        const val Short2 = 100
        const val Short3 = 150
        const val Short4 = 200

        const val Medium1 = 250
        const val Medium2 = 300
        const val Medium3 = 350
        const val Medium4 = 400

        const val Long1 = 450
        const val Long2 = 500
        const val Long3 = 550
        const val Long4 = 600

        const val ExtraLong1 = 700
        const val ExtraLong2 = 800
        const val ExtraLong3 = 900
        const val ExtraLong4 = 1000

        const val Short = Short4
        const val Medium = Medium2
        const val Long = Long2
        const val ExtraLong = ExtraLong1
    }

    /**
     * Material Design 3 Spring Physics tokens.
     */
    object SpringTokens {
        val Default: SpringSpec<Float> =
            spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMedium)

        val Gentle: SpringSpec<Float> =
            spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessLow)

        val Bouncy: SpringSpec<Float> =
            spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow)

        val Snappy: SpringSpec<Float> =
            spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMediumLow)

        val Stiff: SpringSpec<Float> =
            spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessHigh)

        fun <T> defaultSpring(): SpringSpec<T> =
            spring(
                dampingRatio = Spring.DampingRatioNoBouncy,
                stiffness = Spring.StiffnessMedium,
            )

        fun <T> gentle(): SpringSpec<T> =
            spring(
                dampingRatio = Spring.DampingRatioLowBouncy,
                stiffness = Spring.StiffnessLow,
            )

        fun <T> bouncy(): SpringSpec<T> =
            spring(
                dampingRatio = Spring.DampingRatioMediumBouncy,
                stiffness = Spring.StiffnessLow,
            )

        fun <T> snappy(): SpringSpec<T> =
            spring(
                dampingRatio = Spring.DampingRatioNoBouncy,
                stiffness = Spring.StiffnessMediumLow,
            )

        fun <T> stiff(): SpringSpec<T> =
            spring(
                dampingRatio = Spring.DampingRatioNoBouncy,
                stiffness = Spring.StiffnessHigh,
            )
    }

    /**
     * Material Design 3 standard Tween Animation Specs.
     */
    object Specs {
        fun <T> emphasized(durationMillis: Int = DurationTokens.Medium4): TweenSpec<T> =
            tween(durationMillis = durationMillis, easing = EasingTokens.Emphasized)

        fun <T> emphasizedDecelerate(durationMillis: Int = DurationTokens.Medium4): TweenSpec<T> =
            tween(durationMillis = durationMillis, easing = EasingTokens.EmphasizedDecelerate)

        fun <T> emphasizedAccelerate(durationMillis: Int = DurationTokens.Short4): TweenSpec<T> =
            tween(durationMillis = durationMillis, easing = EasingTokens.EmphasizedAccelerate)

        fun <T> standard(durationMillis: Int = DurationTokens.Medium2): TweenSpec<T> =
            tween(durationMillis = durationMillis, easing = EasingTokens.Standard)

        fun <T> standardDecelerate(durationMillis: Int = DurationTokens.Medium1): TweenSpec<T> =
            tween(durationMillis = durationMillis, easing = EasingTokens.StandardDecelerate)

        fun <T> standardAccelerate(durationMillis: Int = DurationTokens.Short4): TweenSpec<T> =
            tween(durationMillis = durationMillis, easing = EasingTokens.StandardAccelerate)

        fun <T> linear(durationMillis: Int = DurationTokens.Short4): TweenSpec<T> =
            tween(durationMillis = durationMillis, easing = EasingTokens.Linear)
    }
}
