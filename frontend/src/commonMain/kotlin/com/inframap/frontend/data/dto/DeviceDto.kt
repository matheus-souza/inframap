package com.inframap.frontend.data.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class DeviceDto(
    val id: String,
    val hostname: String,
    @SerialName("ip_address") val ipAddress: String? = null,
    @SerialName("mac_address") val macAddress: String? = null,
    val manufacturer: String? = null,
    val model: String? = null,
    @SerialName("serial_number") val serialNumber: String? = null,
    @SerialName("device_type") val deviceType: String,
    val status: String,
    @SerialName("power_state") val powerState: String? = null,
    @SerialName("parent_device_id") val parentDeviceId: String? = null,
    val metadata: Map<String, String>? = null,
    @SerialName("created_at") val createdAt: String? = null,
    @SerialName("updated_at") val updatedAt: String? = null,
)

@Serializable
data class DeviceListResponse(
    @SerialName("items") val items: List<DeviceDto>? = emptyList(),
    val total: Long = 0,
    val page: Int = 1,
    @SerialName("per_page") val perPage: Int = 50,
) {
    val devices: List<DeviceDto> get() = items ?: emptyList()
}

@Serializable
data class CreateDeviceRequest(
    val hostname: String,
    @SerialName("ip_address") val ipAddress: String? = null,
    @SerialName("mac_address") val macAddress: String? = null,
    @SerialName("device_type") val deviceType: String,
)

@Serializable
data class UpdateDeviceRequest(
    val hostname: String? = null,
    @SerialName("ip_address") val ipAddress: String? = null,
    @SerialName("mac_address") val macAddress: String? = null,
    @SerialName("device_type") val deviceType: String? = null,
    val status: String? = null,
)

@Serializable
data class StagingDeviceDto(
    val id: String,
    val hostname: String,
    @SerialName("ip_address") val ipAddress: String? = null,
    @SerialName("mac_address") val macAddress: String? = null,
    val manufacturer: String? = null,
    val model: String? = null,
    @SerialName("device_type") val deviceType: String,
    @SerialName("discovery_source_id") val discoverySourceId: String? = null,
    val status: String = "pending",
    @SerialName("created_at") val createdAt: String? = null,
)

@Serializable
data class StagingListResponse(
    @SerialName("items") val items: List<StagingDeviceDto>? = emptyList(),
    val total: Long = 0,
    val page: Int = 1,
    @SerialName("per_page") val perPage: Int = 50,
) {
    val devices: List<StagingDeviceDto> get() = items ?: emptyList()
}
