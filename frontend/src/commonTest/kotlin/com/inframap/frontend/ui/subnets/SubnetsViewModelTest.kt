package com.inframap.frontend.ui.subnets

import com.inframap.frontend.data.api.ApiClient
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull

@OptIn(ExperimentalCoroutinesApi::class)
class SubnetsViewModelTest {
    private val jsonHeaders =
        headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString())

    private fun createClient(handler: suspend (String, String) -> Pair<HttpStatusCode, String>): ApiClient {
        val engine =
            MockEngine { request ->
                val (status, body) = handler(request.method.value, request.url.encodedPath)
                respond(body, status, jsonHeaders)
            }
        val httpClient =
            HttpClient(engine) {
                install(ContentNegotiation) {
                    json(
                        Json {
                            ignoreUnknownKeys = true
                            isLenient = true
                        },
                    )
                }
            }
        return ApiClient(baseUrl = "", httpClient = httpClient)
    }

    private val defaultMockHandler: suspend (String, String) -> Pair<HttpStatusCode, String> = { method, path ->
        when {
            method.equals("GET", ignoreCase = true) && path.endsWith("/subnets") ->
                HttpStatusCode.OK to
                    """{"data":{"items":[{"id":"sub1","name":"Management","cidr":"192.168.1.0/24","vlan_id":10,"gateway_ip":"192.168.1.1","discovery_enabled":true}],"total":1},"meta":{"request_id":"r1"}}"""
            else ->
                HttpStatusCode.NotFound to """{"error":{"code":"NOT_FOUND","message":"Not found"},"meta":{"request_id":"r_err"}}"""
        }
    }

    @Test
    fun loadSubnetsPopulatesListSuccessfully() =
        runTest {
            val client = createClient(defaultMockHandler)
            val vm = SubnetsViewModel(client, scope = this)

            val stateDeferred = async { vm.state.first { !it.isLoading } }
            advanceUntilIdle()
            val state = stateDeferred.await()

            assertFalse(state.isLoading)
            assertNull(state.errorMessage)
            assertEquals(1, state.subnets.size)
            assertEquals("Management", state.subnets.first().name)
            assertEquals("192.168.1.0/24", state.subnets.first().cidr)

            vm.dismissToast()
            assertNull(vm.state.value.toastMessage)
        }

    @Test
    fun loadSubnetsHandlesApiError() =
        runTest {
            val client =
                createClient { _, path ->
                    if (path.endsWith("/subnets")) {
                        HttpStatusCode.InternalServerError to
                            """{"error":{"code":"DB_ERR","message":"DB Error"},"meta":{"request_id":"r_err"}}"""
                    } else {
                        defaultMockHandler("GET", path)
                    }
                }

            val vm = SubnetsViewModel(client, scope = this)
            val stateDeferred = async { vm.state.first { !it.isLoading } }
            advanceUntilIdle()
            val state = stateDeferred.await()

            assertFalse(state.isLoading)
            assertEquals("DB Error", state.errorMessage)
        }

    @Test
    fun loadSubnetsHandlesNetworkError() =
        runTest {
            val engine = MockEngine { throw RuntimeException("Network issue") }
            val httpClient = HttpClient(engine) { install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) } }
            val client = ApiClient(baseUrl = "", httpClient = httpClient)

            val vm = SubnetsViewModel(client, scope = this)
            val stateDeferred = async { vm.state.first { !it.isLoading } }
            advanceUntilIdle()
            val state = stateDeferred.await()

            assertFalse(state.isLoading)
            assertEquals("Erro de rede. Não foi possível conectar ao servidor.", state.errorMessage)
        }
}
