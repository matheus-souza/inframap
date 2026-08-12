package com.inframap.frontend.ui.dashboard

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Rocket
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.inframap.frontend.designsystem.InfraMapButton
import com.inframap.frontend.designsystem.InfraMapEmptyState
import com.inframap.frontend.designsystem.InfraMapLoadingSkeleton
import com.inframap.frontend.designsystem.InfraMapTextPrimary
import com.inframap.frontend.designsystem.InfraMapTextSecondary

@Composable
fun DashboardScreen(
    state: DashboardUiState,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier,
    onDeviceClick: ((String) -> Unit)? = null,
    onStagingClick: (() -> Unit)? = null,
) {
    Column(
        modifier =
            modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
    ) {
        DashboardHeader(isLoading = state.isLoading, onRefresh = onRefresh)
        Spacer(modifier = Modifier.height(24.dp))

        if (state.errorMessage != null) {
            DashboardErrorBanner(errorMessage = state.errorMessage.asString(), onRefresh = onRefresh)
            Spacer(modifier = Modifier.height(24.dp))
        }

        DashboardContent(
            state = state,
            onDeviceClick = onDeviceClick,
            onStagingClick = onStagingClick,
        )
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
                text = "Dashboard Overview",
                style = MaterialTheme.typography.headlineMedium,
                color = InfraMapTextPrimary,
            )
            Text(
                text = "Visão geral da infraestrutura e monitoramento em tempo real",
                style = MaterialTheme.typography.bodyMedium,
                color = InfraMapTextSecondary,
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

@Composable
private fun DashboardContent(
    state: DashboardUiState,
    onDeviceClick: ((String) -> Unit)?,
    onStagingClick: (() -> Unit)?,
) {
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

    DashboardOverviewLayout(
        state = state,
        onDeviceClick = onDeviceClick,
        onStagingClick = onStagingClick,
    )
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
private fun DashboardOverviewLayout(
    state: DashboardUiState,
    onDeviceClick: ((String) -> Unit)?,
    onStagingClick: (() -> Unit)?,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        DashboardKpiRow(
            state = state,
            onStagingClick = onStagingClick,
        )

        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(20.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
            maxItemsInEachRow = 2,
        ) {
            RecentDevicesWidget(
                devices = state.recentDevices,
                onDeviceClick = onDeviceClick,
                modifier = Modifier.weight(1.5f, fill = true),
            )

            LiveEventsWidget(
                events = state.liveEvents,
                modifier = Modifier.weight(1f, fill = true),
            )
        }
    }
}
