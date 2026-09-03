package com.inframap.frontend.domain.model

data class TopologyGraph(
    val nodes: List<TopologyNode>,
    val edges: List<TopologyEdge>,
)

data class TopologyNode(
    val id: String,
    val label: String,
    val deviceType: String,
    /** What InfraMap observed about the device: active, offline, archived. */
    val status: String,
    /**
     * Runtime state a provider reports for a workload, when one owns it. Distinct from
     * [status]: a stopped container is still an actively discovered node.
     */
    val powerState: String? = null,
    /** The host that runs this workload, when it is contained by one. */
    val parentDeviceId: String? = null,
)

data class TopologyEdge(
    val id: String,
    val source: String,
    val target: String,
    val linkType: String,
)
