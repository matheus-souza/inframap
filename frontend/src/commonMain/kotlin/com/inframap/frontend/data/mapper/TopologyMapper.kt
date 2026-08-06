package com.inframap.frontend.data.mapper

import com.inframap.frontend.data.dto.TopologyEdgeDto
import com.inframap.frontend.data.dto.TopologyGraphDto
import com.inframap.frontend.data.dto.TopologyNodeDto
import com.inframap.frontend.domain.model.TopologyEdge
import com.inframap.frontend.domain.model.TopologyGraph
import com.inframap.frontend.domain.model.TopologyNode

fun TopologyNodeDto.toDomain(): TopologyNode =
    TopologyNode(
        id = id,
        label = label,
        deviceType = deviceType,
        status = status,
    )

fun TopologyEdgeDto.toDomain(): TopologyEdge =
    TopologyEdge(
        id = id,
        source = source,
        target = target,
        linkType = linkType,
    )

fun TopologyGraphDto.toDomain(): TopologyGraph =
    TopologyGraph(
        nodes = nodes.map { it.toDomain() },
        edges = edges.map { it.toDomain() },
    )
