@file:Suppress("ktlint:standard:filename", "MatchingDeclarationName")

package com.inframap.frontend.ui.dashboard

import com.inframap.frontend.ui.util.UiText

data class DashboardUiState(
    val totalActiveDevices: Long = 0,
    val totalStagedDevices: Long = 0,
    val isSystemHealthy: Boolean? = null,
    val systemVersion: String = "",
    val totalDiscoverySources: Long = 0,
    val isLoading: Boolean = true,
    val errorMessage: UiText? = null,
)
