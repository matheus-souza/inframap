package com.inframap.frontend.ui.devices

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
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class DeviceDetailViewModelTest {
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
            method.equals("GET", ignoreCase = true) && path.endsWith("/devices/d1") ->
                HttpStatusCode.OK to
                    """{"data":{"id":"d1","hostname":"router-01","ip_address":"192.168.1.1","device_type":"router","status":"active"},"meta":{"request_id":"r1"}}"""
            method.equals("DELETE", ignoreCase = true) && path.contains("devices") ->
                HttpStatusCode.OK to
                    """{"data":{"message":"deleted"},"meta":{"request_id":"r2"}}"""
            else ->
                HttpStatusCode.NotFound to """{"error":{"code":"NOT_FOUND","message":"Device not found"},"meta":{"request_id":"r_err"}}"""
        }
    }

    @Test
    fun loadDevicePopulatesDetailsSuccessfully() =
        runTest {
            val client = createClient(defaultMockHandler)
            val vm = DeviceDetailViewModel("d1", client, scope = this)

            val stateDeferred = async { vm.state.first { !it.isLoading } }
            advanceUntilIdle()
            val state = stateDeferred.await()

            assertFalse(state.isLoading)
            assertNull(state.errorMessage)
            assertEquals("router-01", state.device?.hostname)
            assertEquals("192.168.1.1", state.device?.ipAddress)
            vm.clear()
        }

    @Test
    fun loadDeviceHandlesNotFoundApiError() =
        runTest {
            val client =
                createClient { _, path ->
                    HttpStatusCode.NotFound to
                        """{"error":{"code":"NOT_FOUND","message":"Device not found"},"meta":{"request_id":"r_err"}}"""
                }

            val vm = DeviceDetailViewModel("unknown", client, scope = this)
            val stateDeferred = async { vm.state.first { !it.isLoading } }
            advanceUntilIdle()
            val state = stateDeferred.await()

            assertFalse(state.isLoading)
            assertEquals("Device not found", state.errorMessage)
            vm.clear()
        }

    @Test
    fun loadDeviceHandlesNetworkError() =
        runTest {
            val engine = MockEngine { throw RuntimeException("Connection timed out") }
            val httpClient = HttpClient(engine) { install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) } }
            val client = ApiClient(baseUrl = "", httpClient = httpClient)

            val vm = DeviceDetailViewModel("d1", client, scope = this)
            val stateDeferred = async { vm.state.first { !it.isLoading } }
            advanceUntilIdle()
            val state = stateDeferred.await()

            assertFalse(state.isLoading)
            assertEquals("Network error. Failed to reach server.", state.errorMessage)
            vm.clear()
        }

    @Test
    fun deleteDeviceIgnoresReentrantCallsWhenDeleting() =
        runTest {
            val client = createClient(defaultMockHandler)
            val vm = DeviceDetailViewModel("d1", client, scope = this)
            val loadDeferred = async { vm.state.first { !it.isLoading } }
            advanceUntilIdle()
            loadDeferred.await()

            vm.deleteDevice {}
            assertTrue(vm.state.value.isDeleting)

            vm.deleteDevice {}
            assertTrue(vm.state.value.isDeleting)

            advanceUntilIdle()
            vm.clear()
        }

    @Test
    fun deleteDeviceHandlesApiError() =
        runTest {
            val client =
                createClient { method, path ->
                    if (method == "DELETE") {
                        HttpStatusCode.InternalServerError to
                            """{"error":{"code":"DB_FAIL","message":"Deletion locked"},"meta":{"request_id":"r_err"}}"""
                    } else {
                        defaultMockHandler(method, path)
                    }
                }

            val vm = DeviceDetailViewModel("d1", client, scope = this)
            advanceUntilIdle()

            val stateDeferred = async { vm.state.first { it.errorMessage != null } }
            vm.deleteDevice {}
            advanceUntilIdle()

            val state = stateDeferred.await()
            assertEquals("Deletion locked", state.errorMessage)
            assertFalse(state.isDeleting)
            vm.clear()
        }

    @Test
    fun deleteDeviceHandlesNetworkError() =
        runTest {
            val engine =
                MockEngine { request ->
                    if (request.method.value == "DELETE") {
                        throw RuntimeException("Network fail")
                    } else {
                        respond(
                            """{"data":{"id":"d1","hostname":"r1","device_type":"router","status":"active"},"meta":{"request_id":"r1"}}""",
                            HttpStatusCode.OK,
                            jsonHeaders,
                        )
                    }
                }
            val httpClient = HttpClient(engine) { install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) } }
            val client = ApiClient(baseUrl = "", httpClient = httpClient)

            val vm = DeviceDetailViewModel("d1", client, scope = this)
            advanceUntilIdle()

            val stateDeferred = async { vm.state.first { it.errorMessage != null } }
            vm.deleteDevice {}
            advanceUntilIdle()

            val state = stateDeferred.await()
            assertEquals("Network error. Failed to delete device.", state.errorMessage)
            assertFalse(state.isDeleting)
            vm.clear()
        }

    @Test
    fun openCloseAndDeleteDialogWorkflow() =
        runTest {
            val client = createClient(defaultMockHandler)
            val vm = DeviceDetailViewModel("d1", client, scope = this)
            val loadDeferred = async { vm.state.first { !it.isLoading } }
            advanceUntilIdle()
            loadDeferred.await()

            vm.openDeleteDialog()
            assertTrue(vm.state.value.showDeleteDialog)

            vm.closeDeleteDialog()
            assertFalse(vm.state.value.showDeleteDialog)

            var navTriggered = false
            val deleteDeferred = async { vm.state.first { !it.isDeleting } }
            vm.deleteDevice { navTriggered = true }
            advanceUntilIdle()
            deleteDeferred.await()

            assertTrue(navTriggered)
            assertFalse(vm.state.value.isDeleting)
            vm.clear()
        }
}
