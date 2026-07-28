package com.inframap.frontend.designsystem

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.runComposeUiTest
import org.junit.jupiter.api.Test

@OptIn(ExperimentalTestApi::class)
class LoadingSkeletonTest {
    @Test
    fun skeletonRendersWithoutCrashing() =
        runComposeUiTest {
            setContent {
                InfraMapTheme {
                    InfraMapLoadingSkeleton(lines = 3)
                }
            }
            waitForIdle()
        }

    @Test
    fun skeletonRendersWithCustomLineCount() =
        runComposeUiTest {
            setContent {
                InfraMapTheme {
                    InfraMapLoadingSkeleton(lines = 5)
                }
            }
            waitForIdle()
        }

    @Test
    fun skeletonRendersWithSingleLine() =
        runComposeUiTest {
            setContent {
                InfraMapTheme {
                    InfraMapLoadingSkeleton(lines = 1)
                }
            }
            waitForIdle()
        }
}
