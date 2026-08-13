package com.inframap.frontend.ui.wizard

import com.inframap.frontend.data.api.ApiResult
import com.inframap.frontend.data.dto.CreateSubnetRequest
import com.inframap.frontend.data.storage.LocalStorage
import com.inframap.frontend.domain.model.NetworkInterface
import com.inframap.frontend.domain.usecase.network.GetNetworkInterfacesUseCase
import com.inframap.frontend.domain.usecase.subnet.CreateSubnetUseCase
import com.inframap.frontend.ui.base.BaseViewModel
import com.inframap.frontend.ui.util.UiText
import kotlinx.coroutines.CoroutineScope

class SetupWizardViewModel(
    private val getNetworkInterfacesUseCase: GetNetworkInterfacesUseCase,
    private val createSubnetUseCase: CreateSubnetUseCase,
    private val localStorage: LocalStorage,
    scope: CoroutineScope? = null,
) : BaseViewModel<SetupWizardUiState>(SetupWizardUiState(), scope) {
    fun checkShouldShow(
        totalSubnets: Long,
        totalActiveDevices: Long,
    ) {
        val dismissed = localStorage.get(KEY_WIZARD_DISMISSED) != null
        val completed = localStorage.get(KEY_WIZARD_COMPLETED) != null
        val isFreshInstall = totalSubnets == 0L && totalActiveDevices == 0L
        updateState { it.copy(isVisible = isFreshInstall && !dismissed && !completed) }
    }

    fun show() {
        updateState { it.copy(isVisible = true, currentStep = 1) }
        loadInterfaces()
    }

    fun dismiss() {
        localStorage.set(KEY_WIZARD_DISMISSED, "true")
        updateState { it.copy(isVisible = false) }
    }

    fun complete() {
        localStorage.set(KEY_WIZARD_COMPLETED, "true")
        updateState { it.copy(isVisible = false) }
    }

    fun loadInterfaces() {
        launchJob("load_interfaces") {
            updateState { it.copy(isLoading = true, errorMessage = null) }
            when (val result = getNetworkInterfacesUseCase()) {
                is ApiResult.Success -> {
                    val interfaces = result.data
                    updateState {
                        it.copy(
                            detectedInterfaces = interfaces,
                            selectedCidrs = interfaces.map { iface -> iface.cidr }.toSet(),
                            isLoading = false,
                        )
                    }
                }
                else -> {
                    updateState {
                        it.copy(
                            isLoading = false,
                            errorMessage = mapError(result),
                        )
                    }
                }
            }
        }
    }

    fun toggleInterface(iface: NetworkInterface) {
        updateState {
            val updated =
                if (iface.cidr in it.selectedCidrs) {
                    it.selectedCidrs - iface.cidr
                } else {
                    it.selectedCidrs + iface.cidr
                }
            it.copy(selectedCidrs = updated)
        }
    }

    fun nextStep() {
        when (currentState.currentStep) {
            1 -> createSubnetsAndAdvance()
            else -> updateState { it.copy(currentStep = it.currentStep + 1) }
        }
    }

    fun previousStep() {
        if (currentState.currentStep > 1) {
            updateState { it.copy(currentStep = it.currentStep - 1) }
        }
    }

    fun dismissError() {
        updateState { it.copy(errorMessage = null) }
    }

    private fun createSubnetsAndAdvance() {
        val selected =
            currentState.detectedInterfaces.filter { it.cidr in currentState.selectedCidrs }
        if (selected.isEmpty()) {
            updateState {
                it.copy(errorMessage = UiText.DynamicString("Selecione ao menos uma rede."))
            }
            return
        }

        launchJob("create_subnets") {
            updateState { it.copy(isLoading = true, errorMessage = null) }
            var created = 0
            for (iface in selected) {
                val result =
                    createSubnetUseCase(
                        CreateSubnetRequest(
                            name = iface.name,
                            cidr = iface.cidr,
                            gatewayIp = iface.gateway.ifEmpty { null },
                            discoveryEnabled = true,
                        ),
                    )
                when (result) {
                    is ApiResult.Success -> created++
                    else -> {
                        updateState {
                            it.copy(
                                isLoading = false,
                                createdSubnetCount = created,
                                errorMessage = mapError(result),
                            )
                        }
                        return@launchJob
                    }
                }
            }
            updateState {
                it.copy(
                    isLoading = false,
                    createdSubnetCount = created,
                    currentStep = 2,
                )
            }
        }
    }

    companion object {
        const val KEY_WIZARD_DISMISSED = "inframap_wizard_dismissed"
        const val KEY_WIZARD_COMPLETED = "inframap_wizard_completed"
        const val TOTAL_STEPS = 3
    }
}
