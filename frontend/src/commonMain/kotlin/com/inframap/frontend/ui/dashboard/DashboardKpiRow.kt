package com.inframap.frontend.ui.dashboard

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Lan
import androidx.compose.material.icons.filled.MoveToInbox
import androidx.compose.material.icons.filled.Radar
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.inframap.frontend.designsystem.InfraMapBorder
import com.inframap.frontend.designsystem.InfraMapSurfaceBg
import com.inframap.frontend.designsystem.InfraMapTextPrimary
import com.inframap.frontend.designsystem.InfraMapTextSecondary
import com.inframap.frontend.designsystem.StatusOnlineColor
import com.inframap.frontend.designsystem.StatusStagingColor
import com.inframap.frontend.designsystem.StatusWarningColor

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DashboardKpiRow(
    state: DashboardUiState,
    modifier: Modifier = Modifier,
    onStagingClick: (() -> Unit)? = null,
) {
    FlowRow(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        maxItemsInEachRow = 4,
    ) {
        KpiCard(
            title = "Total Dispositivos",
            value = state.totalActiveDevices.toString(),
            icon = Icons.Filled.Dns,
            badgeText = "${state.onlinePercentage}% online",
            badgeColor = StatusOnlineColor,
            subtitle = "Inventário de Ativos",
            modifier = Modifier.weight(1f, fill = true),
        )

        KpiCard(
            title = "Subredes Monitoradas",
            value = state.totalSubnetsMonitored.toString(),
            icon = Icons.Filled.Lan,
            badgeText = "Monitored",
            badgeColor = StatusOnlineColor,
            subtitle = "Subredes ativas",
            modifier = Modifier.weight(1f, fill = true),
        )

        val isRunning = state.discoveryEngineStatus == DiscoveryEngineStatus.RUNNING
        val engineStatusLabel = if (isRunning) "Running" else "Idle"
        val engineStatusColor = if (isRunning) StatusOnlineColor else StatusWarningColor
        KpiCard(
            title = "Discovery Engine",
            value = engineStatusLabel,
            icon = Icons.Filled.Radar,
            badgeText = if (isRunning) "SCANNING" else "READY",
            badgeColor = engineStatusColor,
            subtitle = "Varredura em tempo real",
            modifier = Modifier.weight(1f, fill = true),
        )

        KpiCard(
            title = "Fila de Staging",
            value = state.totalStagedDevices.toString(),
            icon = Icons.Filled.MoveToInbox,
            badgeText = "${state.totalStagedDevices} Pendentes",
            badgeColor = StatusStagingColor,
            subtitle = "Aguardando aprovação",
            onClick = onStagingClick,
            modifier = Modifier.weight(1f, fill = true),
        )
    }
}

@Composable
private fun KpiCard(
    title: String,
    value: String,
    icon: ImageVector,
    badgeText: String,
    badgeColor: Color,
    subtitle: String,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
) {
    Surface(
        modifier =
            modifier
                .border(1.dp, InfraMapBorder, RoundedCornerShape(12.dp))
                .clip(RoundedCornerShape(12.dp))
                .then(
                    if (onClick != null) {
                        Modifier.clickable { onClick() }
                    } else {
                        Modifier
                    },
                ),
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
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = icon,
                        contentDescription = title,
                        tint = InfraMapTextSecondary,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = title,
                        style = MaterialTheme.typography.labelMedium,
                        color = InfraMapTextSecondary,
                    )
                }

                Surface(
                    color = badgeColor.copy(alpha = 0.15f),
                    shape = RoundedCornerShape(6.dp),
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(
                            modifier =
                                Modifier
                                    .size(6.dp)
                                    .clip(CircleShape)
                                    .background(badgeColor)
                                    .semantics { contentDescription = "$title status dot" },
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = badgeText,
                            style = MaterialTheme.typography.labelSmall,
                            color = badgeColor,
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = value,
                style = MaterialTheme.typography.headlineMedium,
                color = InfraMapTextPrimary,
            )

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = InfraMapTextSecondary,
            )
        }
    }
}
