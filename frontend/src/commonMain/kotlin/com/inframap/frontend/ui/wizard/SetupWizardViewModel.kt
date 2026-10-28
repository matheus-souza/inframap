package com.inframap.frontend.ui.wizard

import com.inframap.frontend.data.api.ApiResult
import com.inframap.frontend.data.dto.CollectorConfigDto
import com.inframap.frontend.data.dto.CreateDiscoverySourceRequest
import com.inframap.frontend.data.dto.CreateSubnetRequest
import com.inframap.frontend.data.dto.DiscoverySourceConfigDto
import com.inframap.frontend.data.dto.DiscoveryTargetRequestDto
import com.inframap.frontend.data.storage.LocalStorage
import com.inframap.frontend.domain.model.NetworkInterface
import com.inframap.frontend.domain.usecase.dashboard.GetStagingSummaryUseCase
import com.inframap.frontend.domain.usecase.discovery.CreateDiscoverySourceUseCase
import com.inframap.frontend.domain.usecase.discovery.GetDiscoverySourcesUseCase
import com.inframap.frontend.domain.usecase.discovery.TriggerDiscoveryRunUseCase
import com.inframap.frontend.domain.usecase.network.GetNetworkInterfacesUseCase
import com.inframap.frontend.domain.usecase.subnet.CreateSubnetUseCase
import com.inframap.frontend.ui.base.BaseViewModel
import com.inframap.frontend.ui.dashboard.AutoSetupCoordinator
import com.inframap.frontend.ui.util.UiText
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay

@Suppress("TooManyFunctions")
class SetupWizardViewModel(
    private val getNetworkInterfacesUseCase: GetNetworkInterfacesUseCase,
    private val createSubnetUseCase: CreateSubnetUseCase,
    private val createDiscoverySourceUseCase: CreateDiscoverySourceUseCase,
    private val triggerDiscoveryRunUseCase: TriggerDiscoveryRunUseCase,
    private val getDiscoverySourcesUseCase: GetDiscoverySourcesUseCase,
    private val getStagingSummaryUseCase: GetStagingSummaryUseCase,
    private val localStorage: LocalStorage,
    scope: CoroutineScope? = null,
) : BaseViewModel<SetupWizardUiState>(SetupWizardUiState(), scope) {
    private val createdCidrs = mutableSetOf<String>()

    /** Id of each Subnet the wizard created, keyed by CIDR, so its source target links to it. */
    private val createdSubnetIds = mutableMapOf<String, String>()

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

    fun startScan() {
        if (currentState.isLoading || currentState.scanStarted) return
        updateState { it.copy(isLoading = true, errorMessage = null, scanStarted = true) }

        launchJob("trigger_scans") {
            val sourceIds = currentState.createdSourceIds
            for (sourceId in sourceIds) {
                when (val result = triggerDiscoveryRunUseCase(sourceId)) {
                    is ApiResult.Success -> {}
                    else -> {
                        updateState {
                            it.copy(
                                isLoading = false,
                                scanStarted = false,
                                errorMessage = mapError(result),
                            )
                        }
                        return@launchJob
                    }
                }
            }
            pollScanStatus(sourceIds)
        }
    }

    @Suppress("ReturnCount")
    private suspend fun pollScanStatus(sourceIds: List<String>) {
        while (true) {
            delay(POLL_INTERVAL_MS)
            when (val result = getDiscoverySourcesUseCase()) {
                is ApiResult.Success -> {
                    val sources = result.data.items.filter { it.id in sourceIds }
                    val allDone = sources.none { it.lastStatus == "running" }
                    val anyError = sources.any { it.lastStatus == "error" }

                    if (anyError) {
                        updateState {
                            it.copy(
                                isLoading = false,
                                scanStarted = false,
                                errorMessage =
                                    UiText.DynamicString("Erro durante a varredura. Tente novamente."),
                            )
                        }
                        return
                    }
                    if (allDone) {
                        fetchStagingCount()
                        return
                    }
                }
                else -> {
                    updateState {
                        it.copy(
                            isLoading = false,
                            scanStarted = false,
                            errorMessage = mapError(result),
                        )
                    }
                    return
                }
            }
        }
    }

    private suspend fun fetchStagingCount() {
        when (val result = getStagingSummaryUseCase()) {
            is ApiResult.Success -> {
                updateState {
                    it.copy(
                        isLoading = false,
                        scanCompleted = true,
                        discoveredDeviceCount = result.data.items.size,
                    )
                }
            }
            else -> {
                updateState {
                    it.copy(
                        isLoading = false,
                        scanCompleted = true,
                        discoveredDeviceCount = 0,
                    )
                }
            }
        }
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
                    is ApiResult.Success -> {
                        createdCidrs.add(iface.cidr)
                        createdSubnetIds[iface.cidr] = result.data.id
                    }
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

    /**
     * Creates the single local network source (RFC-026 §8), with one target per network
     * selected and created in step 1, each linked to its Subnet. Once it exists, going back and
     * forward again does not create a second one.
     */
    @Suppress("ReturnCount")
    private fun createSourcesAndAdvance() {
        if (currentState.isLoading) return
        if (currentState.createdSourceIds.isNotEmpty()) {
            updateState { it.copy(currentStep = 3) }
            return
        }

        val interfaces =
            currentState.detectedInterfaces
                .filter { it.cidr in createdCidrs && it.cidr in currentState.selectedCidrs }
                .distinctBy { it.cidr }
        val targets = AutoSetupCoordinator.localNetworkTargets(interfaces, createdSubnetIds)
        if (targets.isEmpty()) {
            updateState {
                it.copy(errorMessage = UiText.DynamicString("Nenhuma rede configurada."))
            }
            return
        }

        val request = sourceRequest(targets, currentState.scanType, currentState.scheduleFrequency.cron)
        updateState { it.copy(isLoading = true, errorMessage = null) }

        launchJob("create_sources") {
            when (val result = createDiscoverySourceUseCase(request)) {
                is ApiResult.Success ->
                    updateState {
                        it.copy(
                            isLoading = false,
                            createdSourceIds = listOf(result.data.id),
                            currentStep = 3,
                        )
                    }
                else ->
                    updateState { it.copy(isLoading = false, errorMessage = mapError(result)) }
            }
        }
    }

    private fun sourceRequest(
        targets: List<DiscoveryTargetRequestDto>,
        scanType: ScanType,
        cron: String?,
    ) = CreateDiscoverySourceRequest(
        name = AutoSetupCoordinator.LOCAL_NETWORK_SOURCE_NAME,
        enabled = true,
        scheduleCron = cron,
        config = DiscoverySourceConfigDto(targets = targets),
        collectors = scanType.collectorTypes.map { CollectorConfigDto(type = it) },
    )

    companion object {
        const val KEY_WIZARD_DISMISSED = "inframap_wizard_dismissed"
        const val KEY_WIZARD_COMPLETED = "inframap_wizard_completed"
        const val TOTAL_STEPS = 3
        const val POLL_INTERVAL_MS = 3000L
    }
}
