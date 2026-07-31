package com.inframap.frontend.domain.model

data class Device(
    val id: String,
    val hostname: String,
    val ipAddress: String? = null,
    val macAddress: String? = null,
    val manufacturer: String? = null,
    val model: String? = null,
    val serialNumber: String? = null,
    val deviceType: String,
    val status: String,
    val metadata: Map<String, String>? = null,
    val createdAt: String? = null,
    val updatedAt: String? = null,
)

data class StagingDevice(
    val id: String,
    val hostname: String,
    val ipAddress: String? = null,
    val macAddress: String? = null,
    val manufacturer: String? = null,
    val model: String? = null,
    val deviceType: String,
    val discoverySourceId: String? = null,
    val status: String = "pending",
    val createdAt: String? = null,
)
