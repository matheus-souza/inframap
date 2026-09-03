package com.inframap.frontend.ui.devices

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.HorizontalDivider
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
import com.inframap.frontend.designsystem.InfraMapDetailSkeleton
import com.inframap.frontend.designsystem.InfraMapOutlinedButton
import com.inframap.frontend.designsystem.InfraMapPowerStateBadge
import com.inframap.frontend.designsystem.InfraMapStatusBadge
import com.inframap.frontend.designsystem.PowerState
import com.inframap.frontend.domain.model.Device
import com.inframap.frontend.generated.resources.Res
import com.inframap.frontend.generated.resources.common_cancel
import com.inframap.frontend.generated.resources.device_detail_back
import com.inframap.frontend.generated.resources.device_detail_created_at
import com.inframap.frontend.generated.resources.device_detail_delete_action
import com.inframap.frontend.generated.resources.device_detail_delete_confirm
import com.inframap.frontend.generated.resources.device_detail_delete_dialog_title
import com.inframap.frontend.generated.resources.device_detail_delete_processing
import com.inframap.frontend.generated.resources.device_detail_edit
import com.inframap.frontend.generated.resources.device_detail_hardware
import com.inframap.frontend.generated.resources.device_detail_hostname
import com.inframap.frontend.generated.resources.device_detail_id
import com.inframap.frontend.generated.resources.device_detail_ip
import com.inframap.frontend.generated.resources.device_detail_mac
import com.inframap.frontend.generated.resources.device_detail_main_info
import com.inframap.frontend.generated.resources.device_detail_manufacturer
import com.inframap.frontend.generated.resources.device_detail_metadata
import com.inframap.frontend.generated.resources.device_detail_model
import com.inframap.frontend.generated.resources.device_detail_power_state
import com.inframap.frontend.generated.resources.device_detail_serial
import com.inframap.frontend.generated.resources.device_detail_status
import com.inframap.frontend.generated.resources.device_detail_type
import com.inframap.frontend.generated.resources.device_detail_updated_at
import com.inframap.frontend.generated.resources.devices_retry
import org.jetbrains.compose.resources.stringResource

@Composable
fun DeviceDetailScreen(
    state: DeviceDetailUiState,
    actions: DeviceDetailActions,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier.fillMaxSize().padding(24.dp)) {
        if (state.isLoading) {
            InfraMapDetailSkeleton()
        } else if (state.errorMessage != null) {
            DeviceDetailErrorView(
                errorMessage = state.errorMessage.asString(),
                onRetryClicked = actions.onRetryClicked,
            )
        } else if (state.device != null) {
            DeviceDetailContentView(
                device = state.device,
                actions = actions,
            )
        }

        if (state.showDeleteDialog && state.device != null) {
            InfraMapConfirmDialog(
                title = stringResource(Res.string.device_detail_delete_dialog_title),
                message = stringResource(Res.string.device_detail_delete_confirm, state.device.hostname),
                confirmText =
                    if (state.isDeleting) {
                        stringResource(Res.string.device_detail_delete_processing)
                    } else {
                        stringResource(Res.string.device_detail_delete_action)
                    },
                dismissText = stringResource(Res.string.common_cancel),
                onConfirm = actions.onConfirmDelete,
                onDismiss = actions.onCancelDelete,
            )
        }
    }
}

@Composable
private fun DeviceDetailErrorView(
    errorMessage: String,
    onRetryClicked: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = errorMessage,
            color = MaterialTheme.colorScheme.error,
            style = MaterialTheme.typography.bodyLarge,
        )
        Spacer(modifier = Modifier.height(16.dp))
        InfraMapOutlinedButton(
            text = stringResource(Res.string.devices_retry),
            onClick = onRetryClicked,
        )
    }
}

@Composable
private fun DeviceDetailContentView(
    device: Device,
    actions: DeviceDetailActions,
) {
    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
    ) {
        DeviceDetailHeader(
            device = device,
            onBackClicked = actions.onBackClicked,
            onEditClicked = actions.onEditClicked,
            onDeleteClicked = actions.onDeleteClicked,
        )

        Spacer(modifier = Modifier.height(24.dp))

        DeviceMainInfoCard(device = device)

        Spacer(modifier = Modifier.height(16.dp))

        DeviceHardwareCard(device = device)

        if (!device.metadata.isNullOrEmpty()) {
            Spacer(modifier = Modifier.height(16.dp))
            DeviceMetadataCard(metadata = device.metadata)
        }
    }
}

@Composable
private fun DeviceDetailHeader(
    device: Device,
    onBackClicked: () -> Unit,
    onEditClicked: (String) -> Unit,
    onDeleteClicked: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            InfraMapOutlinedButton(
                text = stringResource(Res.string.device_detail_back),
                onClick = onBackClicked,
                leadingIcon = Icons.AutoMirrored.Filled.ArrowBack,
            )
            Spacer(modifier = Modifier.width(16.dp))
            Column {
                Text(
                    text = device.hostname,
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.onBackground,
                )
                Text(
                    text = stringResource(Res.string.device_detail_id, device.id),
                    style =
                        MaterialTheme.typography.bodySmall.copy(
                            fontFamily = FontFamily.Monospace,
                        ),
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
                )
            }
        }

        Row {
            InfraMapButton(
                text = stringResource(Res.string.device_detail_edit),
                onClick = { onEditClicked(device.id) },
            )
            Spacer(modifier = Modifier.width(12.dp))
            InfraMapOutlinedButton(
                text = stringResource(Res.string.device_detail_delete_action),
                onClick = onDeleteClicked,
            )
        }
    }
}

@Composable
private fun DeviceMainInfoCard(device: Device) {
    InfraMapCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.fillMaxWidth().padding(20.dp)) {
            Text(
                text = stringResource(Res.string.device_detail_main_info),
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(modifier = Modifier.height(16.dp))

            DetailRow(stringResource(Res.string.device_detail_hostname), device.hostname)
            DetailRow(stringResource(Res.string.device_detail_ip), device.ipAddress ?: "—", isMonospace = true)
            DetailRow(stringResource(Res.string.device_detail_mac), device.macAddress ?: "—", isMonospace = true)
            DetailRow(stringResource(Res.string.device_detail_type), device.deviceType)
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(Res.string.device_detail_status),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                )
                val statusBadge =
                    when (device.status.lowercase()) {
                        "active", "ok" -> DeviceStatus.ACTIVE
                        "staged" -> DeviceStatus.STAGED
                        else -> DeviceStatus.OFFLINE
                    }
                InfraMapStatusBadge(status = statusBadge)
            }

            // A provider-owned workload also has a runtime state, which is independent of
            // whether InfraMap is still discovering it.
            PowerState.fromRaw(device.powerState)?.let { powerState ->
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = stringResource(Res.string.device_detail_power_state),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                    )
                    InfraMapPowerStateBadge(powerState = powerState)
                }
            }
        }
    }
}

@Composable
private fun DeviceHardwareCard(device: Device) {
    InfraMapCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.fillMaxWidth().padding(20.dp)) {
            Text(
                text = stringResource(Res.string.device_detail_hardware),
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(modifier = Modifier.height(16.dp))

            DetailRow(stringResource(Res.string.device_detail_manufacturer), device.manufacturer ?: "—")
            DetailRow(stringResource(Res.string.device_detail_model), device.model ?: "—")
            DetailRow(stringResource(Res.string.device_detail_serial), device.serialNumber ?: "—")
            DetailRow(stringResource(Res.string.device_detail_created_at), device.createdAt ?: "—")
            DetailRow(stringResource(Res.string.device_detail_updated_at), device.updatedAt ?: "—")
        }
    }
}

@Composable
private fun DeviceMetadataCard(metadata: Map<String, String>) {
    InfraMapCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.fillMaxWidth().padding(20.dp)) {
            Text(
                text = stringResource(Res.string.device_detail_metadata),
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(modifier = Modifier.height(16.dp))
            metadata.forEach { (key, value) ->
                DetailRow(key, value)
            }
        }
    }
}

@Composable
private fun DetailRow(
    label: String,
    value: String,
    isMonospace: Boolean = false,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
            )
            Text(
                text = value,
                style =
                    if (isMonospace) {
                        MaterialTheme.typography.bodyLarge.copy(
                            fontFamily = FontFamily.Monospace,
                        )
                    } else {
                        MaterialTheme.typography.bodyLarge
                    },
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f))
    }
}
