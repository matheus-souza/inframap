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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.inframap.frontend.designsystem.ChipCustomOption
import com.inframap.frontend.designsystem.ChipOption
import com.inframap.frontend.designsystem.ChipSection
import com.inframap.frontend.designsystem.InfraMapButton
import com.inframap.frontend.designsystem.InfraMapCard
import com.inframap.frontend.designsystem.InfraMapCheckboxRow
import com.inframap.frontend.designsystem.InfraMapChoiceChipGroup
import com.inframap.frontend.designsystem.InfraMapFilterChipGroup
import com.inframap.frontend.designsystem.InfraMapIcons
import com.inframap.frontend.designsystem.InfraMapOutlinedButton
import com.inframap.frontend.designsystem.InfraMapTextField
import com.inframap.frontend.designsystem.SubnetSuggestionChips
import com.inframap.frontend.generated.resources.Res
import com.inframap.frontend.generated.resources.chip_coming_soon
import com.inframap.frontend.generated.resources.collector_name_arp_sweep
import com.inframap.frontend.generated.resources.collector_name_docker
import com.inframap.frontend.generated.resources.collector_name_icmp_sweep
import com.inframap.frontend.generated.resources.collector_name_mdns
import com.inframap.frontend.generated.resources.collector_name_proxmox
import com.inframap.frontend.generated.resources.collector_name_reverse_dns
import com.inframap.frontend.generated.resources.collector_name_snmp
import com.inframap.frontend.generated.resources.collector_name_unifi
import com.inframap.frontend.generated.resources.collector_section_network
import com.inframap.frontend.generated.resources.collector_section_providers
import com.inframap.frontend.generated.resources.common_cancel
import com.inframap.frontend.generated.resources.create_discovery_source_cidr_label
import com.inframap.frontend.generated.resources.create_discovery_source_enabled_label
import com.inframap.frontend.generated.resources.create_discovery_source_name_label
import com.inframap.frontend.generated.resources.create_discovery_source_schedule_label
import com.inframap.frontend.generated.resources.create_discovery_source_submit
import com.inframap.frontend.generated.resources.create_discovery_source_submitting
import com.inframap.frontend.generated.resources.create_discovery_source_subtitle
import com.inframap.frontend.generated.resources.create_discovery_source_title
import com.inframap.frontend.generated.resources.discovery_collectors_label
import com.inframap.frontend.generated.resources.schedule_custom_cron_helper
import com.inframap.frontend.generated.resources.schedule_custom_cron_label
import com.inframap.frontend.generated.resources.schedule_preset_15min
import com.inframap.frontend.generated.resources.schedule_preset_1hour
import com.inframap.frontend.generated.resources.schedule_preset_5min
import com.inframap.frontend.generated.resources.schedule_preset_6hours
import com.inframap.frontend.generated.resources.schedule_preset_custom
import com.inframap.frontend.generated.resources.schedule_preset_daily
import com.inframap.frontend.generated.resources.schedule_preset_manual
import org.jetbrains.compose.resources.stringResource

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
            label = stringResource(Res.string.create_discovery_source_name_label),
            error = state.validationErrors["name"]?.asString(),
            modifier = Modifier.fillMaxWidth(),
        )

        Spacer(modifier = Modifier.height(16.dp))

        CollectorsSection(
            selectedCollectors = state.selectedCollectors,
            onSelectionChanged = actions.onCollectorsChanged,
            error = state.validationErrors["collectors"]?.asString(),
        )

        Spacer(modifier = Modifier.height(16.dp))

        SubnetSuggestionChips(
            subnets = state.subnets,
            onSubnetSelected = actions.onSubnetSelected,
            modifier = Modifier.fillMaxWidth(),
        )

        Spacer(modifier = Modifier.height(16.dp))

        InfraMapTextField(
            value = state.configCidr,
            onValueChange = actions.onConfigCidrChanged,
            label = stringResource(Res.string.create_discovery_source_cidr_label),
            error = state.validationErrors["cidr"]?.asString(),
            textStyle = MaterialTheme.typography.bodyLarge.copy(fontFamily = FontFamily.Monospace),
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
            label = stringResource(Res.string.create_discovery_source_enabled_label),
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun CollectorsSection(
    selectedCollectors: Set<String>,
    onSelectionChanged: (Set<String>) -> Unit,
    error: String?,
) {
    val sections = rememberCollectorSections()

    Column(modifier = Modifier.fillMaxWidth()) {
        InfraMapFilterChipGroup(
            sections = sections,
            selected = selectedCollectors,
            onSelectionChanged = onSelectionChanged,
            label = stringResource(Res.string.discovery_collectors_label),
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
private fun rememberCollectorSections(): List<ChipSection<String>> {
    val networkSection = rememberNetworkSection()
    val providersSection = rememberProvidersSection()
    return remember(networkSection, providersSection) {
        listOf(networkSection, providersSection)
    }
}

@Composable
private fun rememberNetworkSection(): ChipSection<String> {
    val title = stringResource(Res.string.collector_section_network)
    val icmpLabel = stringResource(Res.string.collector_name_icmp_sweep)
    val arpLabel = stringResource(Res.string.collector_name_arp_sweep)
    val mdnsLabel = stringResource(Res.string.collector_name_mdns)
    val reverseDnsLabel = stringResource(Res.string.collector_name_reverse_dns)
    val snmpLabel = stringResource(Res.string.collector_name_snmp)

    return remember(title, icmpLabel, arpLabel, mdnsLabel, reverseDnsLabel, snmpLabel) {
        ChipSection(
            title = title,
            options =
                listOf(
                    ChipOption("icmp_sweep", icmpLabel, InfraMapIcons.NetworkPing),
                    ChipOption("arp_sweep", arpLabel, InfraMapIcons.Lan),
                    ChipOption("mdns", mdnsLabel, InfraMapIcons.Dns),
                    ChipOption("reverse_dns", reverseDnsLabel, InfraMapIcons.Dns),
                    ChipOption("snmp", snmpLabel, InfraMapIcons.NetworkCheck),
                ),
        )
    }
}

@Composable
private fun rememberProvidersSection(): ChipSection<String> {
    val title = stringResource(Res.string.collector_section_providers)
    val comingSoonHint = stringResource(Res.string.chip_coming_soon)
    val proxmoxLabel = stringResource(Res.string.collector_name_proxmox)
    val dockerLabel = stringResource(Res.string.collector_name_docker)
    val unifiLabel = stringResource(Res.string.collector_name_unifi)

    return remember(title, comingSoonHint, proxmoxLabel, dockerLabel, unifiLabel) {
        ChipSection(
            title = title,
            options =
                listOf(
                    ChipOption(
                        value = "proxmox",
                        label = proxmoxLabel,
                        icon = InfraMapIcons.Cloud,
                        enabled = false,
                        disabledHint = comingSoonHint,
                        description = comingSoonHint,
                    ),
                    ChipOption(
                        value = "docker",
                        label = dockerLabel,
                        icon = InfraMapIcons.ViewInAr,
                        enabled = false,
                        disabledHint = comingSoonHint,
                        description = comingSoonHint,
                    ),
                    ChipOption(
                        value = "unifi",
                        label = unifiLabel,
                        icon = InfraMapIcons.Wifi,
                        enabled = false,
                        disabledHint = comingSoonHint,
                        description = comingSoonHint,
                    ),
                ),
        )
    }
}

@Composable
private fun rememberScheduleOptions(): Pair<List<ChipOption<String>>, Set<String>> {
    val manual = stringResource(Res.string.schedule_preset_manual)
    val fiveMin = stringResource(Res.string.schedule_preset_5min)
    val fifteenMin = stringResource(Res.string.schedule_preset_15min)
    val oneHour = stringResource(Res.string.schedule_preset_1hour)
    val sixHours = stringResource(Res.string.schedule_preset_6hours)
    val daily = stringResource(Res.string.schedule_preset_daily)

    return remember(manual, fiveMin, fifteenMin, oneHour, sixHours, daily) {
        val options =
            listOf(
                ChipOption("", manual, InfraMapIcons.PauseCircle),
                ChipOption("*/5 * * * *", fiveMin, InfraMapIcons.Timer),
                ChipOption("*/15 * * * *", fifteenMin, InfraMapIcons.Timer),
                ChipOption("0 * * * *", oneHour, InfraMapIcons.Schedule),
                ChipOption("0 */6 * * *", sixHours, InfraMapIcons.Schedule),
                ChipOption("0 0 * * *", daily, InfraMapIcons.NightsStay),
            )
        val values = options.map { it.value }.toSet()
        options to values
    }
}

@Composable
private fun ScheduleSection(
    selectedCron: String,
    onCronSelected: (String) -> Unit,
) {
    var customCronDraft by remember { mutableStateOf("*/10 * * * *") }
    val (scheduleChipOptions, presetValues) = rememberScheduleOptions()
    val isCustomCron: (String) -> Boolean = { it.isNotEmpty() && it !in presetValues }

    val scheduleCustomOption =
        ChipCustomOption(
            chipLabel = stringResource(Res.string.schedule_preset_custom),
            chipIcon = InfraMapIcons.Tune,
            inputLabel = stringResource(Res.string.schedule_custom_cron_label),
            inputPlaceholder = "*/10 * * * *",
            currentValue = if (isCustomCron(selectedCron)) selectedCron else customCronDraft,
            onValueChanged = { newValue ->
                customCronDraft = newValue
                onCronSelected(newValue)
            },
            parseValue = { it },
            formatValue = { it },
            helperText = stringResource(Res.string.schedule_custom_cron_helper),
            isCustom = isCustomCron,
        )

    InfraMapChoiceChipGroup(
        options = scheduleChipOptions,
        selected = selectedCron,
        onSelected = onCronSelected,
        label = stringResource(Res.string.create_discovery_source_schedule_label),
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
            text = stringResource(Res.string.common_cancel),
            onClick = onCancelClicked,
            enabled = !isSubmitting,
        )
        Spacer(modifier = Modifier.width(12.dp))
        InfraMapButton(
            text =
                if (isSubmitting) {
                    stringResource(Res.string.create_discovery_source_submitting)
                } else {
                    stringResource(Res.string.create_discovery_source_submit)
                },
            onClick = onSubmitClicked,
            enabled = !isSubmitting,
        )
    }
}

@Composable
private fun CreateDiscoverySourceHeader() {
    Column {
        Text(
            text = stringResource(Res.string.create_discovery_source_title),
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Text(
            text = stringResource(Res.string.create_discovery_source_subtitle),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
        )
    }
}
