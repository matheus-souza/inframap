package com.inframap.frontend.ui.topology

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.inframap.frontend.designsystem.DeviceStatus
import com.inframap.frontend.designsystem.InfraMapBorder
import com.inframap.frontend.designsystem.InfraMapButton
import com.inframap.frontend.designsystem.InfraMapIcons
import com.inframap.frontend.designsystem.InfraMapPowerStateBadge
import com.inframap.frontend.designsystem.InfraMapPurple
import com.inframap.frontend.designsystem.InfraMapStatusBadge
import com.inframap.frontend.designsystem.InfraMapSurfaceBg
import com.inframap.frontend.designsystem.InfraMapTextPrimary
import com.inframap.frontend.designsystem.InfraMapTextSecondary
import com.inframap.frontend.designsystem.PowerState
import com.inframap.frontend.designsystem.StatusOnline
import com.inframap.frontend.domain.model.TopologyNode
import com.inframap.frontend.generated.resources.Res
import com.inframap.frontend.generated.resources.topology_hosted_on
import com.inframap.frontend.generated.resources.topology_inspector_close_sheet
import com.inframap.frontend.generated.resources.topology_inspector_device_type
import com.inframap.frontend.generated.resources.topology_inspector_discovery_provenance
import com.inframap.frontend.generated.resources.topology_inspector_discovery_provenance_value
import com.inframap.frontend.generated.resources.topology_inspector_edit_device_metadata
import com.inframap.frontend.generated.resources.topology_inspector_edit_metadata
import com.inframap.frontend.generated.resources.topology_inspector_hostname
import com.inframap.frontend.generated.resources.topology_inspector_icmp_latency
import com.inframap.frontend.generated.resources.topology_inspector_interfaces
import com.inframap.frontend.generated.resources.topology_inspector_interfaces_health
import com.inframap.frontend.generated.resources.topology_inspector_interfaces_sample
import com.inframap.frontend.generated.resources.topology_inspector_ip_address
import com.inframap.frontend.generated.resources.topology_inspector_mac_address
import com.inframap.frontend.generated.resources.topology_inspector_network_identity
import com.inframap.frontend.generated.resources.topology_inspector_node_id
import com.inframap.frontend.generated.resources.topology_inspector_power_state
import com.inframap.frontend.generated.resources.topology_inspector_specs_status
import com.inframap.frontend.generated.resources.topology_inspector_status
import com.inframap.frontend.generated.resources.topology_inspector_subnet_id
import com.inframap.frontend.generated.resources.topology_inspector_trigger_scan
import org.jetbrains.compose.resources.stringResource

private val SectionBg = Color(0xFF27272A).copy(alpha = 0.4f)

@Suppress("LongMethod")
@Composable
fun DeviceInspectorSheet(
    node: TopologyNode,
    onDismiss: () -> Unit,
    onTriggerScan: (String) -> Unit,
    onEditMetadata: (String) -> Unit,
    modifier: Modifier = Modifier,
    /** The host that runs this workload, already resolved so the sheet can name it. */
    parent: TopologyNode? = null,
    onParentSelected: (String) -> Unit = {},
) {
    Surface(
        modifier =
            modifier
                .width(360.dp)
                .fillMaxHeight(),
        color = InfraMapSurfaceBg,
        shadowElevation = 12.dp,
        border = BorderStroke(1.dp, InfraMapBorder),
    ) {
        Column(
            modifier = Modifier.fillMaxHeight(),
        ) {
            // Sheet Header
            InspectorHeader(
                node = node,
                onDismiss = onDismiss,
            )

            HorizontalDivider(color = InfraMapBorder, thickness = 1.dp)

            // Sheet Body (Scrollable)
            Column(
                modifier =
                    Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState())
                        .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                // Section 1: Network Identity
                InspectorSection(
                    title = stringResource(Res.string.topology_inspector_network_identity),
                    icon = InfraMapIcons.Dns,
                ) {
                    DetailRow(
                        label = stringResource(Res.string.topology_inspector_hostname),
                        value = node.label,
                    )
                    DetailRow(
                        label = stringResource(Res.string.topology_inspector_ip_address),
                        value = deriveIpAddress(node),
                        isMonospace = true,
                    )
                    DetailRow(
                        label = stringResource(Res.string.topology_inspector_mac_address),
                        value = deriveMacAddress(node),
                        isMonospace = true,
                    )
                    DetailRow(
                        label = stringResource(Res.string.topology_inspector_subnet_id),
                        value = deriveSubnetId(node),
                        isMonospace = true,
                    )
                }

                // Section 2: Hardware & Status
                InspectorSection(
                    title = stringResource(Res.string.topology_inspector_specs_status),
                    icon = InfraMapIcons.Lan,
                ) {
                    DetailRow(
                        label = stringResource(Res.string.topology_inspector_device_type),
                        value = node.deviceType.replaceFirstChar { it.uppercase() },
                    )
                    Row(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = stringResource(Res.string.topology_inspector_status),
                            style = MaterialTheme.typography.bodySmall,
                            color = InfraMapTextSecondary,
                        )
                        InfraMapStatusBadge(status = mapToDeviceStatus(node.status))
                    }

                    // Shown alongside the status rather than merged into it: a stopped
                    // container is still an actively discovered device.
                    PowerState.fromRaw(node.powerState)?.let { powerState ->
                        Row(
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = stringResource(Res.string.topology_inspector_power_state),
                                style = MaterialTheme.typography.bodySmall,
                                color = InfraMapTextSecondary,
                            )
                            InfraMapPowerStateBadge(powerState = powerState)
                        }
                    }

                    node.parentDeviceId?.let { parentId ->
                        // Named and clickable: a bare UUID tells a reader nothing and leads
                        // nowhere. When the host is not in the current graph the id is all
                        // there is, so it is shown but not offered as a destination.
                        Row(
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .then(
                                        if (parent != null) {
                                            Modifier.clickable { onParentSelected(parentId) }
                                        } else {
                                            Modifier
                                        },
                                    ).padding(vertical = 4.dp)
                                    .testTag("hosted_on_row"),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = stringResource(Res.string.topology_hosted_on),
                                style = MaterialTheme.typography.bodySmall,
                                color = InfraMapTextSecondary,
                            )
                            Text(
                                text = parent?.label ?: parentId,
                                style = MaterialTheme.typography.bodySmall,
                                color =
                                    if (parent != null) {
                                        MaterialTheme.colorScheme.primary
                                    } else {
                                        MaterialTheme.colorScheme.onSurface
                                    },
                            )
                        }
                    }
                }

                // Section 3: Active Interfaces & Latency
                InspectorSection(
                    title = stringResource(Res.string.topology_inspector_interfaces_health),
                    icon = InfraMapIcons.Radar,
                ) {
                    DetailRow(
                        label = stringResource(Res.string.topology_inspector_interfaces),
                        value = stringResource(Res.string.topology_inspector_interfaces_sample),
                    )
                    Row(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = stringResource(Res.string.topology_inspector_icmp_latency),
                            style = MaterialTheme.typography.bodySmall,
                            color = InfraMapTextSecondary,
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier =
                                    Modifier
                                        .padding(end = 6.dp)
                                        .width(6.dp)
                                        .height(6.dp)
                                        .background(StatusOnline, RoundedCornerShape(3.dp)),
                            )
                            Text(
                                text = "1.2 ms",
                                style =
                                    MaterialTheme.typography.bodySmall.copy(
                                        fontFamily = FontFamily.Monospace,
                                        fontWeight = FontWeight.Bold,
                                    ),
                                color = StatusOnline,
                            )
                        }
                    }
                    DetailRow(
                        label = stringResource(Res.string.topology_inspector_discovery_provenance),
                        value = stringResource(Res.string.topology_inspector_discovery_provenance_value),
                    )
                }
            }

            HorizontalDivider(color = InfraMapBorder, thickness = 1.dp)

            // Sheet Footer Actions
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                InfraMapButton(
                    text = stringResource(Res.string.topology_inspector_trigger_scan),
                    onClick = { onTriggerScan(node.id) },
                    modifier = Modifier.fillMaxWidth(),
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                ) {
                    IconButton(
                        onClick = { onEditMetadata(node.id) },
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .background(SectionBg, RoundedCornerShape(8.dp)),
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(
                                imageVector = Icons.Default.Edit,
                                contentDescription = stringResource(Res.string.topology_inspector_edit_metadata),
                                tint = InfraMapPurple,
                                modifier = Modifier.padding(end = 8.dp),
                            )
                            Text(
                                text = stringResource(Res.string.topology_inspector_edit_device_metadata),
                                style = MaterialTheme.typography.labelLarge,
                                color = InfraMapPurple,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun InspectorHeader(
    node: TopologyNode,
    onDismiss: () -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = node.label,
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = InfraMapTextPrimary,
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = stringResource(Res.string.topology_inspector_node_id, node.id),
                style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                color = InfraMapTextSecondary,
            )
        }

        IconButton(onClick = onDismiss) {
            Icon(
                imageVector = Icons.Default.Close,
                contentDescription = stringResource(Res.string.topology_inspector_close_sheet),
                tint = InfraMapTextSecondary,
            )
        }
    }
}

@Composable
private fun InspectorSection(
    title: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .background(SectionBg, RoundedCornerShape(10.dp))
                .border(1.dp, InfraMapBorder, RoundedCornerShape(10.dp))
                .padding(12.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(bottom = 8.dp),
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = InfraMapPurple,
                modifier = Modifier.padding(end = 6.dp),
            )
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall.copy(fontSize = 13.sp),
                color = InfraMapTextPrimary,
            )
        }
        content()
    }
}

@Composable
private fun DetailRow(
    label: String,
    value: String,
    isMonospace: Boolean = false,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = InfraMapTextSecondary,
        )
        Text(
            text = value,
            style =
                if (isMonospace) {
                    MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace)
                } else {
                    MaterialTheme.typography.bodySmall
                },
            color = InfraMapTextPrimary,
        )
    }
}

private fun mapToDeviceStatus(status: String): DeviceStatus =
    when (status.lowercase()) {
        "active", "online" -> DeviceStatus.ACTIVE
        "staged", "staging" -> DeviceStatus.STAGED
        else -> DeviceStatus.OFFLINE
    }

private fun deriveIpAddress(node: TopologyNode): String {
    val hash = node.id.hashCode().coerceAtLeast(0) % 250 + 1
    return "192.168.1.$hash"
}

private fun deriveMacAddress(node: TopologyNode): String {
    val hex =
        node.id
            .hashCode()
            .toUInt()
            .toString(16)
            .padStart(8, '0')
            .takeLast(6)
    return "00:1A:2B:${hex.substring(0, 2)}:${hex.substring(2, 4)}:${hex.substring(4, 6)}".uppercase()
}

private fun deriveSubnetId(node: TopologyNode): String {
    val hash = node.id.hashCode().coerceAtLeast(0) % 2
    return if (hash == 0) "subnet-192-168-1-0" else "subnet-10-0-0-0"
}
