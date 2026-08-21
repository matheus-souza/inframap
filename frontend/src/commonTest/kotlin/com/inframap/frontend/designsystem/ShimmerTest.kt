package com.inframap.frontend.designsystem

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.dp
import kotlin.test.Test

class ShimmerTest {
    @OptIn(ExperimentalTestApi::class)
    @Test
    fun testShimmerPlaceholder_isVisible() =
        runComposeUiTest {
            setContent {
                Box(
                    modifier =
                        Modifier
                            .size(100.dp)
                            .shimmerPlaceholder(visible = true)
                            .testTag("shimmer_box"),
                )
            }
            onNodeWithTag("shimmer_box").assertExists()
        }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun testShimmerPlaceholder_isNotVisible() =
        runComposeUiTest {
            setContent {
                Box(
                    modifier =
                        Modifier
                            .size(100.dp)
                            .shimmerPlaceholder(visible = false)
                            .testTag("shimmer_box_invisible"),
                )
            }
            onNodeWithTag("shimmer_box_invisible").assertExists()
        }
}
