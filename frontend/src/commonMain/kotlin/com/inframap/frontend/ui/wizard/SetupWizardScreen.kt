package com.inframap.frontend.ui.wizard

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.inframap.frontend.designsystem.InfraMapWizardOverlay
import com.inframap.frontend.domain.model.NetworkInterface
import com.inframap.frontend.ui.wizard.SetupWizardViewModel.Companion.TOTAL_STEPS

@Composable
fun SetupWizardScreen(
    state: SetupWizardUiState,
    actions: SetupWizardActions,
) {
    if (!state.isVisible) return

    val title =
        when (state.currentStep) {
            1 -> "Configurar sua Rede"
            2 -> "Configurar Descoberta"
            3 -> "Executar Primeiro Scan"
            else -> ""
        }

    InfraMapWizardOverlay(
        currentStep = state.currentStep,
        totalSteps = TOTAL_STEPS,
        title = title,
        onDismiss = actions.onDismiss,
        onBack = if (state.currentStep > 1) actions.onBack else null,
        onNext = actions.onNext,
        nextLabel = if (state.currentStep == TOTAL_STEPS) "Concluir" else "Proximo",
        nextEnabled = state.currentStep != 1 || state.selectedCidrs.isNotEmpty(),
        isLoading = state.isLoading,
    ) {
        when (state.currentStep) {
            1 ->
                StepOneContent(
                    interfaces = state.detectedInterfaces,
                    selectedCidrs = state.selectedCidrs,
                    onToggle = actions.onToggleInterface,
                    errorMessage = state.errorMessage?.asString(),
                    onDismissError = actions.onDismissError,
                )
            2 ->
                StepTwoContent(
                    scanType = state.scanType,
                    onSelectScanType = actions.onSelectScanType,
                    scheduleFrequency = state.scheduleFrequency,
                    onSelectFrequency = actions.onSelectFrequency,
                    errorMessage = state.errorMessage?.asString(),
                    onDismissError = actions.onDismissError,
                )
        }
    }
}

@Composable
private fun StepOneContent(
    interfaces: List<NetworkInterface>,
    selectedCidrs: Set<String>,
    onToggle: (NetworkInterface) -> Unit,
    errorMessage: String?,
    onDismissError: () -> Unit,
) {
    Column {
        Text(
            text = "O InfraMap detectou estas redes:",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Spacer(modifier = Modifier.height(12.dp))

        if (interfaces.isEmpty() && errorMessage == null) {
            Text(
                text = "Nenhuma interface de rede detectada.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        interfaces.forEach { iface ->
            InterfaceCheckboxRow(
                iface = iface,
                isSelected = iface.cidr in selectedCidrs,
                onToggle = { onToggle(iface) },
            )
        }

        if (errorMessage != null) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = errorMessage,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.clickable(onClick = onDismissError),
            )
        }
    }
}

@Composable
private fun StepTwoContent(
    scanType: ScanType,
    onSelectScanType: (ScanType) -> Unit,
    scheduleFrequency: ScheduleFrequency,
    onSelectFrequency: (ScheduleFrequency) -> Unit,
    errorMessage: String?,
    onDismissError: () -> Unit,
) {
    Column {
        Text(
            text = "Escolha o tipo de varredura:",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Spacer(modifier = Modifier.height(8.dp))

        ScanType.entries.forEach { type ->
            RadioRow(
                label = type.label,
                selected = scanType == type,
                onClick = { onSelectScanType(type) },
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "Frequência de varredura:",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Spacer(modifier = Modifier.height(8.dp))

        ScheduleFrequency.entries.forEach { freq ->
            RadioRow(
                label = freq.label,
                selected = scheduleFrequency == freq,
                onClick = { onSelectFrequency(freq) },
            )
        }

        if (errorMessage != null) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = errorMessage,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.clickable(onClick = onDismissError),
            )
        }
    }
}

@Composable
private fun RadioRow(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = onClick)
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(start = 4.dp),
        )
    }
}

@Composable
private fun InterfaceCheckboxRow(
    iface: NetworkInterface,
    isSelected: Boolean,
    onToggle: () -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .clickable(onClick = onToggle)
                .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(checked = isSelected, onCheckedChange = { onToggle() })
        Text(
            text = "${iface.name} — ${iface.cidr}",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(start = 8.dp),
        )
    }
}
