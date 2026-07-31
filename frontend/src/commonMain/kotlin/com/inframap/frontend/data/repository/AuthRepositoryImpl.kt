package com.inframap.frontend.data.repository

import com.inframap.frontend.data.api.ApiClient
import com.inframap.frontend.data.api.ApiResult
import com.inframap.frontend.data.api.map
import com.inframap.frontend.data.dto.LoginRequest
import com.inframap.frontend.data.dto.LoginResponseDto
import com.inframap.frontend.data.dto.OnboardRequest
import com.inframap.frontend.data.dto.OnboardResponseDto
import com.inframap.frontend.data.dto.SetupStatusDto
import com.inframap.frontend.data.dto.UserProfileDto
import com.inframap.frontend.data.mapper.AuthMapper
import com.inframap.frontend.domain.model.LoginResult
import com.inframap.frontend.domain.model.OnboardResult
import com.inframap.frontend.domain.model.SetupStatus
import com.inframap.frontend.domain.model.User
import com.inframap.frontend.domain.repository.AuthRepository

class AuthRepositoryImpl(
    private val apiClient: ApiClient,
) : AuthRepository {
    override suspend fun getSetupStatus(): ApiResult<SetupStatus> =
        apiClient.get<SetupStatusDto>("/api/v1/setup/status").map { AuthMapper.toDomain(it) }

    override suspend fun login(request: LoginRequest): ApiResult<LoginResult> =
        apiClient.post<LoginResponseDto, LoginRequest>("/api/v1/auth/login", request).map {
            AuthMapper.toDomain(it)
        }

    override suspend fun onboard(request: OnboardRequest): ApiResult<OnboardResult> =
        apiClient.post<OnboardResponseDto, OnboardRequest>("/api/v1/setup/onboard", request).map {
            AuthMapper.toDomain(it)
        }

    override suspend fun getCurrentUser(): ApiResult<User> =
        apiClient.get<UserProfileDto>("/api/v1/auth/me").map { AuthMapper.toDomain(it) }
}
