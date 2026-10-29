package com.inframap.frontend.ui.discovery

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.inframap.frontend.designsystem.ChipOption
import com.inframap.frontend.designsystem.ChipSection
import com.inframap.frontend.designsystem.InfraMapFilterChipGroup
import com.inframap.frontend.designsystem.InfraMapIcons
import com.inframap.frontend.generated.resources.Res
import com.inframap.frontend.generated.resources.collector_name_arp_sweep
import com.inframap.frontend.generated.resources.collector_name_icmp_sweep
import com.inframap.frontend.generated.resources.collector_name_mdns
import com.inframap.frontend.generated.resources.collector_name_reverse_dns
import com.inframap.frontend.generated.resources.collector_name_snmp
import com.inframap.frontend.generated.resources.collector_section_network
import com.inframap.frontend.generated.resources.collector_section_providers
import com.inframap.frontend.generated.resources.discovery_collectors_label
import com.inframap.frontend.generated.resources.tooltip_collector_arp
import com.inframap.frontend.generated.resources.tooltip_collector_icmp
import com.inframap.frontend.generated.resources.tooltip_collector_mdns
import com.inframap.frontend.generated.resources.tooltip_collector_reverse_dns
import com.inframap.frontend.generated.resources.tooltip_collector_snmp
import com.inframap.frontend.generated.resources.tooltip_provider_docker
import com.inframap.frontend.generated.resources.tooltip_provider_proxmox
import org.jetbrains.compose.resources.stringResource

/**
 * The collector selection of a discovery plan: network sweeps and infrastructure providers,
 * offered the same way on the create and edit forms.
 */
@Composable
internal fun DiscoveryCollectorsSection(
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
            required = true,
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
    val icmpTooltip = stringResource(Res.string.tooltip_collector_icmp)
    val arpTooltip = stringResource(Res.string.tooltip_collector_arp)
    val mdnsTooltip = stringResource(Res.string.tooltip_collector_mdns)
    val reverseDnsTooltip = stringResource(Res.string.tooltip_collector_reverse_dns)
    val snmpTooltip = stringResource(Res.string.tooltip_collector_snmp)

    return remember(
        title,
        icmpLabel,
        arpLabel,
        mdnsLabel,
        reverseDnsLabel,
        snmpLabel,
        icmpTooltip,
        arpTooltip,
        mdnsTooltip,
        reverseDnsTooltip,
        snmpTooltip,
    ) {
        ChipSection(
            title = title,
            options =
                listOf(
                    ChipOption("icmp_sweep", icmpLabel, InfraMapIcons.NetworkPing, tooltip = icmpTooltip),
                    ChipOption("arp_sweep", arpLabel, InfraMapIcons.Lan, tooltip = arpTooltip),
                    ChipOption("mdns", mdnsLabel, InfraMapIcons.Dns, tooltip = mdnsTooltip),
                    ChipOption("reverse_dns", reverseDnsLabel, InfraMapIcons.Dns, tooltip = reverseDnsTooltip),
                    ChipOption("snmp", snmpLabel, InfraMapIcons.NetworkCheck, tooltip = snmpTooltip),
                ),
        )
    }
}

@Composable
private fun rememberProvidersSection(): ChipSection<String> {
    // Only providers with an implementation behind them. A selectable card for something
    // that cannot be selected is an offer the product cannot honour, and the "coming soon"
    // label did not make it one.
    val title = stringResource(Res.string.collector_section_providers)
    val proxmoxForm = checkNotNull(ProviderForms.formFor(ProviderForms.PROXMOX))
    val dockerForm = checkNotNull(ProviderForms.formFor(ProviderForms.DOCKER))
    val proxmoxLabel = stringResource(proxmoxForm.label)
    val dockerLabel = stringResource(dockerForm.label)
    val proxmoxTooltip = stringResource(Res.string.tooltip_provider_proxmox)
    val dockerTooltip = stringResource(Res.string.tooltip_provider_docker)

    return remember(
        title,
        proxmoxLabel,
        dockerLabel,
        proxmoxTooltip,
        dockerTooltip,
    ) {
        ChipSection(
            title = title,
            options =
                listOf(
                    ChipOption(
                        value = proxmoxForm.id,
                        label = proxmoxLabel,
                        icon = proxmoxForm.icon,
                        tooltip = proxmoxTooltip,
                    ),
                    ChipOption(
                        value = dockerForm.id,
                        label = dockerLabel,
                        icon = dockerForm.icon,
                        tooltip = dockerTooltip,
                    ),
                ),
        )
    }
}
