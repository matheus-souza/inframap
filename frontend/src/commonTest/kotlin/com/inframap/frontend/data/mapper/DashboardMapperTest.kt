package com.inframap.frontend.data.mapper

import com.inframap.frontend.data.dto.DiscoverySourceDto
import com.inframap.frontend.data.dto.HealthDto
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DashboardMapperTest {
    @Test
    fun healthToDomainMapsCorrectly() {
        val dto = HealthDto(status = "healthy", version = "1.0.0")
        val domain = DashboardMapper.toDomain(dto)

        assertEquals("healthy", domain.status)
        assertEquals("1.0.0", domain.version)
    }

    @Test
    fun discoverySourceToDomainMapsCorrectly() {
        val dto = DiscoverySourceDto(id = "src-1", name = "Docker Discovery", type = "docker", enabled = true)
        val domain = DashboardMapper.toDomain(dto)

        assertEquals("src-1", domain.id)
        assertEquals("Docker Discovery", domain.name)
        assertEquals("docker", domain.sourceType)
        assertTrue(domain.enabled)
    }
}
