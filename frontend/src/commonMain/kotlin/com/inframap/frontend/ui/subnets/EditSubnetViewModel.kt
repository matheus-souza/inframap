package com.inframap.frontend.ui.subnets

import com.inframap.frontend.data.api.ApiResult
import com.inframap.frontend.domain.usecase.subnet.DeleteSubnetUseCase
import com.inframap.frontend.domain.usecase.subnet.GetSubnetByIdUseCase
import com.inframap.frontend.domain.usecase.subnet.GetSubnetCidrImpactUseCase
import com.inframap.frontend.domain.usecase.subnet.GetSubnetDeletionImpactUseCase
import com.inframap.frontend.domain.usecase.subnet.UpdateSubnetUseCase
import com.inframap.frontend.generated.resources.Res
import com.inframap.frontend.generated.resources.delete_subnet_error
import com.inframap.frontend.generated.resources.edit_subnet_error_load
import com.inframap.frontend.generated.resources.edit_subnet_error_update
import com.inframap.frontend.generated.resources.error_gateway_not_contained
import com.inframap.frontend.generated.resources.error_subnet_conflict
import com.inframap.frontend.generated.resources.validation_cidr_invalid
import com.inframap.frontend.generated.resources.validation_cidr_required
import com.inframap.frontend.generated.resources.validation_gateway_invalid
import com.inframap.frontend.generated.resources.validation_subnet_name_required
import com.inframap.frontend.generated.resources.validation_vlan_invalid
import com.inframap.frontend.ui.base.BaseViewModel
import com.inframap.frontend.ui.util.UiText
import kotlinx.coroutines.CoroutineScope

@Suppress("TooManyFunctions")
class EditSubnetViewModel(
    private val subnetId: String,
    private val getSubnetByIdUseCase: GetSubnetByIdUseCase,
    private val updateSubnetUseCase: UpdateSubnetUseCase,
    private val getSubnetCidrImpactUseCase: GetSubnetCidrImpactUseCase,
    private val deleteSubnetUseCase: DeleteSubnetUseCase,
    private val getSubnetDeletionImpactUseCase: GetSubnetDeletionImpactUseCase,
    scope: CoroutineScope? = null,
) : BaseViewModel<EditSubnetUiState>(EditSubnetUiState(subnetId = subnetId), scope) {
    init {
        loadSubnet()
    }

    fun loadSubnet() {
        updateState { it.copy(isLoading = true, errorMessage = null) }
        launchJob("fetch_subnet") {
            when (val result = getSubnetByIdUseCase(subnetId)) {
                is ApiResult.Success -> {
                    val subnet = result.data
                    updateState {
                        it.copy(
                            name = subnet.name,
                            cidr = subnet.cidr,
                            initialCidr = subnet.cidr,
                            vlanId = subnet.vlanId?.toString() ?: "",
                            gatewayIp = subnet.gatewayIp ?: "",
                            description = subnet.description ?: "",
                            discoveryEnabled = subnet.discoveryEnabled,
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
                            errorMessage = mapError(result, UiText.Resource(Res.string.edit_subnet_error_load)),
                        )
                    }
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

    fun updateSubnet(onSuccess: (() -> Unit)? = null) {
        if (state.value.isSubmitting) return
        if (!validate()) return

        val stateVal = state.value
        val cidrChanged = stateVal.cidr.trim() != stateVal.initialCidr.trim()

        if (cidrChanged) {
            updateState { it.copy(isSubmitting = true, errorMessage = null) }
            launchJob("check_cidr_impact") {
                when (val result = getSubnetCidrImpactUseCase(subnetId, stateVal.cidr.trim())) {
                    is ApiResult.Success -> {
                        val count = result.data.affectedDevicesCount
                        if (count > 0) {
                            updateState {
                                it.copy(
                                    isSubmitting = false,
                                    showImpactDialog = true,
                                    impactAffectedCount = count,
                                )
                            }
                        } else {
                            performUpdate(onSuccess)
                        }
                    }
                    is ApiResult.Error,
                    is ApiResult.NetworkError,
                    -> {
                        updateState {
                            it.copy(
                                isSubmitting = false,
                                errorMessage = mapError(result, UiText.Resource(Res.string.edit_subnet_error_update)),
                            )
                        }
                    }
                }
            }
        } else {
            performUpdate(onSuccess)
        }
    }

    fun confirmImpact(onSuccess: (() -> Unit)? = null) {
        updateState { it.copy(showImpactDialog = false) }
        performUpdate(onSuccess)
    }

    fun dismissImpact() {
        updateState { it.copy(showImpactDialog = false, isSubmitting = false) }
    }

    private fun performUpdate(onSuccess: (() -> Unit)? = null) {
        val stateVal = state.value
        updateState { it.copy(isSubmitting = true, errorMessage = null, isSuccess = false) }

        launchJob("update_subnet") {
            when (
                val result =
                    updateSubnetUseCase(
                        id = subnetId,
                        name = stateVal.name.trim(),
                        cidr = stateVal.cidr.trim(),
                        vlanId = stateVal.vlanId.trim().toIntOrNull(),
                        gatewayIp = stateVal.gatewayIp.trim().ifEmpty { null },
                        description = stateVal.description.trim().ifEmpty { null },
                        discoveryEnabled = stateVal.discoveryEnabled,
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
                    val err =
                        when {
                            result.code == "error_subnet_conflict" || result.httpStatus == 409 ->
                                UiText.Resource(Res.string.error_subnet_conflict)
                            result.code == "error_gateway_not_contained" || result.httpStatus == 422 ->
                                UiText.Resource(Res.string.error_gateway_not_contained)
                            else -> mapError(result, UiText.Resource(Res.string.edit_subnet_error_update))
                        }
                    updateState { it.copy(isSubmitting = false, errorMessage = err) }
                }
                is ApiResult.NetworkError -> {
                    updateState {
                        it.copy(
                            isSubmitting = false,
                            errorMessage = mapError(result, UiText.Resource(Res.string.edit_subnet_error_update)),
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

    fun requestDelete() {
        updateState {
            it.copy(
                showDeleteDialog = true,
                isLoadingDeleteImpact = true,
                deleteImpact = null,
            )
        }
        launchJob("fetch_deletion_impact") {
            when (val result = getSubnetDeletionImpactUseCase(subnetId)) {
                is ApiResult.Success -> {
                    updateState {
                        it.copy(
                            isLoadingDeleteImpact = false,
                            deleteImpact = result.data.impact,
                        )
                    }
                }
                is ApiResult.Error,
                is ApiResult.NetworkError,
                -> {
                    updateState {
                        it.copy(
                            isLoadingDeleteImpact = false,
                            errorMessage = mapError(result, UiText.Resource(Res.string.delete_subnet_error)),
                        )
                    }
                }
            }
        }
    }

    fun confirmDelete(onSuccess: (() -> Unit)? = null) {
        if (state.value.isDeleting) return
        updateState { it.copy(isDeleting = true, errorMessage = null) }

        launchJob("delete_subnet") {
            when (val result = deleteSubnetUseCase(subnetId)) {
                is ApiResult.Success -> {
                    updateState {
                        it.copy(
                            showDeleteDialog = false,
                            isDeleting = false,
                            isSuccess = true,
                        )
                    }
                    onSuccess?.invoke()
                }
                is ApiResult.Error,
                is ApiResult.NetworkError,
                -> {
                    updateState {
                        it.copy(
                            isDeleting = false,
                            errorMessage = mapError(result, UiText.Resource(Res.string.delete_subnet_error)),
                        )
                    }
                }
            }
        }
    }

    fun dismissDelete() {
        updateState {
            it.copy(
                showDeleteDialog = false,
                isDeleting = false,
                deleteImpact = null,
                isLoadingDeleteImpact = false,
            )
        }
    }
}
