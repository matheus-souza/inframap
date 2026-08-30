package com.inframap.frontend.domain.model

data class DiscoverySource(
    val id: String = "",
    val name: String = "",
    val sourceType: String = "",
    val enabled: Boolean = true,
    val scheduleCron: String? = null,
    val configCidr: String? = null,
    val collectors: List<SourceCollector> = emptyList(),
    val lastRunAt: String? = null,
    val lastStatus: String = "idle",
    val lastRun: CollectorRunSummary? = null,
    val createdAt: String? = null,
    val updatedAt: String? = null,
)

data class SourceCollector(
    val id: String = "",
    val collectorType: String = "",
    val enabled: Boolean = true,
)

data class CollectorRunSummary(
    val status: String = "",
    val collectors: List<CollectorRunDetail> = emptyList(),
)

data class CollectorRunDetail(
    val collectorType: String = "",
    val status: String = "",
    val devicesFound: Int = 0,
    val durationMs: Long = 0L,
    val errorMessage: String? = null,
)
