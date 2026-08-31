package com.inframap.frontend.data.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class CollectorDto(
    val id: String = "",
    @SerialName("collector_type") val collectorType: String = "",
    val enabled: Boolean = true,
)

@Serializable
data class CollectorRunDetailDto(
    @SerialName("collector_type") val collectorType: String = "",
    val status: String = "",
    @SerialName("devices_found") val devicesFound: Int = 0,
    @SerialName("duration_ms") val durationMs: Long = 0L,
    @SerialName("error_message") val errorMessage: String? = null,
)

@Suppress("ConstructorParameterNaming")
@Serializable
data class CollectorRunSummaryDto(
    val status: String = "",
    @SerialName("collectors") private val _collectors: List<CollectorRunDetailDto>? = null,
) {
    val collectors: List<CollectorRunDetailDto> get() = _collectors ?: emptyList()
}

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
    @SerialName("last_run") val lastRun: CollectorRunSummaryDto? = null,
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
data class CollectorConfigDto(
    val type: String,
)

@Serializable
data class CreateDiscoverySourceRequest(
    val name: String,
    val type: String = "",
    val enabled: Boolean = true,
    @SerialName("schedule_cron") val scheduleCron: String? = null,
    val config: Map<String, String>? = null,
    val collectors: List<CollectorConfigDto> = emptyList(),
)
