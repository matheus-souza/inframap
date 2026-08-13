package com.inframap.frontend.domain.model

data class DiscoverySource(
    val id: String = "",
    val name: String = "",
    val sourceType: String = "",
    val enabled: Boolean = true,
    val scheduleCron: String? = null,
    val configCidr: String? = null,
    val lastRunAt: String? = null,
    val lastStatus: String = "idle",
    val createdAt: String? = null,
    val updatedAt: String? = null,
)
