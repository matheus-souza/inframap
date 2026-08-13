package com.inframap.frontend.data.repository

import com.inframap.frontend.data.api.ApiClient
import com.inframap.frontend.data.api.ApiResult
import com.inframap.frontend.data.dto.CreateDiscoverySourceRequest
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

class DiscoveryRepositoryImplTest {
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
    fun getSourcesSuccessMapsToDomainList() =
        runTest {
            val json =
                """
                {
                    "data": {
                        "items": [
                            {"id": "src-1", "name": "Docker", "type": "docker",
                             "enabled": true, "last_status": "idle"}
                        ],
                        "total": 1
                    },
                    "meta": {"request_id": "req-1"}
                }
                """.trimIndent()

            val repo = DiscoveryRepositoryImpl(createMockApiClient(json))
            val result = repo.getSources()

            assertIs<ApiResult.Success<*>>(result)
            val list = (result as ApiResult.Success).data
            assertEquals(1, list.items.size)
            assertEquals("Docker", list.items[0].name)
            assertEquals("docker", list.items[0].sourceType)
        }

    @Test
    fun createSourceSuccessMapsToDomainSource() =
        runTest {
            val json =
                """
                {
                    "data": {"id": "src-1", "name": "Docker", "type": "docker",
                             "enabled": true, "last_status": "idle"},
                    "meta": {"request_id": "req-1"}
                }
                """.trimIndent()

            val repo = DiscoveryRepositoryImpl(createMockApiClient(json))
            val result =
                repo.createSource(
                    CreateDiscoverySourceRequest(name = "Docker", type = "docker"),
                )

            assertIs<ApiResult.Success<*>>(result)
            assertEquals("src-1", (result as ApiResult.Success).data.id)
        }

    @Test
    fun triggerRunSuccessMapsToDomainSource() =
        runTest {
            val json =
                """
                {
                    "data": {"id": "src-1", "name": "Docker", "type": "docker",
                             "enabled": true, "last_status": "running"},
                    "meta": {"request_id": "req-1"}
                }
                """.trimIndent()

            val repo = DiscoveryRepositoryImpl(createMockApiClient(json))
            val result = repo.triggerRun("src-1")

            assertIs<ApiResult.Success<*>>(result)
            assertEquals("running", (result as ApiResult.Success).data.lastStatus)
        }

    @Test
    fun deleteSourceReturnsSuccess() =
        runTest {
            val json =
                """
                {
                    "data": {},
                    "meta": {"request_id": "req-1"}
                }
                """.trimIndent()

            val repo = DiscoveryRepositoryImpl(createMockApiClient(json))
            val result = repo.deleteSource("src-1")

            assertIs<ApiResult.Success<*>>(result)
        }

    @Test
    fun getSourcesReturnsErrorOnApiFailure() =
        runTest {
            val json =
                """
                {
                    "error": {"code": "INTERNAL", "message": "DB error"},
                    "meta": {"request_id": "req-err"}
                }
                """.trimIndent()

            val repo =
                DiscoveryRepositoryImpl(
                    createMockApiClient(json, HttpStatusCode.InternalServerError),
                )
            val result = repo.getSources()

            assertIs<ApiResult.Error>(result)
            assertEquals("INTERNAL", result.code)
        }
}
