package com.inframap.frontend.ui.subnets

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class CreateSubnetDraft(
    val name: String = "",
    val cidr: String = "",
    @SerialName("vlan_id") val vlanId: String = "",
    @SerialName("gateway_ip") val gatewayIp: String = "",
    val description: String = "",
    @SerialName("discovery_enabled") val discoveryEnabled: Boolean = true,
    @SerialName("prefilled_cidr") val prefilledCidr: String? = null,
    @SerialName("prefilled_name") val prefilledName: String? = null,
)

internal fun CreateSubnetUiState.toDraft(
    prefilledCidr: String?,
    prefilledName: String?,
): CreateSubnetDraft =
    CreateSubnetDraft(
        name = name,
        cidr = cidr,
        vlanId = vlanId,
        gatewayIp = gatewayIp,
        description = description,
        discoveryEnabled = discoveryEnabled,
        prefilledCidr = prefilledCidr,
        prefilledName = prefilledName,
    )

internal fun CreateSubnetDraft.applyTo(state: CreateSubnetUiState): CreateSubnetUiState =
    state.copy(
        name = name,
        cidr = cidr,
        vlanId = vlanId,
        gatewayIp = gatewayIp,
        description = description,
        discoveryEnabled = discoveryEnabled,
        restoredFromDraft = true,
    )

internal fun CreateSubnetDraft.matchesOrigin(
    prefilledCidr: String?,
    prefilledName: String?,
): Boolean = this.prefilledCidr == prefilledCidr && this.prefilledName == prefilledName
