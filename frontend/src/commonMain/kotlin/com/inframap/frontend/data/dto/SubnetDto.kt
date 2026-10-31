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
    @SerialName("updated_at") val updatedAt: String? = null,
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
data class UpdateSubnetRequest(
    val name: String,
    val cidr: String,
    @SerialName("vlan_id") val vlanId: Int? = null,
    @SerialName("gateway_ip") val gatewayIp: String? = null,
    val description: String? = null,
    @SerialName("discovery_enabled") val discoveryEnabled: Boolean? = null,
)

@Serializable
data class SubnetListResponse(
    @SerialName("items") val items: List<SubnetDto>? = emptyList(),
    val total: Long = 0,
) {
    val subnets: List<SubnetDto> get() = items ?: emptyList()
}

@Serializable
data class SubnetCidrImpactRequest(
    @SerialName("new_cidr") val newCidr: String,
)

@Serializable
data class SubnetCidrImpactResponse(
    @SerialName("current_cidr") val currentCidr: String,
    @SerialName("new_cidr") val newCidr: String,
    @SerialName("affected_devices_count") val affectedDevicesCount: Int,
)

/** An active discovery source holding a target linked to the Subnet (RFC-026 §6). */
@Serializable
data class SubnetDiscoverySource(
    val id: String,
    val name: String,
)

@Serializable
data class SubnetDeletionImpact(
    @SerialName("affected_devices") val affectedDevices: Int,
    @SerialName("unlinked_topology_edges") val unlinkedTopologyEdges: Int,
    /**
     * Sources that scan the Subnet. Deleting it keeps them: the linked target stays in their
     * list and is skipped on runs. Older backends leave the key out.
     */
    @SerialName("discovery_sources") val discoverySources: List<SubnetDiscoverySource> = emptyList(),
)

@Serializable
data class SubnetDeletionImpactResponse(
    @SerialName("subnet_id") val subnetId: String,
    val impact: SubnetDeletionImpact,
)

@Serializable
data class DeleteSubnetResponse(
    @SerialName("deleted_id") val deletedId: String,
    val impact: SubnetDeletionImpact,
)

@Serializable
data class SubnetAddressDevice(
    val id: String,
    val hostname: String,
    @SerialName("ip_assignment") val ipAssignment: String,
)

@Serializable
data class SubnetAddressEntry(
    val address: String,
    val state: String,
    val devices: List<SubnetAddressDevice> = emptyList(),
    val dnsNames: List<String> = emptyList(),
)

@Serializable
data class SubnetAddressMapResponse(
    @SerialName("subnet_id") val subnetId: String,
    val cidr: String,
    val entries: List<SubnetAddressEntry> = emptyList(),
)
