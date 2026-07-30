package com.inframap.frontend.ui.dashboard

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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.inframap.frontend.designsystem.InfraMapButton
import com.inframap.frontend.designsystem.InfraMapCard
import com.inframap.frontend.designsystem.InfraMapGreen
import com.inframap.frontend.designsystem.InfraMapLoadingSkeleton
import com.inframap.frontend.designsystem.InfraMapRed

@Composable
fun DashboardScreen(
    state: DashboardUiState,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier =
            modifier
                .fillMaxSize()
                .padding(24.dp),
    ) {
        DashboardHeader(isLoading = state.isLoading, onRefresh = onRefresh)
        Spacer(modifier = Modifier.height(24.dp))

        if (state.errorMessage != null) {
            DashboardErrorBanner(errorMessage = state.errorMessage, onRefresh = onRefresh)
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
private fun DashboardErrorBanner(
    errorMessage: String,
    onRefresh: () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.errorContainer,
        shape = RoundedCornerShape(8.dp),
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = errorMessage,
                color = MaterialTheme.colorScheme.onErrorContainer,
                style = MaterialTheme.typography.bodyMedium,
            )
            InfraMapButton(
                text = "Retry",
                onClick = onRefresh,
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DashboardContent(state: DashboardUiState) {
    if (state.isLoading && state.totalActiveDevices == 0L && state.errorMessage == null) {
        InfraMapLoadingSkeleton(
            lines = 4,
            lineHeight = 100.dp,
            spacing = 16.dp,
        )
    } else {
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
                modifier = Modifier.width(260.dp),
            )

            MetricCard(
                title = "Staged Devices",
                value = state.totalStagedDevices.toString(),
                subtitle = "Awaiting verification",
                modifier = Modifier.width(260.dp),
            )

            HealthMetricCard(
                isHealthy = state.isSystemHealthy,
                version = state.systemVersion,
                modifier = Modifier.width(260.dp),
            )

            MetricCard(
                title = "Discovery Sources",
                value = state.totalDiscoverySources.toString(),
                subtitle = "Configured targets",
                modifier = Modifier.width(260.dp),
            )
        }
    }
}

@Composable
private fun MetricCard(
    title: String,
    value: String,
    subtitle: String,
    modifier: Modifier = Modifier,
) {
    InfraMapCard(modifier = modifier) {
        Column {
            Text(
                text = title,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
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
            Text(
                text = "System Health",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
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
