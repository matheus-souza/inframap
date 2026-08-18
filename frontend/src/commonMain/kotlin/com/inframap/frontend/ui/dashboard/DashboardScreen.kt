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
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Rocket
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import com.inframap.frontend.designsystem.InfraMapIcons
import com.inframap.frontend.designsystem.InfraMapLoadingSkeleton
import com.inframap.frontend.designsystem.InfraMapOutlinedButton
import com.inframap.frontend.designsystem.InfraMapRed
import com.inframap.frontend.generated.resources.Res
import com.inframap.frontend.generated.resources.common_close
import com.inframap.frontend.generated.resources.common_refresh
import com.inframap.frontend.generated.resources.common_retry
import com.inframap.frontend.generated.resources.dashboard_active_devices
import com.inframap.frontend.generated.resources.dashboard_active_devices_subtitle
import com.inframap.frontend.generated.resources.dashboard_discovery_sources
import com.inframap.frontend.generated.resources.dashboard_discovery_sources_subtitle
import com.inframap.frontend.generated.resources.dashboard_health_checking
import com.inframap.frontend.generated.resources.dashboard_health_core
import com.inframap.frontend.generated.resources.dashboard_health_degraded
import com.inframap.frontend.generated.resources.dashboard_health_healthy
import com.inframap.frontend.generated.resources.dashboard_health_version
import com.inframap.frontend.generated.resources.dashboard_staged_devices
import com.inframap.frontend.generated.resources.dashboard_staged_devices_subtitle
import com.inframap.frontend.generated.resources.dashboard_subtitle
import com.inframap.frontend.generated.resources.dashboard_system_health
import com.inframap.frontend.generated.resources.dashboard_title
import com.inframap.frontend.generated.resources.dashboard_welcome_cta_discovery
import com.inframap.frontend.generated.resources.dashboard_welcome_cta_subnet
import com.inframap.frontend.generated.resources.dashboard_welcome_subtitle
import com.inframap.frontend.generated.resources.dashboard_welcome_title
import org.jetbrains.compose.resources.stringResource

@Suppress("LongParameterList")
@Composable
fun DashboardScreen(
    state: DashboardUiState,
    onRefresh: () -> Unit,
    onDismissError: () -> Unit,
    onStartAutoSetup: () -> Unit,
    onDismissAutoSetup: () -> Unit,
    onNavigateToSubnets: () -> Unit,
    onNavigateToDiscovery: () -> Unit,
    onNavigateToStaging: () -> Unit,
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

        if (state.errorMessage != null && !state.isErrorDismissed) {
            DashboardErrorToast(
                errorMessage = state.errorMessage.asString(),
                onRefresh = onRefresh,
                onDismiss = onDismissError,
            )
            Spacer(modifier = Modifier.height(24.dp))
        }

        DashboardContent(
            state = state,
            onStartAutoSetup = onStartAutoSetup,
            onDismissAutoSetup = onDismissAutoSetup,
            onNavigateToSubnets = onNavigateToSubnets,
            onNavigateToDiscovery = onNavigateToDiscovery,
            onNavigateToStaging = onNavigateToStaging,
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
                text = stringResource(Res.string.dashboard_title),
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Text(
                text = stringResource(Res.string.dashboard_subtitle),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        InfraMapButton(
            text = stringResource(Res.string.common_refresh),
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
                    text = stringResource(Res.string.common_retry),
                    onClick = onRefresh,
                )
                Spacer(modifier = Modifier.width(8.dp))
                IconButton(onClick = onDismiss) {
                    Icon(
                        imageVector = Icons.Filled.Close,
                        contentDescription =
                            stringResource(Res.string.common_close),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DashboardContent(
    state: DashboardUiState,
    onStartAutoSetup: () -> Unit,
    onDismissAutoSetup: () -> Unit,
    onNavigateToSubnets: () -> Unit,
    onNavigateToDiscovery: () -> Unit,
    onNavigateToStaging: () -> Unit,
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

    if (state.autoSetup.isVisible) {
        AutoSetupBanner(
            autoSetup = state.autoSetup,
            onStart = onStartAutoSetup,
            onDismiss = onDismissAutoSetup,
            onNavigateToStaging = onNavigateToStaging,
        )
        Spacer(modifier = Modifier.height(16.dp))
    } else if (isEmpty && state.errorMessage == null) {
        DashboardWelcomeBanner(
            onNavigateToSubnets = onNavigateToSubnets,
            onNavigateToDiscovery = onNavigateToDiscovery,
        )
        Spacer(modifier = Modifier.height(16.dp))
    }

    DashboardMetrics(state = state)
}

@Composable
private fun AutoSetupBanner(
    autoSetup: AutoSetupState,
    onStart: () -> Unit,
    onDismiss: () -> Unit,
    onNavigateToStaging: () -> Unit,
) {
    when (autoSetup.phase) {
        AutoSetupPhase.COMPLETED ->
            AutoSetupCompletedCard(
                deviceCount = autoSetup.discoveredDeviceCount,
                onNavigateToStaging = onNavigateToStaging,
            )
        else ->
            AutoSetupIdleCard(
                autoSetup = autoSetup,
                onStart = onStart,
                onDismiss = onDismiss,
            )
    }
}

@Composable
private fun AutoSetupIdleCard(
    autoSetup: AutoSetupState,
    onStart: () -> Unit,
    onDismiss: () -> Unit,
) {
    val isInProgress = autoSetup.phase != AutoSetupPhase.IDLE
    val networkCount = autoSetup.detectedInterfaces.size
    val phaseText =
        when (autoSetup.phase) {
            AutoSetupPhase.CREATING_SUBNETS -> "Criando sub-redes..."
            AutoSetupPhase.CREATING_SOURCES -> "Configurando descoberta..."
            AutoSetupPhase.SCANNING -> "Escaneando redes..."
            else -> null
        }

    InfraMapCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Icon(
                imageVector = InfraMapIcons.Lan,
                contentDescription = null,
                modifier = Modifier.size(40.dp),
                tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f),
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = "Detectamos $networkCount rede${if (networkCount > 1) "s" else ""} no servidor",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(modifier = Modifier.height(4.dp))
            AutoSetupIdleCardActions(
                autoSetup = autoSetup,
                isInProgress = isInProgress,
                phaseText = phaseText,
                onStart = onStart,
                onDismiss = onDismiss,
            )
        }
    }
}

@Composable
private fun AutoSetupIdleCardActions(
    autoSetup: AutoSetupState,
    isInProgress: Boolean,
    phaseText: String?,
    onStart: () -> Unit,
    onDismiss: () -> Unit,
) {
    Text(
        text = "Deseja configurar a descoberta automática de dispositivos?",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    if (autoSetup.errorMessage != null) {
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = autoSetup.errorMessage.asString(),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error,
        )
    }
    Spacer(modifier = Modifier.height(16.dp))
    if (isInProgress && phaseText != null) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = phaseText,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary,
            )
        }
    } else {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            InfraMapButton(
                text = "Configurar Automaticamente",
                onClick = onStart,
            )
            InfraMapOutlinedButton(
                text = "Agora não",
                onClick = onDismiss,
            )
        }
    }
}

@Composable
private fun AutoSetupCompletedCard(
    deviceCount: Int,
    onNavigateToStaging: () -> Unit,
) {
    InfraMapCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Icon(
                imageVector = Icons.Filled.CheckCircle,
                contentDescription = null,
                modifier = Modifier.size(40.dp),
                tint = InfraMapGreen,
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = "Configuração concluída!",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(modifier = Modifier.height(4.dp))
            val plural = if (deviceCount > 1) "s" else ""
            val subtitle =
                if (deviceCount > 0) {
                    "$deviceCount dispositivo$plural encontrado$plural"
                } else {
                    "Nenhum dispositivo encontrado ainda — " +
                        "a descoberta continuará automaticamente."
                }
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (deviceCount > 0) {
                Spacer(modifier = Modifier.height(16.dp))
                InfraMapButton(
                    text = "Ver Dispositivos em Staging",
                    onClick = onNavigateToStaging,
                )
            }
        }
    }
}

@Composable
private fun DashboardWelcomeBanner(
    onNavigateToSubnets: () -> Unit,
    onNavigateToDiscovery: () -> Unit,
) {
    InfraMapEmptyState(
        icon = Icons.Filled.Rocket,
        title = stringResource(Res.string.dashboard_welcome_title),
        subtitle = stringResource(Res.string.dashboard_welcome_subtitle),
        ctaLabel = stringResource(Res.string.dashboard_welcome_cta_subnet),
        onCtaClick = onNavigateToSubnets,
        extraActions = {
            InfraMapOutlinedButton(
                text = stringResource(Res.string.dashboard_welcome_cta_discovery),
                onClick = onNavigateToDiscovery,
            )
        },
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
            title = stringResource(Res.string.dashboard_active_devices),
            value = state.totalActiveDevices.toString(),
            subtitle = stringResource(Res.string.dashboard_active_devices_subtitle),
            icon = InfraMapIcons.Dns,
            modifier = Modifier.width(260.dp),
        )
        MetricCard(
            title = stringResource(Res.string.dashboard_staged_devices),
            value = state.totalStagedDevices.toString(),
            subtitle = stringResource(Res.string.dashboard_staged_devices_subtitle),
            icon = InfraMapIcons.MoveToInbox,
            modifier = Modifier.width(260.dp),
        )
        HealthMetricCard(
            isHealthy = state.isSystemHealthy,
            version = state.systemVersion,
            icon = Icons.Filled.CheckCircle,
            modifier = Modifier.width(260.dp),
        )
        MetricCard(
            title = stringResource(Res.string.dashboard_discovery_sources),
            value = state.totalDiscoverySources.toString(),
            subtitle =
                stringResource(Res.string.dashboard_discovery_sources_subtitle),
            icon = InfraMapIcons.Radar,
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
                    contentDescription = null,
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
    val healthTitle = stringResource(Res.string.dashboard_system_health)
    val statusText =
        when (isHealthy) {
            true -> stringResource(Res.string.dashboard_health_healthy)
            false -> stringResource(Res.string.dashboard_health_degraded)
            null -> stringResource(Res.string.dashboard_health_checking)
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
                modifier =
                    Modifier.semantics {
                        contentDescription = "$healthTitle KPI Card"
                    },
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp),
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = healthTitle,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            HealthStatusRow(
                statusText = statusText,
                dotColor = dotColor,
                version = version,
            )
        }
    }
}

@Composable
private fun HealthStatusRow(
    statusText: String,
    dotColor: androidx.compose.ui.graphics.Color,
    version: String,
) {
    Spacer(modifier = Modifier.height(8.dp))
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier =
                Modifier
                    .size(12.dp)
                    .clip(CircleShape)
                    .background(dotColor),
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
        text =
            if (version.isNotEmpty()) {
                stringResource(Res.string.dashboard_health_version, version)
            } else {
                stringResource(Res.string.dashboard_health_core)
            },
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}
