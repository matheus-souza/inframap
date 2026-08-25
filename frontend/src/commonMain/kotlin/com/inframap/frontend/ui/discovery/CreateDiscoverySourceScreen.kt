package com.inframap.frontend.ui.discovery

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.inframap.frontend.designsystem.InfraMapButton
import com.inframap.frontend.designsystem.InfraMapCard
import com.inframap.frontend.designsystem.InfraMapOutlinedButton
import com.inframap.frontend.designsystem.InfraMapTextField

private val sourceTypes =
    listOf(
        "icmp_sweep" to "ICMP Sweep (Ping)",
        "arp_sweep" to "ARP Sweep",
        "mdns" to "mDNS Discovery",
        "proxmox" to "Proxmox",
        "docker" to "Docker",
        "unifi" to "UniFi",
    )

private val schedulePresets =
    listOf(
        "" to "Manual (sem agendamento)",
        "*/5 * * * *" to "A cada 5 minutos",
        "0 * * * *" to "A cada hora",
        "0 */6 * * *" to "A cada 6 horas",
        "0 0 * * *" to "Diariamente (meia-noite)",
    )

@Composable
fun CreateDiscoverySourceScreen(
    state: CreateDiscoverySourceUiState,
    actions: CreateDiscoverySourceActions,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier.fillMaxSize().padding(24.dp)) {
        Column(modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState())) {
            CreateDiscoverySourceHeader()

            Spacer(modifier = Modifier.height(20.dp))

            InfraMapCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.fillMaxWidth().padding(24.dp)) {
                    if (state.errorMessage != null) {
                        Text(
                            text = state.errorMessage.asString(),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.error,
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                    }

                    CreateDiscoverySourceFormFields(state = state, actions = actions)

                    Spacer(modifier = Modifier.height(24.dp))

                    CreateDiscoverySourceFormActions(
                        isSubmitting = state.isSubmitting,
                        onCancelClicked = actions.onCancelClicked,
                        onSubmitClicked = actions.onSubmitClicked,
                    )
                }
            }
        }
    }
}

@Composable
private fun CreateDiscoverySourceFormFields(
    state: CreateDiscoverySourceUiState,
    actions: CreateDiscoverySourceActions,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        InfraMapTextField(
            value = state.name,
            onValueChange = actions.onNameChanged,
            label = "Nome da Fonte *",
            error = state.validationErrors["name"]?.asString(),
            modifier = Modifier.fillMaxWidth(),
        )

        Spacer(modifier = Modifier.height(16.dp))

        SourceTypeDropdown(
            selectedType = state.sourceType,
            onTypeSelected = actions.onSourceTypeChanged,
            error = state.validationErrors["type"]?.asString(),
        )

        Spacer(modifier = Modifier.height(16.dp))

        InfraMapTextField(
            value = state.configCidr,
            onValueChange = actions.onConfigCidrChanged,
            label = "CIDR Alvo (ex: 192.168.1.0/24)",
            error = state.validationErrors["cidr"]?.asString(),
            modifier = Modifier.fillMaxWidth(),
        )

        Spacer(modifier = Modifier.height(16.dp))

        ScheduleDropdown(
            selectedCron = state.scheduleCron,
            onCronSelected = actions.onScheduleCronChanged,
        )

        Spacer(modifier = Modifier.height(16.dp))

        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Checkbox(
                checked = state.enabled,
                onCheckedChange = actions.onEnabledChanged,
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "Fonte habilitada",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SourceTypeDropdown(
    selectedType: String,
    onTypeSelected: (String) -> Unit,
    error: String?,
) {
    var expanded by remember { mutableStateOf(false) }
    val displayLabel = sourceTypes.firstOrNull { it.first == selectedType }?.second ?: ""

    Column {
        ExposedDropdownMenuBox(
            expanded = expanded,
            onExpandedChange = { expanded = it },
        ) {
            OutlinedTextField(
                value = displayLabel,
                onValueChange = {},
                readOnly = true,
                label = { Text("Tipo de Fonte *") },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                isError = error != null,
                modifier = Modifier.fillMaxWidth().menuAnchor(MenuAnchorType.PrimaryNotEditable),
            )
            ExposedDropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false },
            ) {
                sourceTypes.forEach { (value, label) ->
                    DropdownMenuItem(
                        text = { Text(label) },
                        onClick = {
                            onTypeSelected(value)
                            expanded = false
                        },
                    )
                }
            }
        }
        if (error != null) {
            Text(
                text = error,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(start = 16.dp, top = 4.dp),
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ScheduleDropdown(
    selectedCron: String,
    onCronSelected: (String) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val isPreset = schedulePresets.any { it.first == selectedCron }
    val displayLabel =
        if (isPreset) {
            schedulePresets.first { it.first == selectedCron }.second
        } else if (selectedCron.isNotEmpty()) {
            "Personalizado: $selectedCron"
        } else {
            schedulePresets.first().second
        }

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it },
    ) {
        OutlinedTextField(
            value = displayLabel,
            onValueChange = {},
            readOnly = true,
            label = { Text("Agendamento") },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier.fillMaxWidth().menuAnchor(MenuAnchorType.PrimaryNotEditable),
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            schedulePresets.forEach { (value, label) ->
                DropdownMenuItem(
                    text = { Text(label) },
                    onClick = {
                        onCronSelected(value)
                        expanded = false
                    },
                )
            }
        }
    }
}

@Composable
private fun CreateDiscoverySourceFormActions(
    isSubmitting: Boolean,
    onCancelClicked: () -> Unit,
    onSubmitClicked: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.End,
    ) {
        InfraMapOutlinedButton(
            text = "Cancelar",
            onClick = onCancelClicked,
            enabled = !isSubmitting,
        )
        Spacer(modifier = Modifier.width(12.dp))
        InfraMapButton(
            text = if (isSubmitting) "Criando..." else "Criar Fonte",
            onClick = onSubmitClicked,
            enabled = !isSubmitting,
        )
    }
}

@Composable
private fun CreateDiscoverySourceHeader() {
    Column {
        Text(
            text = "Nova Fonte de Descoberta",
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Text(
            text = "Configure uma fonte para varredura automatica de dispositivos na rede",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
        )
    }
}
