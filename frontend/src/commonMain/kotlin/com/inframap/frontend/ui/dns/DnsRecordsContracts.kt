package com.inframap.frontend.ui.dns

import com.inframap.frontend.domain.model.DnsRecord
import com.inframap.frontend.ui.util.UiText

data class DnsRecordsUiState(
    val records: List<DnsRecord> = emptyList(),
    val totalItems: Int = 0,
    val currentPage: Int = 1,
    val isLoading: Boolean = true,
    val errorMessage: UiText? = null,
)

data class DnsRecordsActions(
    val onRecordClicked: (DnsRecord) -> Unit = {},
    val onBackClicked: () -> Unit = {},
    val onRefreshClicked: () -> Unit = {},
)
