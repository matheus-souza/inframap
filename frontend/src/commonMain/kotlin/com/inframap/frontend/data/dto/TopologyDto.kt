package com.inframap.frontend.data.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Suppress("ConstructorParameterNaming")
@Serializable
data class TopologyGraphDto(
    @SerialName("nodes") private val _nodes: List<TopologyNodeDto>? = null,
    @SerialName("edges") private val _edges: List<TopologyEdgeDto>? = null,
) {
    val nodes: List<TopologyNodeDto> get() = _nodes ?: emptyList()
    val edges: List<TopologyEdgeDto> get() = _edges ?: emptyList()
}

@Serializable
data class TopologyNodeDto(
    val id: String,
    @SerialName("hostname") val hostname: String? = null,
    @SerialName("label") val label: String? = null,
    @SerialName("ip_address") val ipAddress: String? = null,
    @SerialName("mac_address") val macAddress: String? = null,
    @SerialName("device_type") val deviceType: String = "unknown",
    val status: String = "active",
) {
    val displayLabel: String get() = hostname ?: label ?: id
}

@Serializable
data class TopologyEdgeDto(
    val id: String,
    @SerialName("source_device_id") val sourceDeviceId: String? = null,
    @SerialName("source") val source: String? = null,
    @SerialName("target_device_id") val targetDeviceId: String? = null,
    @SerialName("target") val target: String? = null,
    @SerialName("link_type") val linkType: String = "manual",
) {
    val sourceId: String get() = sourceDeviceId ?: source ?: ""
    val targetId: String get() = targetDeviceId ?: target ?: ""
}
