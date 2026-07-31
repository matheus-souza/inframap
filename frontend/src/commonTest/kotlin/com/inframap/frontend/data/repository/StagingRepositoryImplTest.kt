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

class StagingRepositoryImplTest {
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
    fun getStagingDevicesSuccessMapsToDomainList() =
        runTest {
            val json =
                """
                {
                    "data": {
                        "items": [
                            {"id": "stg-1", "hostname": "host1", "device_type": "server", "status": "pending"}
                        ],
                        "total": 1,
                        "page": 1,
                        "per_page": 10
                    },
                    "meta": {"request_id": "req-1"}
                }
                """.trimIndent()

            val repo = StagingRepositoryImpl(createMockApiClient(json))
            val result = repo.getStagingDevices(1, 10)

            assertIs<ApiResult.Success<*>>(result)
            val list = (result as ApiResult.Success).data
            assertEquals(1, list.items.size)
            assertEquals("stg-1", list.items[0].id)
        }

    @Test
    fun approveDeviceSuccessMapsToDomainDevice() =
        runTest {
            val json =
                """
                {
                    "data": {"id": "dev-1", "hostname": "host1", "device_type": "server", "status": "active"},
                    "meta": {"request_id": "req-1"}
                }
                """.trimIndent()

            val repo = StagingRepositoryImpl(createMockApiClient(json))
            val result = repo.approveDevice("stg-1")

            assertIs<ApiResult.Success<*>>(result)
            assertEquals("dev-1", (result as ApiResult.Success).data.id)
        }

    @Test
    fun dismissDeviceSuccessReturnsUnit() =
        runTest {
            val json =
                """
                {
                    "data": {},
                    "meta": {"request_id": "req-1"}
                }
                """.trimIndent()

            val repo = StagingRepositoryImpl(createMockApiClient(json))
            val result = repo.dismissDevice("stg-1")

            assertIs<ApiResult.Success<*>>(result)
        }
}
