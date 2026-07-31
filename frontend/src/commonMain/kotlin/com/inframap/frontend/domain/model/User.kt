package com.inframap.frontend.domain.model

data class User(
    val id: String,
    val username: String,
    val email: String = "",
    val fullName: String = "",
    val isActive: Boolean = true,
    val permissions: List<String> = emptyList(),
)

data class LoginResult(
    val token: String,
    val userId: String,
    val username: String,
    val email: String = "",
    val fullName: String = "",
    val permissions: List<String> = emptyList(),
    val expiresAt: String = "",
)

data class SetupStatus(
    val onboardingCompleted: Boolean,
    val systemInstanceId: String = "",
)

data class OnboardResult(
    val onboardingCompleted: Boolean,
    val systemInstanceId: String,
    val adminUserId: String,
)
