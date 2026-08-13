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

class NetworkRepositoryImplTest {
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
    fun getInterfacesSuccessMapsToDomainList() =
        runTest {
            val json =
                """
                {
                    "data": [
                        {"name": "eth0", "ip": "192.168.18.5",
                         "cidr": "192.168.18.0/24", "mac": "aa:bb:cc:dd:ee:ff",
                         "gateway": "192.168.18.1"}
                    ],
                    "meta": {"request_id": "req-1"}
                }
                """.trimIndent()

            val repo = NetworkRepositoryImpl(createMockApiClient(json))
            val result = repo.getInterfaces()

            assertIs<ApiResult.Success<*>>(result)
            val interfaces = (result as ApiResult.Success).data
            assertEquals(1, interfaces.size)
            assertEquals("eth0", interfaces[0].name)
            assertEquals("192.168.18.0/24", interfaces[0].cidr)
            assertEquals("192.168.18.1", interfaces[0].gateway)
        }

    @Test
    fun getInterfacesReturnsErrorOnApiFailure() =
        runTest {
            val json =
                """
                {
                    "error": {"code": "INTERNAL", "message": "Server error"},
                    "meta": {"request_id": "req-err"}
                }
                """.trimIndent()

            val repo =
                NetworkRepositoryImpl(
                    createMockApiClient(json, HttpStatusCode.InternalServerError),
                )
            val result = repo.getInterfaces()

            assertIs<ApiResult.Error>(result)
            assertEquals("INTERNAL", result.code)
        }
}
