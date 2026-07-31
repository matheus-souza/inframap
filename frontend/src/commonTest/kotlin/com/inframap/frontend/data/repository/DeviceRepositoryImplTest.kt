package com.inframap.frontend.data.repository

import com.inframap.frontend.data.api.ApiClient
import com.inframap.frontend.data.api.ApiResult
import com.inframap.frontend.data.dto.CreateDeviceRequest
import com.inframap.frontend.data.dto.UpdateDeviceRequest
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

class DeviceRepositoryImplTest {
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
    fun getDevicesSuccessMapsToDomainList() =
        runTest {
            val json =
                """
                {
                    "data": {
                        "items": [
                            {"id": "d1", "hostname": "srv1", "device_type": "server", "status": "active"}
                        ],
                        "total": 1,
                        "page": 1,
                        "per_page": 10
                    },
                    "meta": {"request_id": "req-1"}
                }
                """.trimIndent()

            val repo = DeviceRepositoryImpl(createMockApiClient(json))
            val result = repo.getDevices(1, 10, "srv")

            assertIs<ApiResult.Success<*>>(result)
            val list = (result as ApiResult.Success).data
            assertEquals(1, list.items.size)
            assertEquals("srv1", list.items[0].hostname)
        }

    @Test
    fun getDeviceByIdSuccessMapsToDomain() =
        runTest {
            val json =
                """
                {
                    "data": {"id": "d1", "hostname": "srv1", "device_type": "server", "status": "active"},
                    "meta": {"request_id": "req-1"}
                }
                """.trimIndent()

            val repo = DeviceRepositoryImpl(createMockApiClient(json))
            val result = repo.getDeviceById("d1")

            assertIs<ApiResult.Success<*>>(result)
            val dev = (result as ApiResult.Success).data
            assertEquals("d1", dev.id)
            assertEquals("srv1", dev.hostname)
        }

    @Test
    fun createDeviceSuccessMapsToDomain() =
        runTest {
            val json =
                """
                {
                    "data": {"id": "d1", "hostname": "new-srv", "device_type": "server", "status": "pending"},
                    "meta": {"request_id": "req-1"}
                }
                """.trimIndent()

            val repo = DeviceRepositoryImpl(createMockApiClient(json))
            val result = repo.createDevice(CreateDeviceRequest(hostname = "new-srv", deviceType = "server"))

            assertIs<ApiResult.Success<*>>(result)
            assertEquals("new-srv", (result as ApiResult.Success).data.hostname)
        }

    @Test
    fun updateDeviceSuccessMapsToDomain() =
        runTest {
            val json =
                """
                {
                    "data": {"id": "d1", "hostname": "updated-srv", "device_type": "server", "status": "active"},
                    "meta": {"request_id": "req-1"}
                }
                """.trimIndent()

            val repo = DeviceRepositoryImpl(createMockApiClient(json))
            val result = repo.updateDevice("d1", UpdateDeviceRequest(hostname = "updated-srv"))

            assertIs<ApiResult.Success<*>>(result)
            assertEquals("updated-srv", (result as ApiResult.Success).data.hostname)
        }

    @Test
    fun deleteDeviceSuccessReturnsUnit() =
        runTest {
            val json =
                """
                {
                    "data": {},
                    "meta": {"request_id": "req-1"}
                }
                """.trimIndent()

            val repo = DeviceRepositoryImpl(createMockApiClient(json))
            val result = repo.deleteDevice("d1")

            assertIs<ApiResult.Success<*>>(result)
        }

    @Test
    fun getDevicesErrorReturnsApiError() =
        runTest {
            val json =
                """
                {
                    "error": {"code": "INTERNAL_ERROR", "message": "Database error"},
                    "meta": {"request_id": "req-err"}
                }
                """.trimIndent()

            val repo = DeviceRepositoryImpl(createMockApiClient(json, HttpStatusCode.InternalServerError))
            val result = repo.getDevices()

            assertIs<ApiResult.Error>(result)
            val err = result as ApiResult.Error
            assertEquals("INTERNAL_ERROR", err.code)
            assertEquals("Database error", err.message)
        }
}
