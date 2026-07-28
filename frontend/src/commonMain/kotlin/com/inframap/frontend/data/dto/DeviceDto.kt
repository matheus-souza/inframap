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
    val metadata: Map<String, String>? = null,
    @SerialName("created_at") val createdAt: String? = null,
    @SerialName("updated_at") val updatedAt: String? = null,
)

@Serializable
data class DeviceListResponse(
    val devices: List<DeviceDto>,
    val total: Long,
    val page: Int,
    @SerialName("per_page") val perPage: Int,
)

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
    @SerialName("device_type") val deviceType: String,
    @SerialName("source_type") val sourceType: String? = null,
    @SerialName("discovered_at") val discoveredAt: String? = null,
)
