package com.inframap.frontend.data.mapper

import com.inframap.frontend.data.dto.LoginResponseDto
import com.inframap.frontend.data.dto.OnboardResponseDto
import com.inframap.frontend.data.dto.SetupStatusDto
import com.inframap.frontend.data.dto.UserProfileDto
import com.inframap.frontend.domain.model.LoginResult
import com.inframap.frontend.domain.model.OnboardResult
import com.inframap.frontend.domain.model.SetupStatus
import com.inframap.frontend.domain.model.User

object AuthMapper {
    fun toDomain(dto: SetupStatusDto): SetupStatus =
        SetupStatus(
            onboardingCompleted = dto.onboardingCompleted,
            systemInstanceId = dto.systemInstanceId,
        )

    fun toDomain(dto: LoginResponseDto): LoginResult =
        LoginResult(
            token = dto.token,
            userId = dto.userId,
            username = dto.username,
            email = dto.email,
            fullName = dto.fullName,
            permissions = dto.permissions,
            expiresAt = dto.expiresAt,
        )

    fun toDomain(dto: OnboardResponseDto): OnboardResult =
        OnboardResult(
            onboardingCompleted = dto.onboardingCompleted,
            systemInstanceId = dto.systemInstanceId,
            adminUserId = dto.adminUserId,
        )

    fun toDomain(dto: UserProfileDto): User =
        User(
            id = dto.id,
            username = dto.username,
            email = dto.email,
            fullName = dto.fullName,
            isActive = dto.isActive,
            permissions = dto.permissions,
        )
}
