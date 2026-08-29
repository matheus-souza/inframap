package com.inframap.frontend.ui.staging

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runComposeUiTest
import com.inframap.frontend.designsystem.InfraMapTheme
import com.inframap.frontend.domain.model.StagingDevice
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

@OptIn(ExperimentalTestApi::class)
class StagingScreenTest {
    private fun testActions(
        onApproveClicked: (StagingDevice) -> Unit = {},
        onDismissClicked: (StagingDevice) -> Unit = {},
    ) = StagingActions(
        onPageChanged = {},
        onApproveClicked = onApproveClicked,
        onDismissClicked = onDismissClicked,
        onConfirmDismiss = {},
        onCancelDismiss = {},
        onRetryClicked = {},
        onConfigureDiscovery = {},
        onDismissToast = {},
        onDismissActionError = {},
    )

    @Test
    fun rendersEmptyStateWhenNoStagingDevices() =
        runComposeUiTest {
            setContent {
                InfraMapTheme {
                    StagingScreen(
                        state = StagingUiState(devices = emptyList(), isLoading = false),
                        actions = testActions(),
                    )
                }
            }

            onNodeWithText("Staging Queue").assertIsDisplayed()
            onNodeWithText("No devices in staging queue").assertIsDisplayed()
        }

    @Test
    fun rendersStagingTableAndApproves() =
        runComposeUiTest {
            val device =
                StagingDevice(
                    id = "stg-1",
                    hostname = "unverified-printer",
                    ipAddress = "192.168.1.50",
                    macAddress = "00:11:22:33:44:55",
                    deviceType = "Printer",
                )
            var approvedDevice: StagingDevice? = null
            var dismissedDevice: StagingDevice? = null

            setContent {
                InfraMapTheme {
                    StagingScreen(
                        state = StagingUiState(devices = listOf(device), totalItems = 1, isLoading = false),
                        actions =
                            testActions(
                                onApproveClicked = { approvedDevice = it },
                                onDismissClicked = { dismissedDevice = it },
                            ),
                    )
                }
            }

            onNodeWithText("unverified-printer").assertIsDisplayed()
            onNodeWithText("192.168.1.50").assertIsDisplayed()

            onNodeWithText("Approve").performClick()
            assertEquals("stg-1", approvedDevice?.id)

            onNodeWithText("Dismiss").performClick()
            assertEquals("stg-1", dismissedDevice?.id)
        }
}
