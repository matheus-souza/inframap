@file:Suppress("ktlint:standard:filename", "MatchingDeclarationName")

package com.inframap.frontend.ui.wizard

import com.inframap.frontend.domain.model.NetworkInterface
import com.inframap.frontend.ui.util.UiText

data class SetupWizardUiState(
    val currentStep: Int = 1,
    val isVisible: Boolean = false,
    val isLoading: Boolean = false,
    val errorMessage: UiText? = null,
    val detectedInterfaces: List<NetworkInterface> = emptyList(),
    val selectedCidrs: Set<String> = emptySet(),
    val createdSubnetCount: Int = 0,
    val scanType: ScanType = ScanType.FULL,
    val scheduleFrequency: ScheduleFrequency = ScheduleFrequency.EVERY_HOUR,
    val createdSourceIds: List<String> = emptyList(),
)

enum class ScanType(
    val label: String,
    val apiType: String,
) {
    ICMP("Varredura ICMP (ping) — rápida, básica", "icmp"),
    ARP_DNS("ARP + DNS — mais detalhes, mais lento", "arp_dns"),
    FULL("Completo (ICMP+ARP+DNS) — recomendado", "full"),
}

enum class ScheduleFrequency(
    val label: String,
    val cron: String?,
) {
    EVERY_15_MIN("A cada 15 minutos", "*/15 * * * *"),
    EVERY_HOUR("A cada hora", "0 * * * *"),
    EVERY_6_HOURS("A cada 6 horas", "0 */6 * * *"),
    EVERY_24_HOURS("A cada 24 horas", "0 0 * * *"),
    MANUAL("Manual", null),
}

data class SetupWizardActions(
    val onDismiss: () -> Unit,
    val onNext: () -> Unit,
    val onBack: () -> Unit,
    val onToggleInterface: (NetworkInterface) -> Unit,
    val onDismissError: () -> Unit,
    val onSelectScanType: (ScanType) -> Unit,
    val onSelectFrequency: (ScheduleFrequency) -> Unit,
)
