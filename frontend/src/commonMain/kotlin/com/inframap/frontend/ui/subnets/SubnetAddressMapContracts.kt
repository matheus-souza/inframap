package com.inframap.frontend.ui.subnets

import com.inframap.frontend.data.dto.SubnetAddressDto
import com.inframap.frontend.data.dto.SubnetAddressMapResponse
import com.inframap.frontend.ui.util.UiText

data class SubnetAddressMapUiState(
    val subnetId: String,
    val isLoading: Boolean = true,
    val map: SubnetAddressMapResponse? = null,
    val selectedAddress: String? = null,
    val errorMessage: UiText? = null,
) {
    /** The selected address, or null when nothing is selected or the map is not loaded. */
    val selected: SubnetAddressDto? get() = map?.addresses?.firstOrNull { it.address == selectedAddress }
}

data class SubnetAddressMapActions(
    val onAddressSelected: (String) -> Unit,
    val onDeviceClicked: (String) -> Unit,
    val onEditSubnetClicked: () -> Unit,
    val onBackClicked: () -> Unit,
    val onRetryClicked: () -> Unit,
)
