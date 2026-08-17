@file:Suppress("ktlint:standard:filename", "MatchingDeclarationName")

package com.inframap.frontend.ui.dashboard

import com.inframap.frontend.domain.model.NetworkInterface
import com.inframap.frontend.ui.util.UiText

data class DashboardUiState(
    val totalActiveDevices: Long = 0,
    val totalStagedDevices: Long = 0,
    val totalSubnets: Long = 0,
    val isSystemHealthy: Boolean? = null,
    val systemVersion: String = "",
    val totalDiscoverySources: Long = 0,
    val isLoading: Boolean = true,
    val errorMessage: UiText? = null,
    val isErrorDismissed: Boolean = false,
    val autoSetup: AutoSetupState = AutoSetupState(),
)

data class AutoSetupState(
    val isVisible: Boolean = false,
    val detectedInterfaces: List<NetworkInterface> = emptyList(),
    val phase: AutoSetupPhase = AutoSetupPhase.IDLE,
    val discoveredDeviceCount: Int = 0,
    val errorMessage: UiText? = null,
)

enum class AutoSetupPhase {
    IDLE,
    CREATING_SUBNETS,
    CREATING_SOURCES,
    SCANNING,
    COMPLETED,
}
