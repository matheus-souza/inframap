package com.inframap.frontend.designsystem

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.dp
import org.junit.jupiter.api.Test

@OptIn(ExperimentalTestApi::class)
class ShimmerTest {
    @Test
    fun m3ShimmerRendersWithoutCrashing() =
        runComposeUiTest {
            setContent {
                InfraMapTheme {
                    Box(
                        modifier =
                            Modifier
                                .size(100.dp)
                                .m3Shimmer()
                                .testTag("m3_shimmer_box"),
                    )
                }
            }
            onNodeWithTag("m3_shimmer_box").assertExists()
        }

    @Test
    fun m3ShimmerInvisibleRendersWithoutCrashing() =
        runComposeUiTest {
            setContent {
                InfraMapTheme {
                    Box(
                        modifier =
                            Modifier
                                .size(100.dp)
                                .m3Shimmer(visible = false)
                                .testTag("m3_shimmer_invisible"),
                    )
                }
            }
            onNodeWithTag("m3_shimmer_invisible").assertExists()
        }

    @Test
    fun m3ShimmerWithCustomColorsAndDuration() =
        runComposeUiTest {
            setContent {
                InfraMapTheme {
                    Box(
                        modifier =
                            Modifier
                                .size(120.dp)
                                .m3Shimmer(
                                    shape = RoundedCornerShape(4.dp),
                                    baseColor = Color(0xFF1E1D24),
                                    highlightColor = Color(0xFF28272F),
                                    durationMillis = 1500,
                                ).testTag("m3_shimmer_custom"),
                    )
                }
            }
            onNodeWithTag("m3_shimmer_custom").assertExists()
        }

    @Test
    fun m3PulseSkeletonRendersWithoutCrashing() =
        runComposeUiTest {
            setContent {
                InfraMapTheme {
                    Box(
                        modifier =
                            Modifier
                                .size(100.dp)
                                .m3PulseSkeleton()
                                .testTag("m3_pulse_box"),
                    )
                }
            }
            onNodeWithTag("m3_pulse_box").assertExists()
        }

    @Test
    fun m3PulseSkeletonInvisibleRendersWithoutCrashing() =
        runComposeUiTest {
            setContent {
                InfraMapTheme {
                    Box(
                        modifier =
                            Modifier
                                .size(100.dp)
                                .m3PulseSkeleton(visible = false)
                                .testTag("m3_pulse_invisible"),
                    )
                }
            }
            onNodeWithTag("m3_pulse_invisible").assertExists()
        }

    @Test
    fun m3PulseSkeletonWithCustomParameters() =
        runComposeUiTest {
            setContent {
                InfraMapTheme {
                    Box(
                        modifier =
                            Modifier
                                .size(100.dp)
                                .m3PulseSkeleton(
                                    shape = RoundedCornerShape(12.dp),
                                    color = Color(0xFF28272F),
                                    minAlpha = 0.2f,
                                    maxAlpha = 0.8f,
                                    durationMillis = 600,
                                ).testTag("m3_pulse_custom"),
                    )
                }
            }
            onNodeWithTag("m3_pulse_custom").assertExists()
        }

    @Test
    fun shimmerPlaceholderLegacyAliasWorks() =
        runComposeUiTest {
            setContent {
                InfraMapTheme {
                    Box(
                        modifier =
                            Modifier
                                .size(100.dp)
                                .shimmerPlaceholder()
                                .testTag("legacy_shimmer_box"),
                    )
                }
            }
            onNodeWithTag("legacy_shimmer_box").assertExists()
        }

    @Test
    fun m3PulseSkeletonThrowsOnInvalidMinAlpha() {
        org.junit.jupiter.api.assertThrows<IllegalArgumentException> {
            Modifier.m3PulseSkeleton(minAlpha = -0.1f)
        }
    }

    @Test
    fun m3PulseSkeletonThrowsOnInvalidMaxAlpha() {
        org.junit.jupiter.api.assertThrows<IllegalArgumentException> {
            Modifier.m3PulseSkeleton(maxAlpha = 1.5f)
        }
    }

    @Test
    fun m3PulseSkeletonThrowsWhenMinAlphaGreaterThanMaxAlpha() {
        org.junit.jupiter.api.assertThrows<IllegalArgumentException> {
            Modifier.m3PulseSkeleton(minAlpha = 0.8f, maxAlpha = 0.2f)
        }
    }
}
