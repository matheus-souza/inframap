package com.inframap.frontend.data.mapper

import com.inframap.frontend.data.dto.NetworkInterfaceDto
import kotlin.test.Test
import kotlin.test.assertEquals

class NetworkInterfaceMapperTest {
    @Test
    fun toDomainMapsAllFieldsCorrectly() {
        val dto =
            NetworkInterfaceDto(
                name = "eth0",
                ip = "192.168.18.5",
                cidr = "192.168.18.0/24",
                mac = "aa:bb:cc:dd:ee:ff",
                gateway = "192.168.18.1",
            )

        val domain = NetworkInterfaceMapper.toDomain(dto)

        assertEquals("eth0", domain.name)
        assertEquals("192.168.18.5", domain.ip)
        assertEquals("192.168.18.0/24", domain.cidr)
        assertEquals("aa:bb:cc:dd:ee:ff", domain.mac)
        assertEquals("192.168.18.1", domain.gateway)
    }

    @Test
    fun toDomainDefaultsGatewayToEmpty() {
        val dto =
            NetworkInterfaceDto(
                name = "lo",
                ip = "127.0.0.1",
                cidr = "127.0.0.0/8",
                mac = "00:00:00:00:00:00",
            )

        val domain = NetworkInterfaceMapper.toDomain(dto)

        assertEquals("", domain.gateway)
    }
}
