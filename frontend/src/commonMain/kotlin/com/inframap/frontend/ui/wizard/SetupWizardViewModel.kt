package com.inframap.frontend.ui.wizard

import com.inframap.frontend.data.api.ApiResult
import com.inframap.frontend.data.dto.CreateDiscoverySourceRequest
import com.inframap.frontend.data.dto.CreateSubnetRequest
import com.inframap.frontend.data.storage.LocalStorage
import com.inframap.frontend.domain.model.NetworkInterface
import com.inframap.frontend.domain.usecase.discovery.CreateDiscoverySourceUseCase
import com.inframap.frontend.domain.usecase.network.GetNetworkInterfacesUseCase
import com.inframap.frontend.domain.usecase.subnet.CreateSubnetUseCase
import com.inframap.frontend.ui.base.BaseViewModel
import com.inframap.frontend.ui.util.UiText
import kotlinx.coroutines.CoroutineScope

class SetupWizardViewModel(
    private val getNetworkInterfacesUseCase: GetNetworkInterfacesUseCase,
    private val createSubnetUseCase: CreateSubnetUseCase,
    private val createDiscoverySourceUseCase: CreateDiscoverySourceUseCase,
    private val localStorage: LocalStorage,
    scope: CoroutineScope? = null,
) : BaseViewModel<SetupWizardUiState>(SetupWizardUiState(), scope) {
    private val createdCidrs = mutableSetOf<String>()
    private val createdSourceCidrs = mutableSetOf<String>()

    fun checkShouldShow(
        totalSubnets: Long,
        totalActiveDevices: Long,
    ) {
        val dismissed = localStorage.get(KEY_WIZARD_DISMISSED) != null
        val completed = localStorage.get(KEY_WIZARD_COMPLETED) != null
        val isFreshInstall = totalSubnets == 0L && totalActiveDevices == 0L
        val shouldShow = isFreshInstall && !dismissed && !completed
        updateState { it.copy(isVisible = shouldShow) }
        if (shouldShow) loadInterfaces()
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

    fun selectScanType(scanType: ScanType) {
        updateState { it.copy(scanType = scanType) }
    }

    fun selectFrequency(frequency: ScheduleFrequency) {
        updateState { it.copy(scheduleFrequency = frequency) }
    }

    fun nextStep() {
        when (currentState.currentStep) {
            1 -> createSubnetsAndAdvance()
            2 -> createSourcesAndAdvance()
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

    @Suppress("ReturnCount")
    private fun createSubnetsAndAdvance() {
        if (currentState.isLoading) return

        val selected =
            currentState.detectedInterfaces
                .filter { it.cidr in currentState.selectedCidrs }
                .distinctBy { it.cidr }
                .filter { it.cidr !in createdCidrs }
        if (selected.isEmpty() && createdCidrs.isEmpty()) {
            updateState {
                it.copy(errorMessage = UiText.DynamicString("Selecione ao menos uma rede."))
            }
            return
        }
        if (selected.isEmpty()) {
            updateState { it.copy(currentStep = 2, createdSubnetCount = createdCidrs.size) }
            return
        }

        updateState { it.copy(isLoading = true, errorMessage = null) }

        launchJob("create_subnets") {
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
                    is ApiResult.Success -> createdCidrs.add(iface.cidr)
                    else -> {
                        updateState {
                            it.copy(
                                isLoading = false,
                                createdSubnetCount = createdCidrs.size,
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
                    createdSubnetCount = createdCidrs.size,
                    currentStep = 2,
                )
            }
        }
    }

    @Suppress("ReturnCount")
    private fun createSourcesAndAdvance() {
        if (currentState.isLoading) return

        val activeCidrs = createdCidrs.intersect(currentState.selectedCidrs)
        val cidrsToCreate = activeCidrs.filter { it !in createdSourceCidrs }
        if (cidrsToCreate.isEmpty() && createdSourceCidrs.isNotEmpty()) {
            updateState { it.copy(currentStep = 3) }
            return
        }
        if (cidrsToCreate.isEmpty()) {
            updateState {
                it.copy(errorMessage = UiText.DynamicString("Nenhuma rede configurada."))
            }
            return
        }

        val scanType = currentState.scanType
        val cron = currentState.scheduleFrequency.cron
        updateState { it.copy(isLoading = true, errorMessage = null) }

        launchJob("create_sources") {
            val newSourceIds = mutableListOf<String>()
            for (cidr in cidrsToCreate) {
                val result =
                    createDiscoverySourceUseCase(
                        CreateDiscoverySourceRequest(
                            name = "${scanType.label.substringBefore(" —")} — $cidr",
                            type = scanType.apiType,
                            enabled = true,
                            scheduleCron = cron,
                            config = mapOf("cidr" to cidr),
                        ),
                    )
                when (result) {
                    is ApiResult.Success -> {
                        createdSourceCidrs.add(cidr)
                        newSourceIds.add(result.data.id)
                    }
                    else -> {
                        updateState {
                            it.copy(
                                isLoading = false,
                                createdSourceIds = it.createdSourceIds + newSourceIds,
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
                    createdSourceIds = it.createdSourceIds + newSourceIds,
                    currentStep = 3,
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
