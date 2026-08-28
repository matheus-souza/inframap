package com.inframap.frontend.ui

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.runComposeUiTest
import com.inframap.frontend.designsystem.InfraMapTheme
import com.inframap.frontend.ui.devices.DeviceDetailActions
import com.inframap.frontend.ui.devices.DeviceDetailScreen
import com.inframap.frontend.ui.devices.DeviceDetailUiState
import com.inframap.frontend.ui.devices.DeviceListActions
import com.inframap.frontend.ui.devices.DeviceListScreen
import com.inframap.frontend.ui.devices.DeviceListUiState
import com.inframap.frontend.ui.devices.EditDeviceActions
import com.inframap.frontend.ui.devices.EditDeviceScreen
import com.inframap.frontend.ui.devices.EditDeviceUiState
import com.inframap.frontend.ui.discovery.DiscoveryListActions
import com.inframap.frontend.ui.discovery.DiscoveryListScreen
import com.inframap.frontend.ui.discovery.DiscoveryListUiState
import com.inframap.frontend.ui.staging.StagingActions
import com.inframap.frontend.ui.staging.StagingScreen
import com.inframap.frontend.ui.staging.StagingUiState
import com.inframap.frontend.ui.subnets.SubnetsActions
import com.inframap.frontend.ui.subnets.SubnetsScreen
import com.inframap.frontend.ui.subnets.SubnetsUiState
import org.junit.jupiter.api.Test

@OptIn(ExperimentalTestApi::class)
class ScreenLoadingSkeletonTest {
    @Test
    fun deviceListScreenRendersSkeletonWhenLoading() =
        runComposeUiTest {
            setContent {
                InfraMapTheme {
                    DeviceListScreen(
                        state = DeviceListUiState(isLoading = true),
                        actions =
                            DeviceListActions(
                                onSearchQueryChanged = {},
                                onPageChanged = {},
                                onCreateDeviceClicked = {},
                                onDeviceClicked = {},
                                onEditDeviceClicked = {},
                                onDeleteDeviceClicked = {},
                                onConfirmDelete = {},
                                onCancelDelete = {},
                                onDismissDeleteError = {},
                                onDismissToast = {},
                                onRetryClicked = {},
                            ),
                    )
                }
            }
            waitForIdle()
        }

    @Test
    fun deviceDetailScreenRendersSkeletonWhenLoading() =
        runComposeUiTest {
            setContent {
                InfraMapTheme {
                    DeviceDetailScreen(
                        state = DeviceDetailUiState(isLoading = true),
                        actions =
                            DeviceDetailActions(
                                onBackClicked = {},
                                onEditClicked = {},
                                onDeleteClicked = {},
                                onConfirmDelete = {},
                                onCancelDelete = {},
                                onRetryClicked = {},
                            ),
                    )
                }
            }
            waitForIdle()
        }

    @Test
    fun editDeviceScreenRendersSkeletonWhenLoading() =
        runComposeUiTest {
            setContent {
                InfraMapTheme {
                    EditDeviceScreen(
                        state = EditDeviceUiState(deviceId = "dev-1", isLoading = true),
                        actions =
                            EditDeviceActions(
                                onHostnameChanged = {},
                                onIpAddressChanged = {},
                                onMacAddressChanged = {},
                                onDeviceTypeChanged = {},
                                onStatusChanged = {},
                                onSubmitClicked = {},
                                onCancelClicked = {},
                                onRetryClicked = {},
                            ),
                    )
                }
            }
            waitForIdle()
        }

    @Test
    fun stagingScreenRendersSkeletonWhenLoading() =
        runComposeUiTest {
            setContent {
                InfraMapTheme {
                    StagingScreen(
                        state = StagingUiState(isLoading = true),
                        actions =
                            StagingActions(
                                onPageChanged = {},
                                onApproveClicked = {},
                                onDismissClicked = {},
                                onConfirmDismiss = {},
                                onCancelDismiss = {},
                                onDismissActionError = {},
                                onDismissToast = {},
                                onRetryClicked = {},
                                onConfigureDiscovery = {},
                            ),
                    )
                }
            }
            waitForIdle()
        }

    @Test
    fun subnetsScreenRendersSkeletonWhenLoading() =
        runComposeUiTest {
            setContent {
                InfraMapTheme {
                    SubnetsScreen(
                        state = SubnetsUiState(isLoading = true),
                        actions =
                            SubnetsActions(
                                onCreateSubnetClicked = {},
                                onAddInterfaceClicked = {},
                                onDismissToast = {},
                                onRetryClicked = {},
                            ),
                    )
                }
            }
            waitForIdle()
        }

    @Test
    fun discoveryListScreenRendersSkeletonWhenLoading() =
        runComposeUiTest {
            setContent {
                InfraMapTheme {
                    DiscoveryListScreen(
                        state = DiscoveryListUiState(isLoading = true),
                        actions =
                            DiscoveryListActions(
                                onCreateSourceClicked = {},
                                onTriggerRunClicked = {},
                                onDeleteSourceClicked = {},
                                onConfirmDelete = {},
                                onCancelDelete = {},
                                onDismissDeleteError = {},
                                onDismissToast = {},
                                onDismissTriggerRunError = {},
                                onRetryClicked = {},
                            ),
                    )
                }
            }
            waitForIdle()
        }
}
