package com.inframap.frontend.ui.subnets

import com.inframap.frontend.data.api.ApiResult
import com.inframap.frontend.domain.usecase.subnet.GetSubnetAddressMapUseCase
import com.inframap.frontend.generated.resources.Res
import com.inframap.frontend.generated.resources.subnet_addresses_error_load
import com.inframap.frontend.ui.base.BaseViewModel
import com.inframap.frontend.ui.util.UiText
import kotlinx.coroutines.CoroutineScope

/** The address map of one subnet: which addresses are free, in use, reserved or in conflict. */
class SubnetAddressMapViewModel(
    private val subnetId: String,
    private val getSubnetAddressMapUseCase: GetSubnetAddressMapUseCase,
    scope: CoroutineScope? = null,
) : BaseViewModel<SubnetAddressMapUiState>(SubnetAddressMapUiState(subnetId = subnetId), scope) {
    init {
        load()
    }

    fun load() {
        updateState { it.copy(isLoading = true, errorMessage = null) }
        launchJob("load") {
            when (val result = getSubnetAddressMapUseCase(subnetId)) {
                is ApiResult.Success -> updateState { it.copy(isLoading = false, map = result.data) }
                else ->
                    updateState {
                        it.copy(
                            isLoading = false,
                            errorMessage = mapError(result, UiText.Resource(Res.string.subnet_addresses_error_load)),
                        )
                    }
            }
        }
    }

    fun onAddressSelected(address: String) {
        updateState { it.copy(selectedAddress = if (it.selectedAddress == address) null else address) }
    }
}
