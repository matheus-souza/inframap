package com.inframap.frontend.ui.dashboard

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.inframap.frontend.designsystem.InfraMapBorder
import com.inframap.frontend.designsystem.InfraMapSurfaceBg
import com.inframap.frontend.designsystem.InfraMapTextPrimary
import com.inframap.frontend.designsystem.InfraMapTextSecondary
import com.inframap.frontend.designsystem.StatusOfflineColor
import com.inframap.frontend.designsystem.StatusOnlineColor
import com.inframap.frontend.designsystem.StatusStagingColor
import com.inframap.frontend.designsystem.StatusWarningColor
import com.inframap.frontend.domain.model.Device

@Composable
fun RecentDevicesWidget(
    devices: List<Device>,
    modifier: Modifier = Modifier,
    onDeviceClick: ((String) -> Unit)? = null,
) {
    Surface(
        modifier =
            modifier
                .border(1.dp, InfraMapBorder, RoundedCornerShape(12.dp))
                .clip(RoundedCornerShape(12.dp)),
        color = InfraMapSurfaceBg,
        shape = RoundedCornerShape(12.dp),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "Dispositivos Recentes",
                    style = MaterialTheme.typography.titleMedium,
                    color = InfraMapTextPrimary,
                )
                Text(
                    text = "Top ${devices.size.coerceAtMost(5)} ativos",
                    style = MaterialTheme.typography.labelSmall,
                    color = InfraMapTextSecondary,
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            if (devices.isEmpty()) {
                Box(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(vertical = 24.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "Nenhum dispositivo encontrado no inventário.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = InfraMapTextSecondary,
                    )
                }
            } else {
                RecentDevicesTableContent(
                    devices = devices.take(5),
                    onDeviceClick = onDeviceClick,
                )
            }
        }
    }
}

@Composable
private fun RecentDevicesTableContent(
    devices: List<Device>,
    onDeviceClick: ((String) -> Unit)?,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
        ) {
            Text(
                text = "Hostname",
                style = MaterialTheme.typography.labelSmall,
                color = InfraMapTextSecondary,
                modifier = Modifier.weight(1.5f),
            )
            Text(
                text = "IP Address",
                style = MaterialTheme.typography.labelSmall,
                color = InfraMapTextSecondary,
                modifier = Modifier.weight(1.2f),
            )
            Text(
                text = "Tipo",
                style = MaterialTheme.typography.labelSmall,
                color = InfraMapTextSecondary,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = "Status",
                style = MaterialTheme.typography.labelSmall,
                color = InfraMapTextSecondary,
                modifier = Modifier.weight(1f),
            )
        }

        HorizontalDivider(color = InfraMapBorder)

        devices.forEachIndexed { index, device ->
            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .then(
                            if (onDeviceClick != null) {
                                Modifier.clickable { onDeviceClick(device.id) }
                            } else {
                                Modifier
                            },
                        ).padding(vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = device.hostname,
                    style = MaterialTheme.typography.bodyMedium,
                    color = InfraMapTextPrimary,
                    modifier = Modifier.weight(1.5f),
                )
                Text(
                    text = device.ipAddress ?: "-",
                    style = MaterialTheme.typography.bodyMedium,
                    color = InfraMapTextSecondary,
                    modifier = Modifier.weight(1.2f),
                )
                Text(
                    text = device.deviceType,
                    style = MaterialTheme.typography.bodySmall,
                    color = InfraMapTextSecondary,
                    modifier = Modifier.weight(1f),
                )
                Box(modifier = Modifier.weight(1f)) {
                    DeviceStatusBadgePill(status = device.status)
                }
            }

            if (index < devices.lastIndex) {
                HorizontalDivider(color = InfraMapBorder.copy(alpha = 0.5f))
            }
        }
    }
}

@Composable
fun DeviceStatusBadgePill(
    status: String,
    modifier: Modifier = Modifier,
) {
    val (color, label) =
        when (status.uppercase()) {
            "ACTIVE", "ONLINE" -> StatusOnlineColor to "Online"
            "WARNING", "WARN" -> StatusWarningColor to "Warning"
            "OFFLINE", "DOWN" -> StatusOfflineColor to "Offline"
            "STAGED", "PENDING" -> StatusStagingColor to "Staging"
            else -> StatusOnlineColor to status.lowercase().replaceFirstChar { it.uppercase() }
        }

    Surface(
        modifier = modifier,
        color = color.copy(alpha = 0.15f),
        shape = RoundedCornerShape(4.dp),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = color,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
        )
    }
}
