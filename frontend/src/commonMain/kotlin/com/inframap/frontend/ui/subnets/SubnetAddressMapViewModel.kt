package com.inframap.frontend.ui.subnets

import com.inframap.frontend.data.api.ApiResult
import com.inframap.frontend.domain.model.SubnetAddressMap
import com.inframap.frontend.domain.usecase.subnet.GetSubnetAddressMapUseCase
import com.inframap.frontend.generated.resources.Res
import com.inframap.frontend.generated.resources.subnet_address_map_error_load
import com.inframap.frontend.ui.base.BaseViewModel
import com.inframap.frontend.ui.util.UiText
import kotlinx.coroutines.CoroutineScope

class SubnetAddressMapViewModel(
    private val subnetId: String,
    private val getSubnetAddressMapUseCase: GetSubnetAddressMapUseCase,
    scope: CoroutineScope? = null,
) : BaseViewModel<SubnetAddressMapUiState>(SubnetAddressMapUiState(), scope = scope) {

    init {
        loadAddressMap()
    }

    fun loadAddressMap() {
        updateState { it.copy(isLoading = true, errorMessage = null) }
        launchJob("fetch_address_map") {
            when (val result = getSubnetAddressMapUseCase(subnetId)) {
                is ApiResult.Success -> {
                    updateState {
                        it.copy(
                            addressMap = result.data,
                            isLoading = false,
                            errorMessage = null,
                        )
                    }
                }
                is ApiResult.Error,
                is ApiResult.NetworkError,
                -> {
                    updateState {
                        it.copy(
                            isLoading = false,
                            errorMessage = mapError(result, UiText.Resource(Res.string.subnet_address_map_error_load)),
                        )
                    }
                }
            }
        }
    }

    fun dismissToast() {
        updateState { it.copy(toastMessage = null) }
    }
}

data class SubnetAddressMapUiState(
    val addressMap: SubnetAddressMap? = null,
    val isLoading: Boolean = false,
    val errorMessage: UiText? = null,
    val toastMessage: UiText? = null,
)
