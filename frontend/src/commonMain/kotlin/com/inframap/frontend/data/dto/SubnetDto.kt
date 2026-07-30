package com.inframap.frontend.data.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class SubnetDto(
    val id: String,
    val name: String,
    val cidr: String,
    @SerialName("vlan_id") val vlanId: Int? = null,
    @SerialName("gateway_ip") val gatewayIp: String? = null,
    val description: String? = null,
    @SerialName("discovery_enabled") val discoveryEnabled: Boolean = false,
    @SerialName("created_at") val createdAt: String? = null,
)

@Serializable
data class CreateSubnetRequest(
    val name: String,
    val cidr: String,
    @SerialName("vlan_id") val vlanId: Int? = null,
    @SerialName("gateway_ip") val gatewayIp: String? = null,
    val description: String? = null,
    @SerialName("discovery_enabled") val discoveryEnabled: Boolean = false,
)

@Serializable
data class SubnetListResponse(
    @SerialName("items") val items: List<SubnetDto> = emptyList(),
    val total: Long = 0,
) {
    val subnets: List<SubnetDto> get() = items
}
