package com.inframap.frontend.ui.topology

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Lan
import androidx.compose.material.icons.filled.Radar
import androidx.compose.material3.Divider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.inframap.frontend.designsystem.DeviceStatus
import com.inframap.frontend.designsystem.InfraMapBorder
import com.inframap.frontend.designsystem.InfraMapButton
import com.inframap.frontend.designsystem.InfraMapPurple
import com.inframap.frontend.designsystem.InfraMapStatusBadge
import com.inframap.frontend.designsystem.InfraMapSurfaceBg
import com.inframap.frontend.designsystem.InfraMapTextPrimary
import com.inframap.frontend.designsystem.InfraMapTextSecondary
import com.inframap.frontend.designsystem.StatusOnline
import com.inframap.frontend.domain.model.TopologyNode

private val SectionBg = Color(0xFF27272A).copy(alpha = 0.4f)

@Suppress("LongMethod")
@Composable
fun DeviceInspectorSheet(
    node: TopologyNode,
    onDismiss: () -> Unit,
    onTriggerScan: (String) -> Unit,
    onEditMetadata: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier =
            modifier
                .width(360.dp)
                .fillMaxHeight()
                .border(1.dp, InfraMapBorder),
        color = InfraMapSurfaceBg,
        shadowElevation = 12.dp,
    ) {
        Column(
            modifier = Modifier.fillMaxHeight(),
        ) {
            // Sheet Header
            InspectorHeader(
                node = node,
                onDismiss = onDismiss,
            )

            Divider(color = InfraMapBorder, thickness = 1.dp)

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
                InspectorSection(title = "Network Identity", icon = Icons.Default.Dns) {
                    DetailRow(label = "Hostname", value = node.label)
                    DetailRow(
                        label = "IP Address",
                        value = deriveIpAddress(node),
                        isMonospace = true,
                    )
                    DetailRow(
                        label = "MAC Address",
                        value = deriveMacAddress(node),
                        isMonospace = true,
                    )
                    DetailRow(
                        label = "Subnet ID",
                        value = deriveSubnetId(node),
                        isMonospace = true,
                    )
                }

                // Section 2: Hardware & Status
                InspectorSection(title = "Device Specs & Status", icon = Icons.Default.Lan) {
                    DetailRow(label = "Device Type", value = node.deviceType.replaceFirstChar { it.uppercase() })
                    Row(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = "Status",
                            style = MaterialTheme.typography.bodySmall,
                            color = InfraMapTextSecondary,
                        )
                        InfraMapStatusBadge(status = mapToDeviceStatus(node.status))
                    }
                }

                // Section 3: Active Interfaces & Latency
                InspectorSection(title = "Active Interfaces & Health", icon = Icons.Default.Radar) {
                    DetailRow(
                        label = "Interfaces",
                        value = "eth0 (active), eth1 (standby)",
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
                            text = "ICMP Ping Latency",
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
                        label = "Discovery Provenance",
                        value = "SNMP v2c / LLDP Discovery",
                    )
                }
            }

            Divider(color = InfraMapBorder, thickness = 1.dp)

            // Sheet Footer Actions
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                InfraMapButton(
                    text = "Trigger Active Scan",
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
                                contentDescription = "Edit Metadata",
                                tint = InfraMapPurple,
                                modifier = Modifier.padding(end = 8.dp),
                            )
                            Text(
                                text = "Edit Device Metadata",
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
                text = "ID: ${node.id}",
                style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                color = InfraMapTextSecondary,
            )
        }

        IconButton(onClick = onDismiss) {
            Icon(
                imageVector = Icons.Default.Close,
                contentDescription = "Close Inspector Sheet",
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
