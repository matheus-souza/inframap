package com.inframap.frontend.ui.subnets

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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Hub
import androidx.compose.material.icons.filled.Sensors
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.inframap.frontend.designsystem.DeviceStatus
import com.inframap.frontend.designsystem.InfraMapButton
import com.inframap.frontend.designsystem.InfraMapCard
import com.inframap.frontend.designsystem.InfraMapEmptyState
import com.inframap.frontend.designsystem.InfraMapOutlinedButton
import com.inframap.frontend.designsystem.InfraMapStatusBadge
import com.inframap.frontend.designsystem.InfraMapTable
import com.inframap.frontend.designsystem.InfraMapTableSkeleton
import com.inframap.frontend.designsystem.TableColumn
import com.inframap.frontend.domain.model.NetworkInterface
import com.inframap.frontend.domain.model.Subnet
import com.inframap.frontend.generated.resources.Res
import com.inframap.frontend.generated.resources.devices_retry
import com.inframap.frontend.generated.resources.subnets_col_auto_discovery
import com.inframap.frontend.generated.resources.subnets_col_cidr
import com.inframap.frontend.generated.resources.subnets_col_description
import com.inframap.frontend.generated.resources.subnets_col_gateway
import com.inframap.frontend.generated.resources.subnets_col_name
import com.inframap.frontend.generated.resources.subnets_col_vlan
import com.inframap.frontend.generated.resources.subnets_create_button
import com.inframap.frontend.generated.resources.subnets_detected_interface_add
import com.inframap.frontend.generated.resources.subnets_detected_interfaces_desc
import com.inframap.frontend.generated.resources.subnets_detected_interfaces_title
import com.inframap.frontend.generated.resources.subnets_empty_subtitle
import com.inframap.frontend.generated.resources.subnets_empty_title
import com.inframap.frontend.generated.resources.subnets_new_button
import com.inframap.frontend.generated.resources.subnets_subtitle
import com.inframap.frontend.generated.resources.subnets_title
import org.jetbrains.compose.resources.stringResource

@Composable
fun SubnetsScreen(
    state: SubnetsUiState,
    actions: SubnetsActions,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier.fillMaxSize().padding(24.dp)) {
        Column(modifier = Modifier.fillMaxSize()) {
            SubnetsHeader(onCreateClicked = actions.onCreateSubnetClicked)

            Spacer(modifier = Modifier.height(20.dp))

            if (state.errorMessage != null) {
                SubnetsErrorCard(
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
                SubnetsTableCard(state = state, actions = actions)
            }
        }
    }
}

@Composable
private fun SubnetsHeader(onCreateClicked: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column {
            Text(
                text = stringResource(Res.string.subnets_title),
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Text(
                text = stringResource(Res.string.subnets_subtitle),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
            )
        }

        InfraMapButton(
            text = stringResource(Res.string.subnets_new_button),
            onClick = onCreateClicked,
        )
    }
}

@Composable
private fun SubnetsErrorCard(
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
private fun SubnetsTableCard(
    state: SubnetsUiState,
    actions: SubnetsActions,
) {
    if (state.subnets.isEmpty()) {
        Column(modifier = Modifier.fillMaxWidth()) {
            InfraMapEmptyState(
                icon = Icons.Filled.Hub,
                title = stringResource(Res.string.subnets_empty_title),
                description = stringResource(Res.string.subnets_empty_subtitle),
                primaryActionText = stringResource(Res.string.subnets_create_button),
                onPrimaryAction = actions.onCreateSubnetClicked,
            )

            if (state.detectedInterfaces.isNotEmpty()) {
                Spacer(modifier = Modifier.height(16.dp))
                DetectedInterfacesCard(
                    interfaces = state.detectedInterfaces,
                    onAddClicked = actions.onAddInterfaceClicked,
                )
            }
        }
    } else {
        val columns =
            listOf(
                TableColumn(header = stringResource(Res.string.subnets_col_name), weight = 2f),
                TableColumn(header = stringResource(Res.string.subnets_col_cidr), weight = 1.8f),
                TableColumn(header = stringResource(Res.string.subnets_col_vlan), weight = 1f),
                TableColumn(header = stringResource(Res.string.subnets_col_gateway), weight = 1.5f),
                TableColumn(header = stringResource(Res.string.subnets_col_auto_discovery), weight = 1.5f),
                TableColumn(header = stringResource(Res.string.subnets_col_description), weight = 2f),
            )

        InfraMapCard(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.fillMaxSize()) {
                InfraMapTable(
                    columns = columns,
                    items = state.subnets,
                    modifier = Modifier.weight(1f),
                ) { colIndex, item ->
                    SubnetRowCell(colIndex = colIndex, item = item)
                }
            }
        }
    }
}

@Composable
private fun DetectedInterfacesCard(
    interfaces: List<NetworkInterface>,
    onAddClicked: (NetworkInterface) -> Unit,
) {
    InfraMapCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.fillMaxWidth().padding(8.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(bottom = 8.dp),
            ) {
                Icon(
                    imageVector = Icons.Filled.Sensors,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                )
                Spacer(modifier = Modifier.width(8.dp))
                Column {
                    Text(
                        text = stringResource(Res.string.subnets_detected_interfaces_title),
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        text = stringResource(Res.string.subnets_detected_interfaces_desc),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                    )
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))

            interfaces.forEach { iface ->
                DetectedInterfaceRow(
                    iface = iface,
                    onAddClicked = { onAddClicked(iface) },
                )
            }
        }
    }
}

@Composable
private fun DetectedInterfaceRow(
    iface: NetworkInterface,
    onAddClicked: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = iface.name,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = "${iface.cidr}  |  IP: ${iface.ip}  |  MAC: ${iface.mac}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
            )
        }
        InfraMapOutlinedButton(
            text = stringResource(Res.string.subnets_detected_interface_add),
            onClick = onAddClicked,
        )
    }
}

@Composable
private fun SubnetRowCell(
    colIndex: Int,
    item: Subnet,
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
                text = item.cidr,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
                fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
            )
        2 ->
            Text(
                text = item.vlanId?.toString() ?: "-",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
        3 ->
            Text(
                text = item.gatewayIp ?: "-",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
                fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
            )
        4 -> {
            val status = if (item.discoveryEnabled) DeviceStatus.ACTIVE else DeviceStatus.OFFLINE
            InfraMapStatusBadge(status = status)
        }
        5 ->
            Text(
                text = item.description?.ifEmpty { "-" } ?: "-",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
    }
}
