package com.inframap.frontend.data.mapper

import com.inframap.frontend.data.dto.TopologyEdgeDto
import com.inframap.frontend.data.dto.TopologyGraphDto
import com.inframap.frontend.data.dto.TopologyNodeDto
import kotlin.test.Test
import kotlin.test.assertEquals

class TopologyMapperTest {
    @Test
    fun nodeDtoToDomainMapsCorrectly() {
        val dto =
            TopologyNodeDto(
                id = "n1",
                label = "router-core",
                deviceType = "router",
                status = "active",
            )
        val domain = dto.toDomain()

        assertEquals("n1", domain.id)
        assertEquals("router-core", domain.label)
        assertEquals("router", domain.deviceType)
        assertEquals("active", domain.status)
    }

    @Test
    fun edgeDtoToDomainMapsCorrectly() {
        val dto =
            TopologyEdgeDto(
                id = "e1",
                source = "n1",
                target = "n2",
                linkType = "physical",
            )
        val domain = dto.toDomain()

        assertEquals("e1", domain.id)
        assertEquals("n1", domain.source)
        assertEquals("n2", domain.target)
        assertEquals("physical", domain.linkType)
    }

    @Test
    fun graphDtoToDomainMapsCorrectly() {
        val dto =
            TopologyGraphDto(
                _nodes =
                    listOf(
                        TopologyNodeDto(id = "n1", hostname = "r1", deviceType = "router", status = "active"),
                        TopologyNodeDto(id = "n2", label = "s1", deviceType = "switch", status = "active"),
                    ),
                _edges =
                    listOf(
                        TopologyEdgeDto(id = "e1", sourceDeviceId = "n1", targetDeviceId = "n2", linkType = "physical"),
                    ),
            )
        val domain = dto.toDomain()

        assertEquals(2, domain.nodes.size)
        assertEquals(1, domain.edges.size)
        assertEquals("r1", domain.nodes.first().label)
        assertEquals("s1", domain.nodes[1].label)
        assertEquals("n1", domain.edges.first().source)
        assertEquals("n2", domain.edges.first().target)
        assertEquals("physical", domain.edges.first().linkType)
    }

    @Test
    fun graphDtoWithNullSlicesMapsToEmptyDomainGraph() {
        val dto = TopologyGraphDto(_nodes = null, _edges = null)
        val domain = dto.toDomain()

        assertEquals(0, domain.nodes.size)
        assertEquals(0, domain.edges.size)
    }
}
