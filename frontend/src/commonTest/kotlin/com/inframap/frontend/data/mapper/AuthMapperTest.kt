package com.inframap.frontend.data.mapper

import com.inframap.frontend.data.dto.LoginResponseDto
import com.inframap.frontend.data.dto.OnboardResponseDto
import com.inframap.frontend.data.dto.SetupStatusDto
import com.inframap.frontend.data.dto.UserProfileDto
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AuthMapperTest {
    @Test
    fun setupStatusToDomainMapsCorrectly() {
        val dto = SetupStatusDto(onboardingCompleted = true, systemInstanceId = "inst-1")
        val domain = AuthMapper.toDomain(dto)

        assertTrue(domain.onboardingCompleted)
        assertEquals("inst-1", domain.systemInstanceId)
    }

    @Test
    fun loginResponseToDomainMapsCorrectly() {
        val dto =
            LoginResponseDto(
                token = "jwt-token",
                userId = "usr-1",
                username = "admin",
                email = "admin@inframap.io",
                fullName = "Admin User",
                permissions = listOf("read", "write"),
                expiresAt = "2026-12-31T23:59:59Z",
            )

        val domain = AuthMapper.toDomain(dto)

        assertEquals("jwt-token", domain.token)
        assertEquals("usr-1", domain.userId)
        assertEquals("admin", domain.username)
        assertEquals("admin@inframap.io", domain.email)
        assertEquals("Admin User", domain.fullName)
        assertEquals(listOf("read", "write"), domain.permissions)
        assertEquals("2026-12-31T23:59:59Z", domain.expiresAt)
    }

    @Test
    fun onboardResponseToDomainMapsCorrectly() {
        val dto = OnboardResponseDto(onboardingCompleted = true, systemInstanceId = "sys-1", adminUserId = "usr-1")
        val domain = AuthMapper.toDomain(dto)

        assertTrue(domain.onboardingCompleted)
        assertEquals("sys-1", domain.systemInstanceId)
        assertEquals("usr-1", domain.adminUserId)
    }

    @Test
    fun userProfileToDomainMapsCorrectly() {
        val dto =
            UserProfileDto(
                id = "u1",
                username = "john",
                email = "john@inframap.io",
                fullName = "John Doe",
                isActive = true,
                permissions = listOf("admin"),
            )

        val domain = AuthMapper.toDomain(dto)

        assertEquals("u1", domain.id)
        assertEquals("john", domain.username)
        assertEquals("john@inframap.io", domain.email)
        assertEquals("John Doe", domain.fullName)
        assertTrue(domain.isActive)
        assertEquals(listOf("admin"), domain.permissions)
    }
}
