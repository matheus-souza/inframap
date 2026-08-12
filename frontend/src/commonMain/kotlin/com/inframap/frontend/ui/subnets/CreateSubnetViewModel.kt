package com.inframap.frontend.ui.subnets

import com.inframap.frontend.data.api.ApiResult
import com.inframap.frontend.data.dto.CreateSubnetRequest
import com.inframap.frontend.designsystem.resources.Res
import com.inframap.frontend.domain.model.NetworkInterface
import com.inframap.frontend.domain.usecase.network.GetNetworkInterfacesUseCase
import com.inframap.frontend.domain.usecase.subnet.CreateSubnetUseCase
import com.inframap.frontend.ui.base.BaseViewModel
import com.inframap.frontend.ui.util.UiText
import kotlinx.coroutines.CoroutineScope

class CreateSubnetViewModel(
    private val createSubnetUseCase: CreateSubnetUseCase,
    private val getNetworkInterfacesUseCase: GetNetworkInterfacesUseCase,
    prefilledCidr: String? = null,
    prefilledName: String? = null,
    scope: CoroutineScope? = null,
) : BaseViewModel<CreateSubnetUiState>(
        CreateSubnetUiState(
            cidr = prefilledCidr ?: "",
            name = prefilledName ?: "",
        ),
        scope,
    ) {
    init {
        loadNetworkInterfaces()
    }

    private fun loadNetworkInterfaces() {
        launchJob("fetch_interfaces") {
            when (val result = getNetworkInterfacesUseCase()) {
                is ApiResult.Success -> {
                    updateState { it.copy(detectedInterfaces = result.data) }
                }
                is ApiResult.Error,
                is ApiResult.NetworkError,
                -> {
                    // Silently ignore — interfaces are a best-effort suggestion
                }
            }
        }
    }

    fun onNameChanged(name: String) {
        updateState { it.copy(name = name, validationErrors = it.validationErrors - "name") }
    }

    fun onCidrChanged(cidr: String) {
        updateState { it.copy(cidr = cidr, validationErrors = it.validationErrors - "cidr") }
    }

    fun onVlanIdChanged(vlanId: String) {
        updateState { it.copy(vlanId = vlanId, validationErrors = it.validationErrors - "vlan_id") }
    }

    fun onGatewayIpChanged(gatewayIp: String) {
        updateState { it.copy(gatewayIp = gatewayIp, validationErrors = it.validationErrors - "gateway_ip") }
    }

    fun onDescriptionChanged(description: String) {
        updateState { it.copy(description = description) }
    }

    fun onDiscoveryEnabledChanged(enabled: Boolean) {
        updateState { it.copy(discoveryEnabled = enabled) }
    }

    fun onInterfaceSelected(iface: NetworkInterface) {
        updateState {
            it.copy(
                cidr = iface.cidr,
                name = iface.name,
                gatewayIp = iface.gateway.ifEmpty { it.gatewayIp },
                showInterfaceSuggestions = false,
                validationErrors = it.validationErrors - "cidr" - "name",
            )
        }
    }

    fun toggleSuggestions() {
        updateState { it.copy(showInterfaceSuggestions = !it.showInterfaceSuggestions) }
    }

    fun validate(): Boolean {
        val errors = mutableMapOf<String, UiText>()
        val name = state.value.name.trim()
        val cidr = state.value.cidr.trim()
        val vlanIdStr = state.value.vlanId.trim()
        val gatewayIp = state.value.gatewayIp.trim()

        if (name.isEmpty()) {
            errors["name"] = UiText.Resource(Res.string.validation_subnet_name_required)
        }

        if (cidr.isEmpty()) {
            errors["cidr"] = UiText.Resource(Res.string.validation_cidr_required)
        } else if (!isValidCidr(cidr)) {
            errors["cidr"] = UiText.Resource(Res.string.validation_cidr_invalid)
        }

        if (vlanIdStr.isNotEmpty()) {
            val vlan = vlanIdStr.toIntOrNull()
            if (vlan == null || vlan < 1 || vlan > 4094) {
                errors["vlan_id"] = UiText.Resource(Res.string.validation_vlan_invalid)
            }
        }

        if (gatewayIp.isNotEmpty() && !isValidIp(gatewayIp)) {
            errors["gateway_ip"] = UiText.Resource(Res.string.validation_gateway_invalid)
        }

        updateState { it.copy(validationErrors = errors) }
        return errors.isEmpty()
    }

    fun createSubnet(onSuccess: (() -> Unit)? = null) {
        if (state.value.isSubmitting) return
        if (!validate()) return

        val stateVal = state.value

        updateState { it.copy(isSubmitting = true, errorMessage = null, isSuccess = false) }

        launchJob("submit") {
            when (
                val result =
                    createSubnetUseCase(
                        CreateSubnetRequest(
                            name = stateVal.name.trim(),
                            cidr = stateVal.cidr.trim(),
                            vlanId = stateVal.vlanId.trim().toIntOrNull(),
                            gatewayIp = stateVal.gatewayIp.trim().ifEmpty { null },
                            description = stateVal.description.trim().ifEmpty { null },
                            discoveryEnabled = stateVal.discoveryEnabled,
                        ),
                    )
            ) {
                is ApiResult.Success -> {
                    updateState {
                        it.copy(
                            isSubmitting = false,
                            isSuccess = true,
                            errorMessage = null,
                        )
                    }
                    onSuccess?.invoke()
                }
                is ApiResult.Error -> {
                    updateState {
                        it.copy(
                            isSubmitting = false,
                            errorMessage = mapError(result, UiText.Resource(Res.string.subnets_error_create)),
                        )
                    }
                }
                is ApiResult.NetworkError -> {
                    updateState {
                        it.copy(
                            isSubmitting = false,
                            errorMessage = mapError(result, UiText.Resource(Res.string.subnets_error_create)),
                        )
                    }
                }
            }
        }
    }

    private fun isValidCidr(cidr: String): Boolean {
        val regex = Regex("""^([0-9]{1,3}\.){3}[0-9]{1,3}\/([0-9]|[12][0-9]|3[0-2])$""")
        if (!regex.matches(cidr)) return false
        val ipPart = cidr.substringBefore("/")
        return ipPart.split(".").all { it.toIntOrNull() in 0..255 }
    }

    private fun isValidIp(ip: String): Boolean {
        val parts = ip.split(".")
        if (parts.size != 4) return false
        return parts.all { it.toIntOrNull() in 0..255 }
    }
}
