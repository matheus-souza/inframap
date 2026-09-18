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

    @Test
    fun getSubnetByIdSuccessMapsToDomainSubnet() =
        runTest {
            val json =
                """
                {
                    "data": {"id": "sub-1", "name": "LAN", "cidr": "192.168.1.0/24"},
                    "meta": {"request_id": "req-1"}
                }
                """.trimIndent()

            val repo = SubnetRepositoryImpl(createMockApiClient(json))
            val result = repo.getSubnetById("sub-1")

            assertIs<ApiResult.Success<*>>(result)
            assertEquals("sub-1", (result as ApiResult.Success).data.id)
            assertEquals("LAN", result.data.name)
        }

    @Test
    fun updateSubnetSuccessMapsToDomainSubnet() =
        runTest {
            val json =
                """
                {
                    "data": {"id": "sub-1", "name": "LAN Updated", "cidr": "192.168.1.0/24"},
                    "meta": {"request_id": "req-1"}
                }
                """.trimIndent()

            val repo = SubnetRepositoryImpl(createMockApiClient(json))
            val result =
                repo.updateSubnet(
                    "sub-1",
                    com.inframap.frontend.data.dto
                        .UpdateSubnetRequest(name = "LAN Updated", cidr = "192.168.1.0/24"),
                )

            assertIs<ApiResult.Success<*>>(result)
            assertEquals("sub-1", (result as ApiResult.Success).data.id)
            assertEquals("LAN Updated", result.data.name)
        }

    @Test
    fun getSubnetCidrImpactReturnsImpactResponse() =
        runTest {
            val json =
                """
                {
                    "data": {
                        "current_cidr": "192.168.1.0/24",
                        "new_cidr": "192.168.1.0/25",
                        "affected_devices_count": 5
                    },
                    "meta": {"request_id": "req-1"}
                }
                """.trimIndent()

            val repo = SubnetRepositoryImpl(createMockApiClient(json))
            val result =
                repo.getSubnetCidrImpact(
                    "sub-1",
                    com.inframap.frontend.data.dto
                        .SubnetCidrImpactRequest("192.168.1.0/25"),
                )

            assertIs<ApiResult.Success<*>>(result)
            val data = (result as ApiResult.Success).data
            assertEquals("192.168.1.0/24", data.currentCidr)
            assertEquals("192.168.1.0/25", data.newCidr)
            assertEquals(5, data.affectedDevicesCount)
        }

    @Test
    fun getSubnetDeletionImpactReturnsImpactResponse() =
        runTest {
            val json =
                """
                {
                    "data": {
                        "subnet_id": "sub-1",
                        "impact": {
                            "affected_devices": 3,
                            "unlinked_topology_edges": 2
                        }
                    },
                    "meta": {"request_id": "req-1"}
                }
                """.trimIndent()

            val repo = SubnetRepositoryImpl(createMockApiClient(json))
            val result = repo.getSubnetDeletionImpact("sub-1")

            assertIs<ApiResult.Success<*>>(result)
            val data = (result as ApiResult.Success).data
            assertEquals("sub-1", data.subnetId)
            assertEquals(3, data.impact.affectedDevices)
            assertEquals(2, data.impact.unlinkedTopologyEdges)
        }

    @Test
    fun deleteSubnetReturnsDeleteResponse() =
        runTest {
            val json =
                """
                {
                    "data": {
                        "deleted_id": "sub-1",
                        "impact": {
                            "affected_devices": 3,
                            "unlinked_topology_edges": 2
                        }
                    },
                    "meta": {"request_id": "req-1"}
                }
                """.trimIndent()

            val repo = SubnetRepositoryImpl(createMockApiClient(json))
            val result = repo.deleteSubnet("sub-1")

            assertIs<ApiResult.Success<*>>(result)
            val data = (result as ApiResult.Success).data
            assertEquals("sub-1", data.deletedId)
            assertEquals(3, data.impact.affectedDevices)
            assertEquals(2, data.impact.unlinkedTopologyEdges)
        }
}
