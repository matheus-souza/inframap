package com.inframap.frontend.data.repository

import com.inframap.frontend.data.api.ApiClient
import com.inframap.frontend.data.api.ApiResult
import com.inframap.frontend.data.dto.CredentialInUseResponse
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

class CredentialsRepositoryImplTest {
    private val jsonFormat = Json { ignoreUnknownKeys = true }

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
    fun listCredentialsSuccessMapsToDomainList() =
        runTest {
            val json =
                """
                {
                    "data": {
                        "items": [
                            {"id": "cred-1", "name": "Homelab SSH", "type": "ssh_key"}
                        ],
                        "total": 1
                    },
                    "meta": {"request_id": "req-1"}
                }
                """.trimIndent()

            val repo = CredentialsRepositoryImpl(createMockApiClient(json))
            val result = repo.listCredentials()

            assertIs<ApiResult.Success<*>>(result)
            val list = (result as ApiResult.Success).data
            assertEquals(1, list.size)
            assertEquals("cred-1", list[0].id)
            assertEquals("Homelab SSH", list[0].name)
            assertEquals("ssh_key", list[0].type)
        }

    @Test
    fun deleteCredentialSuccessReturnsSuccess() =
        runTest {
            val json =
                """
                {
                    "data": {},
                    "meta": {"request_id": "req-del"}
                }
                """.trimIndent()

            val repo = CredentialsRepositoryImpl(createMockApiClient(json, HttpStatusCode.OK))
            val result = repo.deleteCredential("cred-1")

            assertIs<ApiResult.Success<*>>(result)
        }

    @Test
    fun deleteCredentialInUseReturnsConflictWithDependentSources() =
        runTest {
            val json =
                """
                {
                    "error": "CREDENTIAL_IN_USE",
                    "dependent_sources": [
                        {"id": "src-1", "name": "Active Proxmox"},
                        {"id": "src-2", "name": "Active Docker"}
                    ]
                }
                """.trimIndent()

            val repo = CredentialsRepositoryImpl(createMockApiClient(json, HttpStatusCode.Conflict))
            val result = repo.deleteCredential("cred-1")

            assertIs<ApiResult.Error>(result)
            assertEquals("CREDENTIAL_IN_USE", result.code)
            assertEquals(409, result.httpStatus)

            val details = result.detailsJson
            val inUseResponse =
                jsonFormat.decodeFromString<CredentialInUseResponse>(details ?: "")
            assertEquals("CREDENTIAL_IN_USE", inUseResponse.error)
            assertEquals(2, inUseResponse.dependentSources.size)
            assertEquals("Active Proxmox", inUseResponse.dependentSources[0].name)
            assertEquals("Active Docker", inUseResponse.dependentSources[1].name)
        }
}
