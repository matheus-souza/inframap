package com.inframap.frontend.data.dto

import com.inframap.frontend.domain.model.IpAssignments
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Every host address of a subnet with what holds it (RFC-027 §2). */
@Serializable
data class SubnetAddressMapResponse(
    @SerialName("subnet_id") val subnetId: String,
    val cidr: String,
    val total: Int = 0,
    val free: Int = 0,
    @SerialName("in_use") val inUse: Int = 0,
    val reserved: Int = 0,
    val conflict: Int = 0,
    val addresses: List<SubnetAddressDto> = emptyList(),
)

@Serializable
data class SubnetAddressDto(
    val address: String,
    /** free, in_use, reserved or conflict. */
    val state: String,
    @SerialName("is_gateway") val isGateway: Boolean = false,
    val devices: List<SubnetAddressDeviceDto> = emptyList(),
)

@Serializable
data class SubnetAddressDeviceDto(
    val id: String,
    val hostname: String,
    @SerialName("device_type") val deviceType: String = "",
    @SerialName("ip_assignment") val ipAssignment: String = IpAssignments.UNKNOWN,
)
