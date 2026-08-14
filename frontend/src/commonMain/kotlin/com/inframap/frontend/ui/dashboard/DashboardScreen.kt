package com.inframap.frontend.ui.dashboard

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.MoveToInbox
import androidx.compose.material.icons.filled.Radar
import androidx.compose.material.icons.filled.Rocket
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.inframap.frontend.designsystem.InfraMapButton
import com.inframap.frontend.designsystem.InfraMapCard
import com.inframap.frontend.designsystem.InfraMapEmptyState
import com.inframap.frontend.designsystem.InfraMapGreen
import com.inframap.frontend.designsystem.InfraMapLoadingSkeleton
import com.inframap.frontend.designsystem.InfraMapRed

@Composable
fun DashboardScreen(
    state: DashboardUiState,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var isErrorDismissed by remember(state.errorMessage) { mutableStateOf(false) }

    Column(
        modifier =
            modifier
                .fillMaxSize()
                .padding(24.dp),
    ) {
        DashboardHeader(isLoading = state.isLoading, onRefresh = onRefresh)
        Spacer(modifier = Modifier.height(24.dp))

        if (state.errorMessage != null && !isErrorDismissed) {
            DashboardErrorToast(
                errorMessage = state.errorMessage.asString(),
                onRefresh = onRefresh,
                onDismiss = { isErrorDismissed = true },
            )
            Spacer(modifier = Modifier.height(24.dp))
        }

        DashboardContent(state = state)
    }
}

@Composable
private fun DashboardHeader(
    isLoading: Boolean,
    onRefresh: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column {
            Text(
                text = "Dashboard",
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Text(
                text = "Infrastructure Overview & Real-Time Metrics",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        InfraMapButton(
            text = "Refresh",
            onClick = onRefresh,
            enabled = !isLoading,
        )
    }
}

@Composable
private fun DashboardErrorToast(
    errorMessage: String,
    onRefresh: () -> Unit,
    onDismiss: () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = RoundedCornerShape(8.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.4f)),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = Icons.Filled.ErrorOutline,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error,
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = errorMessage,
                    color = MaterialTheme.colorScheme.onBackground,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                InfraMapButton(
                    text = "Retry",
                    onClick = onRefresh,
                )
                Spacer(modifier = Modifier.width(8.dp))
                IconButton(onClick = onDismiss) {
                    Icon(
                        imageVector = Icons.Filled.Close,
                        contentDescription = "Fechar",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DashboardContent(state: DashboardUiState) {
    val isEmpty =
        state.totalActiveDevices == 0L &&
            state.totalStagedDevices == 0L &&
            state.totalDiscoverySources == 0L

    if (state.isLoading && isEmpty && state.errorMessage == null) {
        InfraMapLoadingSkeleton(
            lines = 4,
            lineHeight = 100.dp,
            spacing = 16.dp,
        )
        return
    }

    if (isEmpty && state.errorMessage == null) {
        DashboardWelcomeBanner()
        Spacer(modifier = Modifier.height(16.dp))
    }

    DashboardMetrics(state = state)
}

@Composable
private fun DashboardWelcomeBanner() {
    InfraMapEmptyState(
        icon = Icons.Filled.Rocket,
        title = "Bem-vindo ao InfraMap",
        subtitle =
            "Para começar, cadastre uma subrede na seção Subredes e configure " +
                "uma fonte de descoberta para escanear sua rede automaticamente.",
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DashboardMetrics(state: DashboardUiState) {
    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        maxItemsInEachRow = 4,
    ) {
        MetricCard(
            title = "Active Devices",
            value = state.totalActiveDevices.toString(),
            subtitle = "Inventory items",
            icon = Icons.Filled.Dns,
            modifier = Modifier.width(260.dp),
        )
        MetricCard(
            title = "Staged Devices",
            value = state.totalStagedDevices.toString(),
            subtitle = "Awaiting verification",
            icon = Icons.Filled.MoveToInbox,
            modifier = Modifier.width(260.dp),
        )
        HealthMetricCard(
            isHealthy = state.isSystemHealthy,
            version = state.systemVersion,
            icon = Icons.Filled.CheckCircle,
            modifier = Modifier.width(260.dp),
        )
        MetricCard(
            title = "Discovery Sources",
            value = state.totalDiscoverySources.toString(),
            subtitle = "Configured targets",
            icon = Icons.Filled.Radar,
            modifier = Modifier.width(260.dp),
        )
    }
}

@Composable
private fun MetricCard(
    title: String,
    value: String,
    subtitle: String,
    icon: ImageVector,
    modifier: Modifier = Modifier,
) {
    InfraMapCard(modifier = modifier) {
        Column {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.semantics { contentDescription = "$title KPI Card" },
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = "$title icon",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp),
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = title,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = value,
                style = MaterialTheme.typography.headlineLarge,
                color = MaterialTheme.colorScheme.primary,
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun HealthMetricCard(
    isHealthy: Boolean?,
    version: String,
    icon: ImageVector = Icons.Filled.CheckCircle,
    modifier: Modifier = Modifier,
) {
    val statusText =
        when (isHealthy) {
            true -> "Healthy"
            false -> "Degraded"
            null -> "Checking..."
        }

    val dotColor =
        when (isHealthy) {
            true -> InfraMapGreen
            false -> InfraMapRed
            null -> MaterialTheme.colorScheme.onSurfaceVariant
        }

    InfraMapCard(modifier = modifier) {
        Column {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.semantics { contentDescription = "System Health KPI Card" },
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = "System Health icon",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp),
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "System Health",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier =
                        Modifier
                            .size(12.dp)
                            .clip(CircleShape)
                            .background(dotColor)
                            .semantics { contentDescription = "Health indicator" },
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = statusText,
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = if (version.isNotEmpty()) "Version: $version" else "Core platform",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
