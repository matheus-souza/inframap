package com.inframap.frontend.ui.subnets

import com.inframap.frontend.data.api.ApiClient
import com.inframap.frontend.data.api.ApiResult
import com.inframap.frontend.data.dto.CreateSubnetRequest
import com.inframap.frontend.data.dto.SubnetDto
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class CreateSubnetViewModel(
    private val apiClient: ApiClient,
    private val scope: CoroutineScope,
) {
    private val _state = MutableStateFlow(CreateSubnetUiState())
    val state: StateFlow<CreateSubnetUiState> = _state.asStateFlow()

    private var submitJob: Job? = null

    fun onNameChanged(name: String) {
        _state.update { it.copy(name = name, validationErrors = it.validationErrors - "name") }
    }

    fun onCidrChanged(cidr: String) {
        _state.update { it.copy(cidr = cidr, validationErrors = it.validationErrors - "cidr") }
    }

    fun onVlanIdChanged(vlanId: String) {
        _state.update { it.copy(vlanId = vlanId, validationErrors = it.validationErrors - "vlan_id") }
    }

    fun onGatewayIpChanged(gatewayIp: String) {
        _state.update { it.copy(gatewayIp = gatewayIp, validationErrors = it.validationErrors - "gateway_ip") }
    }

    fun onDescriptionChanged(description: String) {
        _state.update { it.copy(description = description) }
    }

    fun onDiscoveryEnabledChanged(enabled: Boolean) {
        _state.update { it.copy(discoveryEnabled = enabled) }
    }

    fun validate(): Boolean {
        val errors = mutableMapOf<String, String>()
        val name = _state.value.name.trim()
        val cidr = _state.value.cidr.trim()
        val vlanIdStr = _state.value.vlanId.trim()
        val gatewayIp = _state.value.gatewayIp.trim()

        if (name.isEmpty()) {
            errors["name"] = "Nome da subrede é obrigatório"
        }

        if (cidr.isEmpty()) {
            errors["cidr"] = "CIDR é obrigatório"
        } else if (!isValidCidr(cidr)) {
            errors["cidr"] = "Formato de CIDR inválido (ex: 192.168.1.0/24)"
        }

        if (vlanIdStr.isNotEmpty()) {
            val vlan = vlanIdStr.toIntOrNull()
            if (vlan == null || vlan < 1 || vlan > 4094) {
                errors["vlan_id"] = "VLAN ID deve ser um número entre 1 e 4094"
            }
        }

        if (gatewayIp.isNotEmpty() && !isValidIp(gatewayIp)) {
            errors["gateway_ip"] = "Endereço IP de Gateway inválido"
        }

        _state.update { it.copy(validationErrors = errors) }
        return errors.isEmpty()
    }

    fun createSubnet(onSuccess: (() -> Unit)? = null) {
        if (_state.value.isSubmitting) return
        if (!validate()) return

        val stateVal = _state.value
        val request =
            CreateSubnetRequest(
                name = stateVal.name.trim(),
                cidr = stateVal.cidr.trim(),
                vlanId = stateVal.vlanId.trim().toIntOrNull(),
                gatewayIp = stateVal.gatewayIp.trim().ifEmpty { null },
                description = stateVal.description.trim().ifEmpty { null },
                discoveryEnabled = stateVal.discoveryEnabled,
            )

        _state.update { it.copy(isSubmitting = true, errorMessage = null, isSuccess = false) }

        submitJob?.cancel()
        submitJob =
            scope.launch {
                when (val result = apiClient.post<SubnetDto, CreateSubnetRequest>("/api/v1/subnets", request)) {
                    is ApiResult.Success -> {
                        _state.update {
                            it.copy(
                                isSubmitting = false,
                                isSuccess = true,
                                errorMessage = null,
                            )
                        }
                        onSuccess?.invoke()
                    }
                    is ApiResult.Error -> {
                        _state.update {
                            it.copy(
                                isSubmitting = false,
                                errorMessage = result.message.ifEmpty { "Falha ao criar subrede" },
                            )
                        }
                    }
                    is ApiResult.NetworkError -> {
                        _state.update {
                            it.copy(
                                isSubmitting = false,
                                errorMessage = "Erro de rede. Não foi possível conectar ao servidor.",
                            )
                        }
                    }
                }
            }
    }

    fun clear() {
        submitJob?.cancel()
        submitJob = null
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
