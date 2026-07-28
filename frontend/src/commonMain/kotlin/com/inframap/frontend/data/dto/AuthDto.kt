package com.inframap.frontend.data.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class SetupStatusDto(
    @SerialName("onboarding_completed") val onboardingCompleted: Boolean,
    @SerialName("system_instance_id") val systemInstanceId: String = "",
)

@Serializable
data class OnboardRequest(
    @SerialName("admin_username") val adminUsername: String,
    @SerialName("admin_email") val adminEmail: String,
    @SerialName("admin_password") val adminPassword: String,
    @SerialName("admin_full_name") val adminFullName: String,
    @SerialName("telemetry_enabled") val telemetryEnabled: Boolean = false,
)

@Serializable
data class OnboardResponseDto(
    @SerialName("onboarding_completed") val onboardingCompleted: Boolean,
    @SerialName("system_instance_id") val systemInstanceId: String,
    @SerialName("admin_user_id") val adminUserId: String,
)

@Serializable
data class LoginRequest(
    val username: String,
    val password: String,
)

@Serializable
data class LoginResponseDto(
    val token: String,
    @SerialName("user_id") val userId: String,
    val username: String,
    val email: String = "",
    @SerialName("full_name") val fullName: String = "",
    val permissions: List<String> = emptyList(),
    @SerialName("expires_at") val expiresAt: String = "",
)

@Serializable
data class UserProfileDto(
    val id: String,
    val username: String,
    val email: String = "",
    @SerialName("full_name") val fullName: String = "",
    @SerialName("is_active") val isActive: Boolean = true,
    val permissions: List<String> = emptyList(),
)

@Serializable
data class HealthDto(
    val status: String,
    val version: String,
)
