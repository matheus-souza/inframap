package com.inframap.frontend.ui.discovery

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
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
import com.inframap.frontend.designsystem.InfraMapTableSkeleton
import com.inframap.frontend.designsystem.SnackbarType
import com.inframap.frontend.designsystem.SourceStatus
import com.inframap.frontend.designsystem.TableColumn
import com.inframap.frontend.domain.model.DiscoverySource
import com.inframap.frontend.generated.resources.Res
import com.inframap.frontend.generated.resources.collector_name_arp_sweep
import com.inframap.frontend.generated.resources.collector_name_docker
import com.inframap.frontend.generated.resources.collector_name_icmp_sweep
import com.inframap.frontend.generated.resources.collector_name_mdns
import com.inframap.frontend.generated.resources.collector_name_proxmox
import com.inframap.frontend.generated.resources.collector_name_reverse_dns
import com.inframap.frontend.generated.resources.collector_name_snmp
import com.inframap.frontend.generated.resources.collector_name_unifi
import com.inframap.frontend.generated.resources.common_cancel
import com.inframap.frontend.generated.resources.devices_retry
import com.inframap.frontend.generated.resources.discovery_action_delete
import com.inframap.frontend.generated.resources.discovery_action_execute
import com.inframap.frontend.generated.resources.discovery_col_actions
import com.inframap.frontend.generated.resources.discovery_col_cidr
import com.inframap.frontend.generated.resources.discovery_col_name
import com.inframap.frontend.generated.resources.discovery_col_schedule
import com.inframap.frontend.generated.resources.discovery_col_status
import com.inframap.frontend.generated.resources.discovery_col_type
import com.inframap.frontend.generated.resources.discovery_delete_confirm_message
import com.inframap.frontend.generated.resources.discovery_delete_dialog_title
import com.inframap.frontend.generated.resources.discovery_empty_cta
import com.inframap.frontend.generated.resources.discovery_empty_subtitle
import com.inframap.frontend.generated.resources.discovery_empty_title
import com.inframap.frontend.generated.resources.discovery_new_button
import com.inframap.frontend.generated.resources.discovery_schedule_manual
import com.inframap.frontend.generated.resources.discovery_subtitle
import com.inframap.frontend.generated.resources.discovery_title
import org.jetbrains.compose.resources.stringResource

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
                InfraMapCard(modifier = Modifier.fillMaxWidth()) {
                    InfraMapTableSkeleton(
                        rows = 5,
                        columns = 6,
                    )
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
                title = stringResource(Res.string.discovery_delete_dialog_title),
                message = stringResource(Res.string.discovery_delete_confirm_message, state.sourceToDelete.name),
                confirmText = stringResource(Res.string.discovery_action_delete),
                dismissText = stringResource(Res.string.common_cancel),
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
                text = stringResource(Res.string.discovery_title),
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Text(
                text = stringResource(Res.string.discovery_subtitle),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
            )
        }

        InfraMapButton(
            text = stringResource(Res.string.discovery_new_button),
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
                text = stringResource(Res.string.devices_retry),
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
                TableColumn(header = stringResource(Res.string.discovery_col_name), weight = 2f),
                TableColumn(header = stringResource(Res.string.discovery_col_type), weight = 2f),
                TableColumn(header = stringResource(Res.string.discovery_col_cidr), weight = 1.5f),
                TableColumn(header = stringResource(Res.string.discovery_col_schedule), weight = 1.2f),
                TableColumn(header = stringResource(Res.string.discovery_col_status), weight = 1f),
                TableColumn(header = stringResource(Res.string.discovery_col_actions), weight = 2.3f),
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
        1 -> DiscoveryCollectorsCell(item = item)
        2 ->
            Text(
                text = item.configCidr ?: "-",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f),
                fontFamily = FontFamily.Monospace,
            )
        3 ->
            Text(
                text = item.scheduleCron ?: stringResource(Res.string.discovery_schedule_manual),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f),
            )
        4 -> DiscoveryStatusCell(lastStatus = item.lastStatus)
        5 -> DiscoveryActionsCell(item = item, actions = actions)
    }
}

@Composable
private fun DiscoveryCollectorsCell(item: DiscoverySource) {
    if (item.collectors.isEmpty()) {
        Text(
            text = "-",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f),
        )
    } else {
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            item.collectors.forEach { collector ->
                Surface(
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    shape = RoundedCornerShape(4.dp),
                ) {
                    Text(
                        text = formatCollectorName(collector.collectorType),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun DiscoveryStatusCell(lastStatus: String) {
    val sourceStatus =
        when (lastStatus.lowercase()) {
            "running" -> SourceStatus.RUNNING
            "partial" -> SourceStatus.PARTIAL
            "error" -> SourceStatus.ERROR
            "cancelled" -> SourceStatus.CANCELLED
            else -> SourceStatus.IDLE
        }
    InfraMapStatusBadge(status = sourceStatus)
}

@Composable
private fun DiscoveryActionsCell(
    item: DiscoverySource,
    actions: DiscoveryListActions,
) {
    Row(
        horizontalArrangement = Arrangement.Start,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        InfraMapOutlinedButton(
            text = stringResource(Res.string.discovery_action_execute),
            onClick = { actions.onTriggerRunClicked(item.id) },
            enabled = item.lastStatus != "running",
        )
        Spacer(modifier = Modifier.width(6.dp))
        InfraMapOutlinedButton(
            text = stringResource(Res.string.discovery_action_delete),
            onClick = { actions.onDeleteSourceClicked(item) },
        )
    }
}

@Composable
private fun formatCollectorName(type: String): String =
    when (type.lowercase()) {
        "icmp_sweep" -> stringResource(Res.string.collector_name_icmp_sweep)
        "arp_sweep" -> stringResource(Res.string.collector_name_arp_sweep)
        "mdns" -> stringResource(Res.string.collector_name_mdns)
        "reverse_dns" -> stringResource(Res.string.collector_name_reverse_dns)
        "snmp" -> stringResource(Res.string.collector_name_snmp)
        "proxmox" -> stringResource(Res.string.collector_name_proxmox)
        "docker" -> stringResource(Res.string.collector_name_docker)
        "unifi" -> stringResource(Res.string.collector_name_unifi)
        else -> type
    }
