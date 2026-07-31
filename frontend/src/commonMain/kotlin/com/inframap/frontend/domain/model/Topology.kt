package com.inframap.frontend.domain.model

data class TopologyGraph(
    val nodes: List<TopologyNode>,
    val edges: List<TopologyEdge>,
)

data class TopologyNode(
    val id: String,
    val label: String,
    val deviceType: String,
    val status: String,
)

data class TopologyEdge(
    val id: String,
    val source: String,
    val target: String,
    val linkType: String,
)
