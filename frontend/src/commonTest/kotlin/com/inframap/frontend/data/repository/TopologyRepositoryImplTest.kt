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

class TopologyRepositoryImplTest {
    private fun createMockApiClient(
        jsonResponse: String,
        status: HttpStatusCode = HttpStatusCode.OK,
    ): ApiClient {
        val mockEngine =
            MockEngine { request ->
                assertEquals("/api/v1/topology/graph", request.url.encodedPath)
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
    fun getTopologyGraphSuccessMapsToDomainGraph() =
        runTest {
            val json =
                """
                {
                    "data": {
                        "nodes": [
                            {"id": "n1", "label": "r1", "device_type": "router", "status": "active"},
                            {"id": "n2", "label": "sw1", "device_type": "switch", "status": "active"}
                        ],
                        "edges": [
                            {"id": "e1", "source": "n1", "target": "n2", "link_type": "physical"}
                        ]
                    },
                    "meta": {"request_id": "req-top-1"}
                }
                """.trimIndent()

            val repo = TopologyRepositoryImpl(createMockApiClient(json))
            val result = repo.getTopologyGraph()

            assertIs<ApiResult.Success<*>>(result)
            val graph = (result as ApiResult.Success).data
            assertEquals(2, graph.nodes.size)
            assertEquals(1, graph.edges.size)
            assertEquals("r1", graph.nodes.first().label)
            assertEquals("physical", graph.edges.first().linkType)
        }

    @Test
    fun getTopologyGraphErrorReturnsApiError() =
        runTest {
            val json =
                """
                {
                    "error": {"code": "SERVICE_UNAVAILABLE", "message": "Graph engine starting"},
                    "meta": {"request_id": "req-top-err"}
                }
                """.trimIndent()

            val repo = TopologyRepositoryImpl(createMockApiClient(json, HttpStatusCode.ServiceUnavailable))
            val result = repo.getTopologyGraph()

            assertIs<ApiResult.Error>(result)
            val err = result as ApiResult.Error
            assertEquals("SERVICE_UNAVAILABLE", err.code)
            assertEquals("Graph engine starting", err.message)
        }

    @Test
    fun getTopologyGraphWithBackendRealSchemaAndNullsReturnsEmptyGraph() =
        runTest {
            val json =
                """
                {
                    "data": {
                        "nodes": null,
                        "edges": null,
                        "metadata": {"total_nodes": 0, "total_edges": 0}
                    },
                    "meta": {"request_id": "req-top-empty"}
                }
                """.trimIndent()

            val repo = TopologyRepositoryImpl(createMockApiClient(json))
            val result = repo.getTopologyGraph()

            assertIs<ApiResult.Success<*>>(result)
            val graph = (result as ApiResult.Success).data
            assertEquals(0, graph.nodes.size)
            assertEquals(0, graph.edges.size)
        }
}
