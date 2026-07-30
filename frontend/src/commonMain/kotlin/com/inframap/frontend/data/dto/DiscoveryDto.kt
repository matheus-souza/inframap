package com.inframap.frontend.data.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class DiscoverySourceDto(
    val id: String = "",
    val name: String = "",
    @SerialName("source_type") val sourceType: String = "",
    val enabled: Boolean = true,
)

@Serializable
data class DiscoveryListResponse(
    @SerialName("items") val items: List<DiscoverySourceDto> = emptyList(),
    val total: Long = 0,
)
