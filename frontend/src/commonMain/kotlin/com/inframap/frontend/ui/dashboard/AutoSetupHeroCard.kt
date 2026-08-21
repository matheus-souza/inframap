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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.inframap.frontend.designsystem.InfraMapButton
import com.inframap.frontend.designsystem.InfraMapCard
import com.inframap.frontend.designsystem.InfraMapIcons
import com.inframap.frontend.designsystem.InfraMapOutlinedButton
import com.inframap.frontend.domain.model.NetworkInterface
import com.inframap.frontend.generated.resources.Res
import com.inframap.frontend.generated.resources.dashboard_auto_setup_completed
import com.inframap.frontend.generated.resources.dashboard_auto_setup_creating_sources
import com.inframap.frontend.generated.resources.dashboard_auto_setup_creating_subnets
import com.inframap.frontend.generated.resources.dashboard_auto_setup_desc
import com.inframap.frontend.generated.resources.dashboard_auto_setup_dismiss
import com.inframap.frontend.generated.resources.dashboard_auto_setup_manual
import com.inframap.frontend.generated.resources.dashboard_auto_setup_scanning
import com.inframap.frontend.generated.resources.dashboard_auto_setup_start
import com.inframap.frontend.generated.resources.dashboard_auto_setup_title
import com.inframap.frontend.generated.resources.dashboard_auto_setup_view_staging
import org.jetbrains.compose.resources.stringResource

@Composable
fun AutoSetupHeroCard(
    autoSetup: AutoSetupState,
    onStart: () -> Unit,
    onDismiss: () -> Unit,
    onManualConfig: () -> Unit,
    onNavigateToStaging: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val isInProgress = autoSetup.phase != AutoSetupPhase.IDLE && autoSetup.phase != AutoSetupPhase.COMPLETED

    InfraMapCard(
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(24.dp),
        ) {
            AutoSetupHeroHeader()

            if (autoSetup.detectedInterfaces.isNotEmpty()) {
                Spacer(modifier = Modifier.height(16.dp))
                AutoSetupInterfacesList(interfaces = autoSetup.detectedInterfaces)
            }

            if (autoSetup.errorMessage != null) {
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = autoSetup.errorMessage.asString(),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }

            Spacer(modifier = Modifier.height(20.dp))

            when {
                autoSetup.phase == AutoSetupPhase.COMPLETED -> {
                    AutoSetupCompletedSection(
                        deviceCount = autoSetup.discoveredDeviceCount,
                        onNavigateToStaging = onNavigateToStaging,
                    )
                }

                isInProgress -> {
                    AutoSetupProgressSection(phase = autoSetup.phase)
                }

                else -> {
                    AutoSetupIdleActions(
                        onStart = onStart,
                        onManualConfig = onManualConfig,
                        onDismiss = onDismiss,
                    )
                }
            }
        }
    }
}

@Composable
private fun AutoSetupHeroHeader() {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier =
                Modifier
                    .size(44.dp)
                    .background(
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                        shape = RoundedCornerShape(10.dp),
                    ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = InfraMapIcons.Radar,
                contentDescription = null,
                modifier = Modifier.size(24.dp),
                tint = MaterialTheme.colorScheme.primary,
            )
        }
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = stringResource(Res.string.dashboard_auto_setup_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = stringResource(Res.string.dashboard_auto_setup_desc),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AutoSetupInterfacesList(interfaces: List<NetworkInterface>) {
    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        interfaces.forEach { iface ->
            NetworkInterfaceChip(iface = iface)
        }
    }
}

@Composable
private fun AutoSetupCompletedSection(
    deviceCount: Int,
    onNavigateToStaging: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = stringResource(Res.string.dashboard_auto_setup_completed, deviceCount),
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.primary,
        )
        InfraMapButton(
            text = stringResource(Res.string.dashboard_auto_setup_view_staging),
            onClick = onNavigateToStaging,
        )
    }
}

@Composable
private fun AutoSetupProgressSection(phase: AutoSetupPhase) {
    val phaseText =
        when (phase) {
            AutoSetupPhase.CREATING_SUBNETS -> stringResource(Res.string.dashboard_auto_setup_creating_subnets)
            AutoSetupPhase.CREATING_SOURCES -> stringResource(Res.string.dashboard_auto_setup_creating_sources)
            AutoSetupPhase.SCANNING -> stringResource(Res.string.dashboard_auto_setup_scanning)
            else -> ""
        }

    Column(modifier = Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            CircularProgressIndicator(
                modifier = Modifier.size(16.dp),
                strokeWidth = 2.dp,
                color = MaterialTheme.colorScheme.primary,
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = phaseText,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary,
            )
        }
        Spacer(modifier = Modifier.height(8.dp))
        LinearProgressIndicator(
            modifier = Modifier.fillMaxWidth(),
            color = MaterialTheme.colorScheme.primary,
            trackColor = MaterialTheme.colorScheme.surfaceVariant,
        )
    }
}

@Composable
private fun AutoSetupIdleActions(
    onStart: () -> Unit,
    onManualConfig: () -> Unit,
    onDismiss: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        InfraMapButton(
            text = stringResource(Res.string.dashboard_auto_setup_start),
            onClick = onStart,
        )
        InfraMapOutlinedButton(
            text = stringResource(Res.string.dashboard_auto_setup_manual),
            onClick = onManualConfig,
        )
        Spacer(modifier = Modifier.weight(1f))
        InfraMapOutlinedButton(
            text = stringResource(Res.string.dashboard_auto_setup_dismiss),
            onClick = onDismiss,
        )
    }
}

@Composable
private fun NetworkInterfaceChip(
    iface: NetworkInterface,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(6.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "${iface.name}: ",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = iface.cidr.ifEmpty { iface.ip },
                style = MaterialTheme.typography.labelMedium,
                fontFamily = FontFamily.Monospace,
                color = MaterialTheme.colorScheme.primary,
            )
        }
    }
}
