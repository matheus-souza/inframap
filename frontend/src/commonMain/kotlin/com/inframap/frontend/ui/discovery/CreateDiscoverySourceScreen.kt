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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.inframap.frontend.designsystem.ChipCustomOption
import com.inframap.frontend.designsystem.ChipOption
import com.inframap.frontend.designsystem.InfraMapButton
import com.inframap.frontend.designsystem.InfraMapCard
import com.inframap.frontend.designsystem.InfraMapCheckboxRow
import com.inframap.frontend.designsystem.InfraMapChoiceChipGroup
import com.inframap.frontend.designsystem.InfraMapIcons
import com.inframap.frontend.designsystem.InfraMapOutlinedButton
import com.inframap.frontend.designsystem.InfraMapTextField
import com.inframap.frontend.designsystem.SubnetSuggestionChips

private val sourceTypeChipOptions =
    listOf(
        ChipOption("icmp_sweep", "ICMP Ping", InfraMapIcons.NetworkPing),
        ChipOption("arp_sweep", "ARP Sweep", InfraMapIcons.Lan),
        ChipOption("mdns", "mDNS", InfraMapIcons.Dns),
        ChipOption("proxmox", "Proxmox", InfraMapIcons.Cloud),
        ChipOption("docker", "Docker", InfraMapIcons.ViewInAr),
        ChipOption("unifi", "UniFi", InfraMapIcons.Wifi),
    )

private val scheduleChipOptions =
    listOf(
        ChipOption("", "Manual", InfraMapIcons.PauseCircle),
        ChipOption("*/5 * * * *", "5 min", InfraMapIcons.Timer),
        ChipOption("*/15 * * * *", "15 min", InfraMapIcons.Timer),
        ChipOption("0 * * * *", "1 hora", InfraMapIcons.Schedule),
        ChipOption("0 */6 * * *", "6 horas", InfraMapIcons.Schedule),
        ChipOption("0 0 * * *", "Diário", InfraMapIcons.NightsStay),
    )

private val presetScheduleValues = scheduleChipOptions.map { it.value }.toSet()

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

        SourceTypeSection(
            selectedType = state.sourceType,
            onTypeSelected = actions.onSourceTypeChanged,
            error = state.validationErrors["type"]?.asString(),
        )

        Spacer(modifier = Modifier.height(16.dp))

        SubnetSuggestionChips(
            subnets = state.subnets,
            selectedCidr = state.configCidr.ifEmpty { null },
            isLoading = state.isLoadingSubnets,
            onSubnetSelected = actions.onSubnetSelected,
            modifier = Modifier.fillMaxWidth(),
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

        ScheduleSection(
            selectedCron = state.scheduleCron,
            onCronSelected = actions.onScheduleCronChanged,
        )

        Spacer(modifier = Modifier.height(16.dp))

        InfraMapCheckboxRow(
            checked = state.enabled,
            onCheckedChange = actions.onEnabledChanged,
            label = "Fonte habilitada",
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun SourceTypeSection(
    selectedType: String,
    onTypeSelected: (String) -> Unit,
    error: String?,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        InfraMapChoiceChipGroup(
            options = sourceTypeChipOptions,
            selected = selectedType,
            onSelected = onTypeSelected,
            label = "Tipo de Fonte *",
            modifier = Modifier.fillMaxWidth(),
        )
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

@Composable
private fun ScheduleSection(
    selectedCron: String,
    onCronSelected: (String) -> Unit,
) {
    var customCronDraft by remember { mutableStateOf("*/10 * * * *") }
    val isCustomCron: (String) -> Boolean = { it.isNotEmpty() && it !in presetScheduleValues }

    val scheduleCustomOption =
        ChipCustomOption(
            chipLabel = "Personalizado",
            chipIcon = InfraMapIcons.Tune,
            inputLabel = "Expressão Cron",
            inputPlaceholder = "*/10 * * * *",
            currentValue = if (isCustomCron(selectedCron)) selectedCron else customCronDraft,
            onValueChanged = { newValue ->
                customCronDraft = newValue
                onCronSelected(newValue)
            },
            parseValue = { it },
            formatValue = { it },
            helperText = "Formato cron de 5 campos",
            isCustom = isCustomCron,
        )

    InfraMapChoiceChipGroup(
        options = scheduleChipOptions,
        selected = selectedCron,
        onSelected = onCronSelected,
        label = "Agendamento",
        customOption = scheduleCustomOption,
        modifier = Modifier.fillMaxWidth(),
    )
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
