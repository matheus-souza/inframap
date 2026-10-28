package com.inframap.frontend.data.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

@Serializable
data class CollectorDto(
    val id: String = "",
    @SerialName("collector_type") val collectorType: String = "",
    val enabled: Boolean = true,
    val config: Map<String, JsonElement>? = null,
    @SerialName("configured_secrets") val configuredSecrets: List<String>? = null,
)

@Serializable
data class CollectorRunDetailDto(
    @SerialName("collector_type") val collectorType: String = "",
    val status: String = "",
    @SerialName("devices_found") val devicesFound: Int = 0,
    @SerialName("duration_ms") val durationMs: Long = 0L,
    @SerialName("error_message") val errorMessage: String? = null,
    /** The target this row ran against; null for collectors that run once per source (RFC-026 §4). */
    @SerialName("target_cidr") val targetCidr: String? = null,
)

@Suppress("ConstructorParameterNaming")
@Serializable
data class CollectorRunSummaryDto(
    val status: String = "",
    @SerialName("collectors") private val _collectors: List<CollectorRunDetailDto>? = null,
) {
    val collectors: List<CollectorRunDetailDto> get() = _collectors ?: emptyList()
}

/**
 * One network target of a source (RFC-026 §7). `cidr` is the stored value or, for a target
 * linked to a Subnet, a display snapshot of it. Every field is optional so a partial or
 * older payload still decodes.
 */
@Serializable
data class DiscoveryTargetDto(
    val cidr: String? = null,
    @SerialName("subnet_id") val subnetId: String? = null,
    @SerialName("subnet_name") val subnetName: String? = null,
    @SerialName("subnet_deleted") val subnetDeleted: Boolean? = null,
)

@Suppress("ConstructorParameterNaming")
@Serializable
data class DiscoverySourceDto(
    val id: String = "",
    val name: String = "",
    val type: String = "",
    val enabled: Boolean = true,
    @SerialName("schedule_cron") val scheduleCron: String? = null,
    /** Deprecated by the backend in favour of [targets]; it mirrors the first target's CIDR. */
    @SerialName("config_cidr") val configCidr: String? = null,
    /** Absent from backends that predate RFC-026; the mapper then falls back to [configCidr]. */
    val targets: List<DiscoveryTargetDto>? = null,
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
    /**
     * Per-collector settings. Network sweeps take theirs from the plan-level config, while
     * provider collectors carry their own endpoint and credentials here.
     */
    val config: Map<String, String>? = null,
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

@Serializable
data class UpdateDiscoverySourceRequest(
    val name: String,
    val type: String = "",
    val enabled: Boolean = true,
    @SerialName("schedule_cron") val scheduleCron: String? = null,
    val config: Map<String, String>? = null,
    val collectors: List<CollectorConfigDto> = emptyList(),
)

@Serializable
data class DiscoverySourceHealthResponseDto(
    @SerialName("provider_id") val providerId: String = "",
    val status: String = "",
    val message: String? = null,
)

@Serializable
data class DiscoverySourceDeletionImpactDto(
    @SerialName("collectors_halted") val collectorsHalted: Int = 0,
    @SerialName("devices_unlinked") val devicesUnlinked: Int = 0,
)

@Serializable
data class DiscoverySourceDeletionImpactResponseDto(
    @SerialName("source_id") val sourceId: String = "",
    val impact: DiscoverySourceDeletionImpactDto = DiscoverySourceDeletionImpactDto(),
)

@Serializable
data class DeleteDiscoverySourceResponseDto(
    @SerialName("deleted_id") val deletedId: String = "",
    val impact: DiscoverySourceDeletionImpactDto = DiscoverySourceDeletionImpactDto(),
)
