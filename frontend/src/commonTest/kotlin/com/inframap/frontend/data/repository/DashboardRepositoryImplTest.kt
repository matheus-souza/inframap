package com.inframap.frontend.data.repository

import com.inframap.frontend.data.api.ApiClient
import com.inframap.frontend.data.api.ApiResult
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

class DashboardRepositoryImplTest {
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
    fun getHealthSuccessMapsToHealth() =
        runTest {
            val json =
                """
                {
                    "data": {"status": "ok", "version": "1.0.0"},
                    "meta": {"request_id": "req-1"}
                }
                """.trimIndent()

            val repo = DashboardRepositoryImpl(createMockApiClient(json))
            val result = repo.getHealth()

            assertIs<ApiResult.Success<*>>(result)
            assertEquals("ok", (result as ApiResult.Success).data.status)
        }

    @Test
    fun getStagingSummarySuccessMapsToStagingList() =
        runTest {
            val json =
                """
                {
                    "data": {
                        "items": [
                            {"id": "stg-1", "hostname": "host1", "device_type": "server"}
                        ],
                        "total": 1,
                        "page": 1,
                        "per_page": 5
                    },
                    "meta": {"request_id": "req-1"}
                }
                """.trimIndent()

            val repo = DashboardRepositoryImpl(createMockApiClient(json))
            val result = repo.getStagingSummary()

            assertIs<ApiResult.Success<*>>(result)
            assertEquals(1, (result as ApiResult.Success).data.items.size)
        }

    @Test
    fun getDiscoverySourcesSuccessMapsToSourceList() =
        runTest {
            val json =
                """
                {
                    "data": {
                        "items": [
                            {"id": "src-1", "name": "Docker", "source_type": "docker", "enabled": true}
                        ],
                        "total": 1
                    },
                    "meta": {"request_id": "req-1"}
                }
                """.trimIndent()

            val repo = DashboardRepositoryImpl(createMockApiClient(json))
            val result = repo.getDiscoverySources()

            assertIs<ApiResult.Success<*>>(result)
            assertEquals(1, (result as ApiResult.Success).data.size)
            assertEquals("Docker", result.data[0].name)
        }
}
