package com.inframap.frontend.ui.dashboard

data class DashboardUiState(
    val totalActiveDevices: Long = 0,
    val totalStagedDevices: Long = 0,
    val isSystemHealthy: Boolean? = null,
    val systemVersion: String = "",
    val totalDiscoverySources: Long = 0,
    val isLoading: Boolean = true,
    val errorMessage: String? = null,
)

sealed class DashboardEffect {
    data class ShowToast(
        val message: String,
    ) : DashboardEffect()
}
