package com.inframap.frontend.data.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class TopologyGraphDto(
    val nodes: List<TopologyNodeDto>,
    val edges: List<TopologyEdgeDto>,
)

@Serializable
data class TopologyNodeDto(
    val id: String,
    val label: String,
    @SerialName("device_type") val deviceType: String,
    val status: String,
)

@Serializable
data class TopologyEdgeDto(
    val id: String,
    val source: String,
    val target: String,
    @SerialName("link_type") val linkType: String,
)
