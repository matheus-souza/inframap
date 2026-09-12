package com.inframap.frontend.ui.subnets

import com.inframap.frontend.data.api.ApiResult
import com.inframap.frontend.data.dto.CreateSubnetRequest
import com.inframap.frontend.data.storage.draft.DraftForm
import com.inframap.frontend.data.storage.draft.FormDraftStore
import com.inframap.frontend.domain.model.NetworkInterface
import com.inframap.frontend.domain.usecase.network.GetNetworkInterfacesUseCase
import com.inframap.frontend.domain.usecase.subnet.CreateSubnetUseCase
import com.inframap.frontend.generated.resources.Res
import com.inframap.frontend.generated.resources.subnets_error_create
import com.inframap.frontend.generated.resources.validation_cidr_invalid
import com.inframap.frontend.generated.resources.validation_cidr_required
import com.inframap.frontend.generated.resources.validation_gateway_invalid
import com.inframap.frontend.generated.resources.validation_subnet_name_required
import com.inframap.frontend.generated.resources.validation_vlan_invalid
import com.inframap.frontend.ui.base.BaseViewModel
import com.inframap.frontend.ui.util.UiText
import kotlinx.coroutines.CoroutineScope

@Suppress("TooManyFunctions")
class CreateSubnetViewModel(
    private val createSubnetUseCase: CreateSubnetUseCase,
    private val getNetworkInterfacesUseCase: GetNetworkInterfacesUseCase,
    private val formDrafts: FormDraftStore,
    private val prefilledCidr: String? = null,
    private val prefilledName: String? = null,
    scope: CoroutineScope? = null,
) : BaseViewModel<CreateSubnetUiState>(
        CreateSubnetUiState(
            cidr = prefilledCidr ?: "",
            name = prefilledName ?: "",
        ),
        scope,
    ) {
    private val pristineDraft =
        CreateSubnetDraft(
            name = prefilledName.orEmpty(),
            cidr = prefilledCidr.orEmpty(),
            prefilledCidr = prefilledCidr,
            prefilledName = prefilledName,
        )

    init {
        restoreDraft()
        loadNetworkInterfaces()
    }

    private fun restoreDraft() {
        val draft = formDrafts.load(DraftForm.CreateSubnet, CreateSubnetDraft.serializer()) ?: return
        if (draft.matchesOrigin(prefilledCidr, prefilledName)) {
            updateState { draft.applyTo(it) }
        }
    }

    private inline fun editForm(crossinline reducer: (CreateSubnetUiState) -> CreateSubnetUiState) {
        updateState { reducer(it) }
        val currentDraft = state.value.toDraft(prefilledCidr, prefilledName)
        if (currentDraft == pristineDraft) {
            formDrafts.discard(DraftForm.CreateSubnet)
        } else {
            formDrafts.save(DraftForm.CreateSubnet, currentDraft, CreateSubnetDraft.serializer())
        }
    }

    fun discardDraft() {
        formDrafts.discard(DraftForm.CreateSubnet)
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
        editForm { it.copy(name = name, validationErrors = it.validationErrors - "name").reconcileSelection() }
    }

    fun onCidrChanged(cidr: String) {
        editForm { it.copy(cidr = cidr, validationErrors = it.validationErrors - "cidr").reconcileSelection() }
    }

    fun onVlanIdChanged(vlanId: String) {
        editForm { it.copy(vlanId = vlanId, validationErrors = it.validationErrors - "vlan_id") }
    }

    fun onGatewayIpChanged(gatewayIp: String) {
        editForm {
            it.copy(gatewayIp = gatewayIp, validationErrors = it.validationErrors - "gateway_ip").reconcileSelection()
        }
    }

    fun onDescriptionChanged(description: String) {
        editForm { it.copy(description = description) }
    }

    fun onDiscoveryEnabledChanged(enabled: Boolean) {
        editForm { it.copy(discoveryEnabled = enabled) }
    }

    fun onInterfaceSelected(iface: NetworkInterface) {
        editForm {
            if (it.selectedInterface == iface) {
                // A second click only takes the highlight away. The values stay: the operator
                // may have meant "stop tracking this interface", not "undo what it filled in".
                it.copy(selectedInterface = null)
            } else {
                val clearedErrors = it.validationErrors - "cidr" - "name"
                it.copy(
                    cidr = iface.cidr,
                    name = iface.name,
                    gatewayIp = iface.gateway.ifEmpty { it.gatewayIp },
                    selectedInterface = iface,
                    validationErrors =
                        if (iface.gateway.isNotEmpty()) {
                            clearedErrors - "gateway_ip"
                        } else {
                            clearedErrors
                        },
                )
            }
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
                    formDrafts.discard(DraftForm.CreateSubnet)
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

/** Drops the highlight once the form no longer carries what the selected interface filled in. */
private fun CreateSubnetUiState.reconcileSelection(): CreateSubnetUiState {
    val selected = selectedInterface ?: return this
    return if (isFilledFrom(selected)) this else copy(selectedInterface = null)
}

/**
 * Whether the form still carries the values [iface] filled in via [CreateSubnetViewModel.onInterfaceSelected].
 * The gateway only counts when the interface reported one; values are compared trimmed,
 * the same way they are submitted.
 */
private fun CreateSubnetUiState.isFilledFrom(iface: NetworkInterface): Boolean =
    name.trim() == iface.name &&
        cidr.trim() == iface.cidr &&
        (iface.gateway.isEmpty() || gatewayIp.trim() == iface.gateway)
