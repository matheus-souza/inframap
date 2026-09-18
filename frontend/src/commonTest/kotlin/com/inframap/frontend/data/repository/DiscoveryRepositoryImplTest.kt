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

    @Test
    fun getSourceByIdSuccessMapsToDomainSource() =
        runTest {
            val json =
                """
                {
                    "data": {
                        "id": "src-1",
                        "name": "Proxmox Lab",
                        "type": "proxmox",
                        "enabled": true,
                        "collectors": [
                            {
                                "id": "col-1",
                                "collector_type": "proxmox",
                                "enabled": true,
                                "config": {"api_url": "https://pve1.lab:8006"},
                                "configured_secrets": ["token_secret"]
                            }
                        ]
                    },
                    "meta": {"request_id": "req-1"}
                }
                """.trimIndent()

            val repo = DiscoveryRepositoryImpl(createMockApiClient(json))
            val result = repo.getSourceById("src-1")

            assertIs<ApiResult.Success<*>>(result)
            val src = (result as ApiResult.Success).data
            assertEquals("src-1", src.id)
            assertEquals("Proxmox Lab", src.name)
            assertEquals(1, src.collectors.size)
            assertEquals("https://pve1.lab:8006", src.collectors[0].config["api_url"])
            assertEquals(listOf("token_secret"), src.collectors[0].configuredSecrets)
        }

    @Test
    fun updateSourceSuccessMapsToDomainSource() =
        runTest {
            val json =
                """
                {
                    "data": {
                        "id": "src-1",
                        "name": "Updated Proxmox",
                        "type": "proxmox",
                        "enabled": true
                    },
                    "meta": {"request_id": "req-1"}
                }
                """.trimIndent()

            val repo = DiscoveryRepositoryImpl(createMockApiClient(json))
            val result =
                repo.updateSource(
                    "src-1",
                    com.inframap.frontend.data.dto.UpdateDiscoverySourceRequest(
                        name = "Updated Proxmox",
                        type = "proxmox",
                    ),
                )

            assertIs<ApiResult.Success<*>>(result)
            assertEquals("Updated Proxmox", (result as ApiResult.Success).data.name)
        }

    @Test
    fun testSourceHealthSuccessMapsToProviderHealth() =
        runTest {
            val json =
                """
                {
                    "data": {
                        "provider_id": "proxmox",
                        "status": "ok",
                        "message": "Connection established"
                    },
                    "meta": {"request_id": "req-1"}
                }
                """.trimIndent()

            val repo = DiscoveryRepositoryImpl(createMockApiClient(json))
            val result = repo.testSourceHealth("src-1", "proxmox")

            assertIs<ApiResult.Success<*>>(result)
            val health = (result as ApiResult.Success).data
            assertEquals("proxmox", health.providerId)
            assertEquals(true, health.isHealthy)
            assertEquals("Connection established", health.message)
        }

    @Test
    fun getDeletionImpactSuccessMapsToDomain() =
        runTest {
            val json =
                """
                {
                    "data": {
                        "source_id": "src-1",
                        "impact": {
                            "collectors_halted": 3,
                            "devices_unlinked": 8
                        }
                    },
                    "meta": {"request_id": "req-1"}
                }
                """.trimIndent()

            val repo = DiscoveryRepositoryImpl(createMockApiClient(json))
            val result = repo.getDeletionImpact("src-1")

            assertIs<ApiResult.Success<*>>(result)
            val impact = (result as ApiResult.Success).data
            assertEquals(3, impact.collectorsHalted)
            assertEquals(8, impact.devicesUnlinked)
        }
}
