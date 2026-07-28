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
}
