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
    val username: String,
    val password: String,
)

@Serializable
data class LoginRequest(
    val username: String,
    val password: String,
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
