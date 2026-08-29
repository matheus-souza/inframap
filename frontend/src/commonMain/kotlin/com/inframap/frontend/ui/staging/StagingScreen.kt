package com.inframap.frontend.ui.staging

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.inframap.frontend.designsystem.DeviceStatus
import com.inframap.frontend.designsystem.InfraMapButton
import com.inframap.frontend.designsystem.InfraMapCard
import com.inframap.frontend.designsystem.InfraMapConfirmDialog
import com.inframap.frontend.designsystem.InfraMapEmptyState
import com.inframap.frontend.designsystem.InfraMapIcons
import com.inframap.frontend.designsystem.InfraMapOutlinedButton
import com.inframap.frontend.designsystem.InfraMapStatusBadge
import com.inframap.frontend.designsystem.InfraMapTable
import com.inframap.frontend.designsystem.InfraMapTablePagination
import com.inframap.frontend.designsystem.InfraMapTableSkeleton
import com.inframap.frontend.designsystem.TableColumn
import com.inframap.frontend.domain.model.StagingDevice
import com.inframap.frontend.generated.resources.Res
import com.inframap.frontend.generated.resources.common_cancel
import com.inframap.frontend.generated.resources.staging_action_approve
import com.inframap.frontend.generated.resources.staging_action_processing
import com.inframap.frontend.generated.resources.staging_col_actions
import com.inframap.frontend.generated.resources.staging_col_hostname
import com.inframap.frontend.generated.resources.staging_col_ip
import com.inframap.frontend.generated.resources.staging_col_mac
import com.inframap.frontend.generated.resources.staging_col_status
import com.inframap.frontend.generated.resources.staging_col_type
import com.inframap.frontend.generated.resources.staging_configure_discovery
import com.inframap.frontend.generated.resources.staging_dismiss_action
import com.inframap.frontend.generated.resources.staging_dismiss_confirm_message
import com.inframap.frontend.generated.resources.staging_dismiss_dialog_title
import com.inframap.frontend.generated.resources.staging_dismiss_error_prefix
import com.inframap.frontend.generated.resources.staging_dismiss_processing
import com.inframap.frontend.generated.resources.staging_empty_subtitle
import com.inframap.frontend.generated.resources.staging_empty_title
import com.inframap.frontend.generated.resources.staging_header
import com.inframap.frontend.generated.resources.staging_retry
import com.inframap.frontend.generated.resources.staging_subtitle
import com.inframap.frontend.generated.resources.staging_value_empty
import org.jetbrains.compose.resources.stringResource
import kotlin.math.ceil

@Composable
fun StagingScreen(
    state: StagingUiState,
    actions: StagingActions,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier.fillMaxSize().padding(24.dp)) {
        Column(modifier = Modifier.fillMaxSize()) {
            StagingHeader()

            Spacer(modifier = Modifier.height(20.dp))

            if (state.errorMessage != null) {
                StagingErrorCard(
                    errorMessage = state.errorMessage.asString(),
                    onRetryClicked = actions.onRetryClicked,
                )
            } else if (state.isLoading) {
                InfraMapCard(modifier = Modifier.fillMaxWidth()) {
                    InfraMapTableSkeleton(
                        rows = 5,
                        columns = 6,
                    )
                }
            } else {
                StagingTableCard(state = state, actions = actions)
            }
        }

        if (state.deviceToDismiss != null) {
            val confirmMessage =
                if (state.actionErrorMessage != null) {
                    stringResource(Res.string.staging_dismiss_error_prefix, state.actionErrorMessage.asString())
                } else {
                    stringResource(Res.string.staging_dismiss_confirm_message, state.deviceToDismiss.hostname)
                }

            InfraMapConfirmDialog(
                title = stringResource(Res.string.staging_dismiss_dialog_title),
                message = confirmMessage,
                confirmText =
                    if (state.isProcessingAction) {
                        stringResource(Res.string.staging_dismiss_processing)
                    } else {
                        stringResource(Res.string.staging_dismiss_action)
                    },
                dismissText = stringResource(Res.string.common_cancel),
                onConfirm = actions.onConfirmDismiss,
                onDismiss = actions.onCancelDismiss,
            )
        }
    }
}

@Composable
private fun StagingHeader() {
    Column {
        Text(
            text = stringResource(Res.string.staging_header),
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Text(
            text = stringResource(Res.string.staging_subtitle),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
        )
    }
}

@Composable
private fun StagingErrorCard(
    errorMessage: String,
    onRetryClicked: () -> Unit,
) {
    InfraMapCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = errorMessage,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.error,
            )
            Spacer(modifier = Modifier.height(16.dp))
            InfraMapButton(
                text = stringResource(Res.string.staging_retry),
                onClick = onRetryClicked,
            )
        }
    }
}

@Composable
private fun StagingTableCard(
    state: StagingUiState,
    actions: StagingActions,
) {
    if (state.devices.isEmpty()) {
        InfraMapEmptyState(
            icon = InfraMapIcons.MoveToInbox,
            title = stringResource(Res.string.staging_empty_title),
            description = stringResource(Res.string.staging_empty_subtitle),
            primaryActionText = stringResource(Res.string.staging_configure_discovery),
            onPrimaryAction = actions.onConfigureDiscovery,
        )
    } else {
        val columns =
            listOf(
                TableColumn(header = stringResource(Res.string.staging_col_hostname), weight = 2f),
                TableColumn(header = stringResource(Res.string.staging_col_ip), weight = 1.5f),
                TableColumn(header = stringResource(Res.string.staging_col_mac), weight = 1.5f),
                TableColumn(header = stringResource(Res.string.staging_col_type), weight = 1.2f),
                TableColumn(header = stringResource(Res.string.staging_col_status), weight = 1f),
                TableColumn(header = stringResource(Res.string.staging_col_actions), weight = 2.5f),
            )

        val totalPages = maxOf(1, ceil(state.totalItems.toDouble() / state.perPage.toDouble()).toInt())

        InfraMapCard(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.fillMaxSize()) {
                InfraMapTable(
                    columns = columns,
                    items = state.devices,
                    modifier = Modifier.weight(1f),
                ) { colIndex, item ->
                    StagingRowCell(colIndex = colIndex, item = item, state = state, actions = actions)
                }

                Spacer(modifier = Modifier.height(16.dp))

                InfraMapTablePagination(
                    currentPage = state.currentPage,
                    totalPages = totalPages,
                    onPageChange = actions.onPageChanged,
                )
            }
        }
    }
}

@Composable
private fun StagingRowCell(
    colIndex: Int,
    item: StagingDevice,
    state: StagingUiState,
    actions: StagingActions,
) {
    val isActioningThis = state.isProcessingAction && state.actionDeviceId == item.id

    when (colIndex) {
        0 ->
            Text(
                text = item.hostname,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
        1 ->
            Text(
                text = item.ipAddress ?: stringResource(Res.string.staging_value_empty),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
                fontFamily = FontFamily.Monospace,
            )
        2 ->
            Text(
                text = item.macAddress ?: stringResource(Res.string.staging_value_empty),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
                fontFamily = FontFamily.Monospace,
            )
        3 ->
            Text(
                text = item.deviceType,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
        4 ->
            InfraMapStatusBadge(status = DeviceStatus.STAGED)
        5 ->
            Row {
                InfraMapButton(
                    text =
                        if (isActioningThis) {
                            stringResource(Res.string.staging_action_processing)
                        } else {
                            stringResource(Res.string.staging_action_approve)
                        },
                    onClick = { actions.onApproveClicked(item) },
                    enabled = !state.isProcessingAction,
                )
                Spacer(modifier = Modifier.width(8.dp))
                InfraMapOutlinedButton(
                    text = stringResource(Res.string.staging_dismiss_action),
                    onClick = { actions.onDismissClicked(item) },
                    enabled = !state.isProcessingAction,
                )
            }
    }
}
