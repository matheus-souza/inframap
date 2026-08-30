package com.inframap.frontend.designsystem

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.runComposeUiTest
import org.junit.jupiter.api.Test

@OptIn(ExperimentalTestApi::class)
class StatusBadgeTest {
    @Test
    fun activeBadgeShowsActiveLabel() =
        runComposeUiTest {
            setContent {
                InfraMapTheme {
                    InfraMapStatusBadge(status = DeviceStatus.ACTIVE)
                }
            }
            onNodeWithText("Active").assertIsDisplayed()
        }

    @Test
    fun offlineBadgeShowsOfflineLabel() =
        runComposeUiTest {
            setContent {
                InfraMapTheme {
                    InfraMapStatusBadge(status = DeviceStatus.OFFLINE)
                }
            }
            onNodeWithText("Offline").assertIsDisplayed()
        }

    @Test
    fun stagedBadgeShowsStagedLabel() =
        runComposeUiTest {
            setContent {
                InfraMapTheme {
                    InfraMapStatusBadge(status = DeviceStatus.STAGED)
                }
            }
            onNodeWithText("Staged").assertIsDisplayed()
        }

    @Test
    fun sourceIdleBadgeShowsIdleLabel() =
        runComposeUiTest {
            setContent {
                InfraMapTheme {
                    InfraMapStatusBadge(status = SourceStatus.IDLE)
                }
            }
            onNodeWithText("Idle").assertIsDisplayed()
        }

    @Test
    fun sourceRunningBadgeShowsRunningLabel() =
        runComposeUiTest {
            setContent {
                InfraMapTheme {
                    InfraMapStatusBadge(status = SourceStatus.RUNNING)
                }
            }
            onNodeWithText("Running").assertIsDisplayed()
        }

    @Test
    fun sourcePartialBadgeShowsPartialLabel() =
        runComposeUiTest {
            setContent {
                InfraMapTheme {
                    InfraMapStatusBadge(status = SourceStatus.PARTIAL)
                }
            }
            onNodeWithText("Partial").assertIsDisplayed()
        }

    @Test
    fun sourceErrorBadgeShowsErrorLabel() =
        runComposeUiTest {
            setContent {
                InfraMapTheme {
                    InfraMapStatusBadge(status = SourceStatus.ERROR)
                }
            }
            onNodeWithText("Error").assertIsDisplayed()
        }

    @Test
    fun sourceCancelledBadgeShowsCancelledLabel() =
        runComposeUiTest {
            setContent {
                InfraMapTheme {
                    InfraMapStatusBadge(status = SourceStatus.CANCELLED)
                }
            }
            onNodeWithText("Cancelled").assertIsDisplayed()
        }
}
