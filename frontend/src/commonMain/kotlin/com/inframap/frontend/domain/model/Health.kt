package com.inframap.frontend.domain.model

data class Health(
    val status: String,
    val version: String,
)

data class DiscoverySource(
    val id: String = "",
    val name: String = "",
    val sourceType: String = "",
    val enabled: Boolean = true,
)
