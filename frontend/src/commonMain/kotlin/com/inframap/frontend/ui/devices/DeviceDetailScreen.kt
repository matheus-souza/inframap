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
import androidx.compose.ui.unit.dp
import com.inframap.frontend.designsystem.DeviceStatus
import com.inframap.frontend.designsystem.InfraMapButton
import com.inframap.frontend.designsystem.InfraMapCard
import com.inframap.frontend.designsystem.InfraMapConfirmDialog
import com.inframap.frontend.designsystem.InfraMapDetailSkeleton
import com.inframap.frontend.designsystem.InfraMapOutlinedButton
import com.inframap.frontend.designsystem.InfraMapStatusBadge
import com.inframap.frontend.domain.model.Device

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
                title = "Excluir Dispositivo",
                message = "Deseja realmente excluir o dispositivo '${state.device.hostname}'?",
                confirmText = if (state.isDeleting) "Excluindo..." else "Excluir",
                dismissText = "Cancelar",
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
        InfraMapOutlinedButton(text = "Tentar Novamente", onClick = onRetryClicked)
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
                text = "Voltar",
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
                    text = "ID: ${device.id}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
                )
            }
        }

        Row {
            InfraMapButton(
                text = "Editar",
                onClick = { onEditClicked(device.id) },
            )
            Spacer(modifier = Modifier.width(12.dp))
            InfraMapOutlinedButton(
                text = "Excluir",
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
                text = "Informações Principais",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(modifier = Modifier.height(16.dp))

            DetailRow("Hostname", device.hostname)
            DetailRow("Endereço IP", device.ipAddress ?: "—")
            DetailRow("Endereço MAC", device.macAddress ?: "—")
            DetailRow("Tipo de Dispositivo", device.deviceType)
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "Status",
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
        }
    }
}

@Composable
private fun DeviceHardwareCard(device: Device) {
    InfraMapCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.fillMaxWidth().padding(20.dp)) {
            Text(
                text = "Hardware & Fabricante",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(modifier = Modifier.height(16.dp))

            DetailRow("Fabricante", device.manufacturer ?: "—")
            DetailRow("Modelo", device.model ?: "—")
            DetailRow("Número de Série", device.serialNumber ?: "—")
            DetailRow("Criado em", device.createdAt ?: "—")
            DetailRow("Atualizado em", device.updatedAt ?: "—")
        }
    }
}

@Composable
private fun DeviceMetadataCard(metadata: Map<String, String>) {
    InfraMapCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.fillMaxWidth().padding(20.dp)) {
            Text(
                text = "Metadados",
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
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f))
    }
}
