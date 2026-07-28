package com.inframap.frontend.data.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class SetupStatusDto(
    @SerialName("is_onboarded") val isOnboarded: Boolean,
    val version: String? = null,
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
    val roles: List<String> = emptyList(),
)

@Serializable
data class HealthDto(
    val status: String,
    val version: String,
)
