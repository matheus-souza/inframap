package com.inframap.frontend.designsystem.motion

import androidx.compose.animation.core.Spring
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class MotionTokensTest {
    @Test
    fun easingTokensTransformStartAndEndValues() {
        val easings =
            listOf(
                MotionTokens.EasingTokens.Emphasized,
                MotionTokens.EasingTokens.EmphasizedDecelerate,
                MotionTokens.EasingTokens.EmphasizedAccelerate,
                MotionTokens.EasingTokens.Standard,
                MotionTokens.EasingTokens.StandardDecelerate,
                MotionTokens.EasingTokens.StandardAccelerate,
                MotionTokens.EasingTokens.Linear,
            )

        for (easing in easings) {
            assertEquals(0.0f, easing.transform(0.0f), 0.001f)
            assertEquals(1.0f, easing.transform(1.0f), 0.001f)
        }
    }

    @Test
    fun easingCurvesProduceExpectedMidpointProgression() {
        val emphasizedMid = MotionTokens.EasingTokens.Emphasized.transform(0.5f)
        val decelerateMid = MotionTokens.EasingTokens.EmphasizedDecelerate.transform(0.5f)
        val accelerateMid = MotionTokens.EasingTokens.EmphasizedAccelerate.transform(0.5f)
        val linearMid = MotionTokens.EasingTokens.Linear.transform(0.5f)

        assertEquals(0.5f, linearMid, 0.001f)
        assertTrue(decelerateMid > linearMid, "Decelerate should be ahead at midpoint")
        assertTrue(accelerateMid < linearMid, "Accelerate should be behind at midpoint")
        assertTrue(emphasizedMid in 0.0f..1.0f, "Emphasized midpoint must remain normalized")
    }

    @Test
    fun durationTokensScaleMonotonically() {
        val durations =
            listOf(
                MotionTokens.DurationTokens.Short1,
                MotionTokens.DurationTokens.Short2,
                MotionTokens.DurationTokens.Short3,
                MotionTokens.DurationTokens.Short4,
                MotionTokens.DurationTokens.Medium1,
                MotionTokens.DurationTokens.Medium2,
                MotionTokens.DurationTokens.Medium3,
                MotionTokens.DurationTokens.Medium4,
                MotionTokens.DurationTokens.Long1,
                MotionTokens.DurationTokens.Long2,
                MotionTokens.DurationTokens.Long3,
                MotionTokens.DurationTokens.Long4,
                MotionTokens.DurationTokens.ExtraLong1,
                MotionTokens.DurationTokens.ExtraLong2,
                MotionTokens.DurationTokens.ExtraLong3,
                MotionTokens.DurationTokens.ExtraLong4,
            )

        for (i in 0 until durations.size - 1) {
            assertTrue(
                durations[i] < durations[i + 1],
                "Duration token at index $i (${durations[i]}ms) must be strictly less than next (${durations[i + 1]}ms)",
            )
        }
    }

    @Test
    fun defaultDurationShortcutsMatchExpectedTokens() {
        assertEquals(MotionTokens.DurationTokens.Short4, MotionTokens.DurationTokens.Short)
        assertEquals(MotionTokens.DurationTokens.Medium2, MotionTokens.DurationTokens.Medium)
        assertEquals(MotionTokens.DurationTokens.Long2, MotionTokens.DurationTokens.Long)
        assertEquals(MotionTokens.DurationTokens.ExtraLong1, MotionTokens.DurationTokens.ExtraLong)
    }

    @Test
    fun springTokensConfigureDampingAndStiffness() {
        assertEquals(Spring.DampingRatioNoBouncy, MotionTokens.SpringTokens.Default.dampingRatio)
        assertEquals(Spring.StiffnessMedium, MotionTokens.SpringTokens.Default.stiffness)

        assertEquals(Spring.DampingRatioLowBouncy, MotionTokens.SpringTokens.Gentle.dampingRatio)
        assertEquals(Spring.StiffnessLow, MotionTokens.SpringTokens.Gentle.stiffness)

        assertEquals(Spring.DampingRatioMediumBouncy, MotionTokens.SpringTokens.Bouncy.dampingRatio)
        assertEquals(Spring.StiffnessLow, MotionTokens.SpringTokens.Bouncy.stiffness)

        assertEquals(Spring.DampingRatioNoBouncy, MotionTokens.SpringTokens.Snappy.dampingRatio)
        assertEquals(Spring.StiffnessMediumLow, MotionTokens.SpringTokens.Snappy.stiffness)

        assertEquals(Spring.DampingRatioNoBouncy, MotionTokens.SpringTokens.Stiff.dampingRatio)
        assertEquals(Spring.StiffnessHigh, MotionTokens.SpringTokens.Stiff.stiffness)
    }

    @Test
    fun springHelperFunctionsReturnConfiguredSpecs() {
        val gentleSpec = MotionTokens.SpringTokens.gentle<Float>()
        val snappySpec = MotionTokens.SpringTokens.snappy<Float>()
        val bouncySpec = MotionTokens.SpringTokens.bouncy<Float>()
        val stiffSpec = MotionTokens.SpringTokens.stiff<Float>()
        val defaultSpec = MotionTokens.SpringTokens.defaultSpring<Float>()

        assertEquals(Spring.DampingRatioLowBouncy, gentleSpec.dampingRatio)
        assertEquals(Spring.DampingRatioNoBouncy, snappySpec.dampingRatio)
        assertEquals(Spring.DampingRatioMediumBouncy, bouncySpec.dampingRatio)
        assertEquals(Spring.DampingRatioNoBouncy, stiffSpec.dampingRatio)
        assertEquals(Spring.DampingRatioNoBouncy, defaultSpec.dampingRatio)
    }

    @Test
    fun animationSpecsConfigureDurationAndEasing() {
        val emphasizedSpec = MotionTokens.Specs.emphasized<Float>()
        val emphasizedDecelerateSpec = MotionTokens.Specs.emphasizedDecelerate<Float>()
        val emphasizedAccelerateSpec = MotionTokens.Specs.emphasizedAccelerate<Float>()
        val standardSpec = MotionTokens.Specs.standard<Float>()
        val standardDecelerateSpec = MotionTokens.Specs.standardDecelerate<Float>()
        val standardAccelerateSpec = MotionTokens.Specs.standardAccelerate<Float>()
        val linearSpec = MotionTokens.Specs.linear<Float>()

        assertNotNull(emphasizedSpec)
        assertNotNull(emphasizedDecelerateSpec)
        assertNotNull(emphasizedAccelerateSpec)
        assertNotNull(standardSpec)
        assertNotNull(standardDecelerateSpec)
        assertNotNull(standardAccelerateSpec)
        assertNotNull(linearSpec)

        assertEquals(MotionTokens.DurationTokens.Medium4, emphasizedSpec.durationMillis)
        assertEquals(MotionTokens.DurationTokens.Medium4, emphasizedDecelerateSpec.durationMillis)
        assertEquals(MotionTokens.DurationTokens.Short4, emphasizedAccelerateSpec.durationMillis)
        assertEquals(MotionTokens.DurationTokens.Medium2, standardSpec.durationMillis)
        assertEquals(MotionTokens.DurationTokens.Medium1, standardDecelerateSpec.durationMillis)
        assertEquals(MotionTokens.DurationTokens.Short4, standardAccelerateSpec.durationMillis)
        assertEquals(MotionTokens.DurationTokens.Short4, linearSpec.durationMillis)
    }
}
