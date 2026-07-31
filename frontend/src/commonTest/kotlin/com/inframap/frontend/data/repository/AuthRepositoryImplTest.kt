package com.inframap.frontend.data.repository

import com.inframap.frontend.data.api.ApiClient
import com.inframap.frontend.data.api.ApiResult
import com.inframap.frontend.data.dto.LoginRequest
import com.inframap.frontend.data.dto.OnboardRequest
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class AuthRepositoryImplTest {
    private fun createMockApiClient(
        jsonResponse: String,
        status: HttpStatusCode = HttpStatusCode.OK,
    ): ApiClient {
        val mockEngine =
            MockEngine { _ ->
                respond(
                    content = jsonResponse,
                    status = status,
                    headers = headersOf(HttpHeaders.ContentType, "application/json"),
                )
            }
        val httpClient =
            HttpClient(mockEngine) {
                install(ContentNegotiation) {
                    json(
                        Json {
                            ignoreUnknownKeys = true
                            isLenient = true
                        },
                    )
                }
            }
        return ApiClient("http://localhost:8080", httpClient)
    }

    @Test
    fun getSetupStatusSuccessMapsToSetupStatus() =
        runTest {
            val json =
                """
                {
                    "data": {"onboarding_completed": true, "system_instance_id": "sys-1"},
                    "meta": {"request_id": "req-1"}
                }
                """.trimIndent()

            val repo = AuthRepositoryImpl(createMockApiClient(json))
            val result = repo.getSetupStatus()

            assertIs<ApiResult.Success<*>>(result)
            assertTrue((result as ApiResult.Success).data.onboardingCompleted)
            assertEquals("sys-1", result.data.systemInstanceId)
        }

    @Test
    fun loginSuccessMapsToLoginResult() =
        runTest {
            val json =
                """
                {
                    "data": {
                        "token": "tok-123",
                        "user_id": "u-1",
                        "username": "admin",
                        "email": "admin@test.com",
                        "full_name": "Admin User",
                        "permissions": ["admin"]
                    },
                    "meta": {"request_id": "req-1"}
                }
                """.trimIndent()

            val repo = AuthRepositoryImpl(createMockApiClient(json))
            val result = repo.login(LoginRequest("admin", "pass"))

            assertIs<ApiResult.Success<*>>(result)
            assertEquals("tok-123", (result as ApiResult.Success).data.token)
        }

    @Test
    fun onboardSuccessMapsToOnboardResult() =
        runTest {
            val json =
                """
                {
                    "data": {
                        "onboarding_completed": true,
                        "system_instance_id": "sys-1",
                        "admin_user_id": "u-1"
                    },
                    "meta": {"request_id": "req-1"}
                }
                """.trimIndent()

            val repo = AuthRepositoryImpl(createMockApiClient(json))
            val result = repo.onboard(OnboardRequest("admin", "a@test.com", "pass", "Admin"))

            assertIs<ApiResult.Success<*>>(result)
            assertTrue((result as ApiResult.Success).data.onboardingCompleted)
        }

    @Test
    fun getCurrentUserSuccessMapsToUser() =
        runTest {
            val json =
                """
                {
                    "data": {
                        "id": "u-1",
                        "username": "admin",
                        "email": "admin@test.com",
                        "full_name": "Admin",
                        "is_active": true,
                        "permissions": []
                    },
                    "meta": {"request_id": "req-1"}
                }
                """.trimIndent()

            val repo = AuthRepositoryImpl(createMockApiClient(json))
            val result = repo.getCurrentUser()

            assertIs<ApiResult.Success<*>>(result)
            assertEquals("u-1", (result as ApiResult.Success).data.id)
        }
}
