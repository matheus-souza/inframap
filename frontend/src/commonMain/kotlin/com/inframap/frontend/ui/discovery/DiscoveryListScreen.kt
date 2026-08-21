package com.inframap.frontend.ui.discovery

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
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import org.jetbrains.compose.resources.stringResource
import inframap.frontend.generated.resources.Res
import inframap.frontend.generated.resources.*
import androidx.compose.ui.unit.dp
import com.inframap.frontend.designsystem.InfraMapButton
import com.inframap.frontend.designsystem.InfraMapCard
import com.inframap.frontend.designsystem.InfraMapConfirmDialog
import com.inframap.frontend.designsystem.InfraMapEmptyState
import com.inframap.frontend.designsystem.InfraMapIcons
import com.inframap.frontend.designsystem.InfraMapOutlinedButton
import com.inframap.frontend.designsystem.InfraMapSnackbarHost
import com.inframap.frontend.designsystem.InfraMapStatusBadge
import com.inframap.frontend.designsystem.InfraMapTable
import com.inframap.frontend.designsystem.SnackbarType
import com.inframap.frontend.designsystem.SourceStatus
import com.inframap.frontend.designsystem.TableColumn
import com.inframap.frontend.domain.model.DiscoverySource

@Composable
fun DiscoveryListScreen(
    state: DiscoveryListUiState,
    actions: DiscoveryListActions,
    modifier: Modifier = Modifier,
) {
    val successSnackbar = remember { SnackbarHostState() }
    val errorSnackbar = remember { SnackbarHostState() }

    DiscoveryFeedbackEffects(state, actions, successSnackbar, errorSnackbar)

    Box(modifier = modifier.fillMaxSize().padding(24.dp)) {
        Column(modifier = Modifier.fillMaxSize()) {
            DiscoveryListHeader(onCreateClicked = actions.onCreateSourceClicked)

            Spacer(modifier = Modifier.height(20.dp))

            if (state.errorMessage != null) {
                DiscoveryErrorCard(
                    errorMessage = state.errorMessage.asString(),
                    onRetryClicked = actions.onRetryClicked,
                )
            } else if (state.isLoading) {
                Box(
                    modifier = Modifier.fillMaxWidth().weight(1f),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                }
            } else {
                DiscoveryTableCard(state = state, actions = actions)
            }
        }

        InfraMapSnackbarHost(
            hostState = successSnackbar,
            type = SnackbarType.SUCCESS,
            modifier = Modifier.align(Alignment.BottomCenter),
        )

        InfraMapSnackbarHost(
            hostState = errorSnackbar,
            type = SnackbarType.ERROR,
            modifier = Modifier.align(Alignment.BottomCenter),
        )

        if (state.sourceToDelete != null) {
            InfraMapConfirmDialog(
                title = "Excluir Fonte de Descoberta",
                message =
                    "Tem certeza que deseja excluir a fonte '${state.sourceToDelete.name}'? " +
                        "Os agendamentos associados serão removidos.",
                confirmText = "Excluir",
                dismissText = "Cancelar",
                onConfirm = actions.onConfirmDelete,
                onDismiss = actions.onCancelDelete,
            )
        }
    }
}

@Composable
private fun DiscoveryFeedbackEffects(
    state: DiscoveryListUiState,
    actions: DiscoveryListActions,
    successSnackbar: SnackbarHostState,
    errorSnackbar: SnackbarHostState,
) {
    LaunchedEffect(state.toastMessage) {
        state.toastMessage?.let {
            successSnackbar.showSnackbar(it.asStringAsync())
            actions.onDismissToast()
        }
    }

    LaunchedEffect(state.deleteError) {
        state.deleteError?.let {
            errorSnackbar.showSnackbar(it.asStringAsync())
            actions.onDismissDeleteError()
        }
    }

    LaunchedEffect(state.triggerRunError) {
        state.triggerRunError?.let {
            errorSnackbar.showSnackbar(it.asStringAsync())
            actions.onDismissTriggerRunError()
        }
    }
}

@Composable
private fun DiscoveryListHeader(onCreateClicked: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column {
            Text(
                text = "Fontes de Descoberta",
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Text(
                text = "Gerencie as fontes de varredura automatica de rede",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
            )
        }

        InfraMapButton(
            text = "+ Nova Fonte",
            onClick = onCreateClicked,
        )
    }
}

@Composable
private fun DiscoveryErrorCard(
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
                text = "Tentar Novamente",
                onClick = onRetryClicked,
            )
        }
    }
}

@Composable
private fun DiscoveryTableCard(
    state: DiscoveryListUiState,
    actions: DiscoveryListActions,
) {
    if (state.sources.isEmpty()) {
        InfraMapEmptyState(
            icon = InfraMapIcons.Radar,
            title = stringResource(Res.string.discovery_empty_title),
            description = stringResource(Res.string.discovery_empty_subtitle),
            primaryActionText = stringResource(Res.string.discovery_empty_cta),
            onPrimaryAction = actions.onCreateSourceClicked,
        )
    } else {
        val columns =
            listOf(
                TableColumn(header = "Nome", weight = 2f),
                TableColumn(header = "Tipo", weight = 1.2f),
                TableColumn(header = "CIDR", weight = 1.5f),
                TableColumn(header = "Agendamento", weight = 1.5f),
                TableColumn(header = "Status", weight = 1f),
                TableColumn(header = "Acoes", weight = 2.5f),
            )

        InfraMapCard(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.fillMaxSize()) {
                InfraMapTable(
                    columns = columns,
                    items = state.sources,
                    modifier = Modifier.weight(1f),
                ) { colIndex, item ->
                    DiscoveryRowCell(colIndex = colIndex, item = item, actions = actions)
                }
            }
        }
    }
}

@Composable
private fun DiscoveryRowCell(
    colIndex: Int,
    item: DiscoverySource,
    actions: DiscoveryListActions,
) {
    when (colIndex) {
        0 ->
            Text(
                text = item.name,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
        1 ->
            Text(
                text = formatSourceType(item.sourceType),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f),
            )
        2 ->
            Text(
                text = item.configCidr ?: "-",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f),
                fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
            )
        3 ->
            Text(
                text = item.scheduleCron ?: "Manual",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f),
            )
        4 -> {
            val sourceStatus =
                when (item.lastStatus.lowercase()) {
                    "running" -> SourceStatus.RUNNING
                    "error" -> SourceStatus.ERROR
                    "cancelled" -> SourceStatus.CANCELLED
                    else -> SourceStatus.IDLE
                }
            InfraMapStatusBadge(status = sourceStatus)
        }
        5 -> {
            Row(
                horizontalArrangement = Arrangement.Start,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                InfraMapOutlinedButton(
                    text = "Executar",
                    onClick = { actions.onTriggerRunClicked(item.id) },
                    enabled = item.lastStatus != "running",
                )
                Spacer(modifier = Modifier.width(6.dp))
                InfraMapOutlinedButton(
                    text = "Excluir",
                    onClick = { actions.onDeleteSourceClicked(item) },
                )
            }
        }
    }
}

private fun formatSourceType(type: String): String =
    when (type.lowercase()) {
        "icmp_sweep" -> "ICMP Sweep"
        "arp_sweep" -> "ARP Sweep"
        "mdns" -> "mDNS"
        "proxmox" -> "Proxmox"
        "docker" -> "Docker"
        "unifi" -> "UniFi"
        else -> type
    }
