@file:Suppress("ktlint:standard:filename", "MatchingDeclarationName")

package com.inframap.frontend.ui.dashboard

import com.inframap.frontend.domain.model.Device
import com.inframap.frontend.ui.util.UiText

enum class DiscoveryEngineStatus {
    IDLE,
    RUNNING,
}

data class DashboardEventItem(
    val id: String,
    val timestamp: String,
    val eventType: String,
    val message: String,
)

data class DashboardUiState(
    val totalActiveDevices: Long = 0,
    val onlineDevicesCount: Long = 0,
    val totalSubnetsMonitored: Long = 0,
    val discoveryEngineStatus: DiscoveryEngineStatus = DiscoveryEngineStatus.IDLE,
    val totalStagedDevices: Long = 0,
    val recentDevices: List<Device> = emptyList(),
    val liveEvents: List<DashboardEventItem> = emptyList(),
    val isSystemHealthy: Boolean? = null,
    val systemVersion: String = "",
    val totalDiscoverySources: Long = 0,
    val isLoading: Boolean = true,
    val errorMessage: UiText? = null,
) {
    val onlinePercentage: Int
        get() =
            if (totalActiveDevices > 0) {
                ((onlineDevicesCount.toDouble() / totalActiveDevices.toDouble()) * 100)
                    .toInt()
                    .coerceIn(0, 100)
            } else {
                0
            }
}
