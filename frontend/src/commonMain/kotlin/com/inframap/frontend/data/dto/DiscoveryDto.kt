package com.inframap.frontend.data.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class CollectorDto(
    val id: String = "",
    @SerialName("collector_type") val collectorType: String = "",
    val enabled: Boolean = true,
)

@Suppress("ConstructorParameterNaming")
@Serializable
data class DiscoverySourceDto(
    val id: String = "",
    val name: String = "",
    val type: String = "",
    val enabled: Boolean = true,
    @SerialName("schedule_cron") val scheduleCron: String? = null,
    @SerialName("config_cidr") val configCidr: String? = null,
    @SerialName("collectors") private val _collectors: List<CollectorDto>? = null,
    @SerialName("last_run_at") val lastRunAt: String? = null,
    @SerialName("last_status") val lastStatus: String = "idle",
    @SerialName("created_at") val createdAt: String? = null,
    @SerialName("updated_at") val updatedAt: String? = null,
) {
    val collectors: List<CollectorDto> get() = _collectors ?: emptyList()
}

@Serializable
data class DiscoveryListResponse(
    @SerialName("items") val items: List<DiscoverySourceDto>? = emptyList(),
    val total: Long = 0,
) {
    val sources: List<DiscoverySourceDto> get() = items ?: emptyList()
}

@Serializable
data class CreateDiscoverySourceRequest(
    val name: String,
    val type: String,
    val enabled: Boolean = true,
    @SerialName("schedule_cron") val scheduleCron: String? = null,
    val config: Map<String, String>? = null,
)
