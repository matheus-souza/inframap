package com.inframap.frontend.data.repository

import com.inframap.frontend.data.api.ApiClient
import com.inframap.frontend.data.api.ApiResult
import com.inframap.frontend.data.dto.CreateSubnetRequest
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

class SubnetRepositoryImplTest {
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
    fun getSubnetsSuccessMapsToDomainList() =
        runTest {
            val json =
                """
                {
                    "data": {
                        "items": [
                            {"id": "sub-1", "name": "LAN", "cidr": "192.168.1.0/24"}
                        ],
                        "total": 1
                    },
                    "meta": {"request_id": "req-1"}
                }
                """.trimIndent()

            val repo = SubnetRepositoryImpl(createMockApiClient(json))
            val result = repo.getSubnets()

            assertIs<ApiResult.Success<*>>(result)
            val list = (result as ApiResult.Success).data
            assertEquals(1, list.items.size)
            assertEquals("LAN", list.items[0].name)
        }

    @Test
    fun createSubnetSuccessMapsToDomainSubnet() =
        runTest {
            val json =
                """
                {
                    "data": {"id": "sub-1", "name": "LAN", "cidr": "192.168.1.0/24"},
                    "meta": {"request_id": "req-1"}
                }
                """.trimIndent()

            val repo = SubnetRepositoryImpl(createMockApiClient(json))
            val result = repo.createSubnet(CreateSubnetRequest(name = "LAN", cidr = "192.168.1.0/24"))

            assertIs<ApiResult.Success<*>>(result)
            assertEquals("sub-1", (result as ApiResult.Success).data.id)
        }
}
