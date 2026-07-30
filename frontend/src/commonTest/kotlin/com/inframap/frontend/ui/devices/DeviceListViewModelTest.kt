package com.inframap.frontend.ui.devices

import com.inframap.frontend.data.api.ApiClient
import com.inframap.frontend.data.dto.DeviceDto
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
class DeviceListViewModelTest {
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
            method.equals("GET", ignoreCase = true) && path.endsWith("/devices") ->
                HttpStatusCode.OK to
                    """{"data":{"items":[{"id":"d1","hostname":"router-01","device_type":"router","status":"active"}],"total":1,"page":1,"per_page":50},"meta":{"request_id":"r1"}}"""
            method.equals("DELETE", ignoreCase = true) && path.contains("devices") ->
                HttpStatusCode.OK to
                    """{"data":{"message":"device soft-deleted successfully"},"meta":{"request_id":"r2"}}"""
            else ->
                HttpStatusCode.NotFound to """{"error":{"code":"NOT_FOUND","message":"Not found"},"meta":{"request_id":"r_err"}}"""
        }
    }

    @Test
    fun loadDevicesPopulatesListSuccessfully() =
        runTest {
            val client = createClient(defaultMockHandler)
            val vm = DeviceListViewModel(client, scope = this)

            val stateDeferred = async { vm.state.first { !it.isLoading } }
            advanceUntilIdle()
            val state = stateDeferred.await()

            assertFalse(state.isLoading)
            assertNull(state.errorMessage)
            assertEquals(1, state.devices.size)
            assertEquals("router-01", state.devices.first().hostname)
        }

    @Test
    fun loadDevicesHandlesApiError() =
        runTest {
            val client =
                createClient { _, path ->
                    if (path.endsWith("/devices")) {
                        HttpStatusCode.InternalServerError to
                            """{"error":{"code":"DB_ERR","message":"DB Error"},"meta":{"request_id":"r_err"}}"""
                    } else {
                        defaultMockHandler("GET", path)
                    }
                }

            val vm = DeviceListViewModel(client, scope = this)
            val stateDeferred = async { vm.state.first { !it.isLoading } }
            advanceUntilIdle()
            val state = stateDeferred.await()

            assertFalse(state.isLoading)
            assertEquals("DB Error", state.errorMessage)
        }

    @Test
    fun loadDevicesHandlesNetworkError() =
        runTest {
            val engine = MockEngine { throw RuntimeException("Socket closed") }
            val httpClient = HttpClient(engine) { install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) } }
            val client = ApiClient(baseUrl = "", httpClient = httpClient)
            val vm = DeviceListViewModel(client, scope = this)

            val stateDeferred = async { vm.state.first { !it.isLoading } }
            advanceUntilIdle()
            val state = stateDeferred.await()

            assertFalse(state.isLoading)
            assertEquals("Network error. Failed to reach server.", state.errorMessage)
        }

    @Test
    fun searchAndPaginationStateUpdates() =
        runTest {
            val client = createClient(defaultMockHandler)
            val vm = DeviceListViewModel(client, scope = this)
            val loadDeferred = async { vm.state.first { !it.isLoading } }
            advanceUntilIdle()
            loadDeferred.await()

            vm.onSearchQueryChanged("router")
            assertEquals("router", vm.state.value.searchQuery)

            vm.dismissToast()
            assertNull(vm.state.value.toastMessage)
        }

    @Test
    fun deleteDeviceIgnoresReentrantCallsWhenDeleting() =
        runTest {
            val client = createClient(defaultMockHandler)
            val vm = DeviceListViewModel(client, scope = this)
            val loadDeferred = async { vm.state.first { !it.isLoading } }
            advanceUntilIdle()
            loadDeferred.await()

            val device = DeviceDto(id = "d1", hostname = "router-01", deviceType = "router", status = "active")
            vm.confirmDeleteDevice(device)

            vm.deleteDevice()
            assertTrue(vm.state.value.isDeleting)

            vm.deleteDevice()
            assertTrue(vm.state.value.isDeleting)

            advanceUntilIdle()
            vm.clear()
        }

    @Test
    fun deleteDeviceWorkflowCompletesSuccessfully() =
        runTest {
            val client = createClient(defaultMockHandler)
            val vm = DeviceListViewModel(client, scope = this)
            val loadDeferred = async { vm.state.first { !it.isLoading } }
            advanceUntilIdle()
            loadDeferred.await()

            val device = DeviceDto(id = "d1", hostname = "router-01", deviceType = "router", status = "active")
            vm.confirmDeleteDevice(device)
            assertEquals(
                "d1",
                vm.state.value.deviceToDelete
                    ?.id,
            )

            val deleteDeferred = async { vm.state.first { !it.isDeleting && it.toastMessage != null } }
            vm.deleteDevice()
            advanceUntilIdle()
            deleteDeferred.await()

            assertNull(vm.state.value.deviceToDelete)
            assertFalse(vm.state.value.isDeleting)
            assertTrue(
                vm.state.value.toastMessage
                    ?.contains("router-01") == true,
            )
        }

    @Test
    fun deleteDeviceHandlesApiError() =
        runTest {
            val client =
                createClient { method, path ->
                    if (method == "DELETE") {
                        HttpStatusCode.BadRequest to
                            """{"error":{"code":"BAD_REQ","message":"Cannot delete active router"},"meta":{"request_id":"r_err"}}"""
                    } else {
                        defaultMockHandler(method, path)
                    }
                }

            val vm = DeviceListViewModel(client, scope = this)
            advanceUntilIdle()

            val device = DeviceDto(id = "d1", hostname = "router-01", deviceType = "router", status = "active")
            vm.confirmDeleteDevice(device)

            val stateDeferred = async { vm.state.first { it.errorMessage != null } }
            vm.deleteDevice()
            advanceUntilIdle()

            val state = stateDeferred.await()
            assertEquals("Cannot delete active router", state.errorMessage)
            assertFalse(state.isDeleting)
        }

    @Test
    fun deleteDeviceHandlesNetworkError() =
        runTest {
            val engine =
                MockEngine { request ->
                    if (request.method.value == "DELETE") {
                        throw RuntimeException("Connection reset")
                    } else {
                        respond(
                            """{"data":{"items":[{"id":"d1","hostname":"r1","device_type":"router","status":"active"}],"total":1},"meta":{"request_id":"r1"}}""",
                            HttpStatusCode.OK,
                            jsonHeaders,
                        )
                    }
                }
            val httpClient = HttpClient(engine) { install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) } }
            val client = ApiClient(baseUrl = "", httpClient = httpClient)

            val vm = DeviceListViewModel(client, scope = this)
            advanceUntilIdle()

            val device = DeviceDto(id = "d1", hostname = "router-01", deviceType = "router", status = "active")
            vm.confirmDeleteDevice(device)

            val stateDeferred = async { vm.state.first { it.errorMessage != null } }
            vm.deleteDevice()
            advanceUntilIdle()

            val state = stateDeferred.await()
            assertEquals("Network error. Failed to delete device.", state.errorMessage)
            assertFalse(state.isDeleting)
        }

    @Test
    fun cancelDeleteDeviceClearsSelection() =
        runTest {
            val client = createClient(defaultMockHandler)
            val vm = DeviceListViewModel(client, scope = this)
            val loadDeferred = async { vm.state.first { !it.isLoading } }
            advanceUntilIdle()
            loadDeferred.await()

            val device = DeviceDto(id = "d1", hostname = "router-01", deviceType = "router", status = "active")
            vm.confirmDeleteDevice(device)
            vm.cancelDeleteDevice()
            assertNull(vm.state.value.deviceToDelete)

            vm.clear()
        }
}
