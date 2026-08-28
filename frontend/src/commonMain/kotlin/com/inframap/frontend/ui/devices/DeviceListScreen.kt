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
import com.inframap.frontend.designsystem.InfraMapEmptyState
import com.inframap.frontend.designsystem.InfraMapIcons
import com.inframap.frontend.designsystem.InfraMapOutlinedButton
import com.inframap.frontend.designsystem.InfraMapStatusBadge
import com.inframap.frontend.designsystem.InfraMapTable
import com.inframap.frontend.designsystem.InfraMapTablePagination
import com.inframap.frontend.designsystem.InfraMapTableSkeleton
import com.inframap.frontend.designsystem.InfraMapTextField
import com.inframap.frontend.designsystem.TableColumn
import com.inframap.frontend.domain.model.Device
import com.inframap.frontend.generated.resources.Res
import com.inframap.frontend.generated.resources.devices_create_button
import com.inframap.frontend.generated.resources.devices_empty_subtitle
import com.inframap.frontend.generated.resources.devices_empty_title
import org.jetbrains.compose.resources.stringResource
import kotlin.math.ceil

@Composable
fun DeviceListScreen(
    state: DeviceListUiState,
    actions: DeviceListActions,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier.fillMaxSize().padding(24.dp)) {
        Column(modifier = Modifier.fillMaxSize()) {
            DeviceListHeader(onCreateClicked = actions.onCreateDeviceClicked)

            Spacer(modifier = Modifier.height(20.dp))

            InfraMapCard(modifier = Modifier.fillMaxWidth()) {
                Box(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                    InfraMapTextField(
                        value = state.searchQuery,
                        onValueChange = actions.onSearchQueryChanged,
                        label = "Buscar por hostname...",
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            if (state.errorMessage != null) {
                DeviceListErrorCard(
                    errorMessage = state.errorMessage.asString(),
                    onRetryClicked = actions.onRetryClicked,
                )
            } else if (state.isLoading) {
                InfraMapCard(modifier = Modifier.fillMaxWidth()) {
                    InfraMapTableSkeleton(
                        rows = 5,
                        columns = 5,
                    )
                }
            } else {
                DeviceListTableCard(state = state, actions = actions)
            }
        }

        if (state.deviceToDelete != null) {
            val confirmMessage =
                if (state.deleteErrorMessage != null) {
                    "Erro ao excluir: ${state.deleteErrorMessage.asString()}\n\n" +
                        "Tem certeza que deseja tentar novamente?"
                } else {
                    "Tem certeza que deseja excluir o dispositivo '${state.deviceToDelete.hostname}'? " +
                        "Esta ação marca o dispositivo como excluído."
                }
            InfraMapConfirmDialog(
                title = "Excluir Dispositivo",
                message = confirmMessage,
                confirmText = if (state.isDeleting) "Excluindo..." else "Excluir",
                dismissText = "Cancelar",
                onConfirm = actions.onConfirmDelete,
                onDismiss = actions.onCancelDelete,
            )
        }
    }
}

@Composable
private fun DeviceListHeader(onCreateClicked: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column {
            Text(
                text = "Dispositivos",
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Text(
                text = "Gerencie os ativos de infraestrutura da rede",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
            )
        }

        InfraMapButton(
            text = "+ Novo Dispositivo",
            onClick = onCreateClicked,
        )
    }
}

@Composable
private fun DeviceListErrorCard(
    errorMessage: String,
    onRetryClicked: () -> Unit,
) {
    InfraMapCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = errorMessage,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyLarge,
            )
            Spacer(modifier = Modifier.height(12.dp))
            InfraMapOutlinedButton(
                text = "Tentar Novamente",
                onClick = onRetryClicked,
            )
        }
    }
}

@Composable
private fun DeviceListTableCard(
    state: DeviceListUiState,
    actions: DeviceListActions,
) {
    if (state.devices.isEmpty()) {
        InfraMapEmptyState(
            icon = InfraMapIcons.Dns,
            title = stringResource(Res.string.devices_empty_title),
            description = stringResource(Res.string.devices_empty_subtitle),
            primaryActionText = stringResource(Res.string.devices_create_button),
            onPrimaryAction = actions.onCreateDeviceClicked,
        )
    } else {
        val columns =
            listOf(
                TableColumn(header = "Hostname", weight = 2f),
                TableColumn(header = "IP", weight = 1.5f),
                TableColumn(header = "Tipo", weight = 1.2f),
                TableColumn(header = "Status", weight = 1f),
                TableColumn(header = "Ações", weight = 2f),
            )

        InfraMapCard(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.fillMaxSize()) {
                InfraMapTable(
                    columns = columns,
                    items = state.devices,
                    onRowClick = { actions.onDeviceClicked(it.id) },
                    modifier = Modifier.weight(1f),
                ) { colIndex, item ->
                    DeviceTableRowCell(colIndex = colIndex, item = item, actions = actions)
                }

                val totalPages =
                    maxOf(1, ceil(state.totalItems.toDouble() / state.perPage.toDouble()).toInt())
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
private fun DeviceTableRowCell(
    colIndex: Int,
    item: Device,
    actions: DeviceListActions,
) {
    when (colIndex) {
        0 ->
            Text(
                text = item.hostname,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
        1 ->
            Text(
                text = item.ipAddress ?: "—",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f),
                fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
            )
        2 ->
            Text(
                text = item.deviceType,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f),
            )
        3 -> {
            val deviceStatus =
                when (item.status.lowercase()) {
                    "active", "ok" -> DeviceStatus.ACTIVE
                    "staged" -> DeviceStatus.STAGED
                    else -> DeviceStatus.OFFLINE
                }
            InfraMapStatusBadge(status = deviceStatus)
        }
        4 -> {
            Row(
                horizontalArrangement = Arrangement.Start,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                InfraMapOutlinedButton(
                    text = "Ver",
                    onClick = { actions.onDeviceClicked(item.id) },
                )
                Spacer(modifier = Modifier.width(6.dp))
                InfraMapOutlinedButton(
                    text = "Editar",
                    onClick = { actions.onEditDeviceClicked(item.id) },
                )
                Spacer(modifier = Modifier.width(6.dp))
                InfraMapOutlinedButton(
                    text = "Excluir",
                    onClick = { actions.onDeleteDeviceClicked(item) },
                )
            }
        }
    }
}
