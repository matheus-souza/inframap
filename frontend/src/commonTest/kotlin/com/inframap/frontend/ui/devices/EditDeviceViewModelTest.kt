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
class EditDeviceViewModelTest {
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
                    """{"data":{"id":"d1","hostname":"router-01","ip_address":"10.0.0.1","device_type":"router","status":"active"},"meta":{"request_id":"r1"}}"""
            method.equals("PUT", ignoreCase = true) && path.endsWith("/devices/d1") ->
                HttpStatusCode.OK to
                    """{"data":{"id":"d1","hostname":"router-01-updated","ip_address":"10.0.0.1","device_type":"router","status":"active"},"meta":{"request_id":"r2"}}"""
            else -> HttpStatusCode.NotFound to """{"error":{"code":"NOT_FOUND","message":"Not found"},"meta":{"request_id":"r_err"}}"""
        }
    }

    @Test
    fun loadDevicePrepopulatesStateSuccessfully() =
        runTest {
            val client = createClient(defaultMockHandler)
            val vm = EditDeviceViewModel("d1", client, scope = this)

            val stateDeferred = async { vm.state.first { !it.isLoading } }
            advanceUntilIdle()
            val state = stateDeferred.await()

            assertFalse(state.isLoading)
            assertEquals("router-01", state.hostname)
            assertEquals("10.0.0.1", state.ipAddress)
            vm.clear()
        }

    @Test
    fun loadDeviceHandlesApiError() =
        runTest {
            val client =
                createClient { _, path ->
                    HttpStatusCode.NotFound to
                        """{"error":{"code":"NOT_FOUND","message":"Device not found"},"meta":{"request_id":"r_err"}}"""
                }

            val vm = EditDeviceViewModel("d999", client, scope = this)
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
            val engine = MockEngine { throw RuntimeException("Network fail") }
            val httpClient = HttpClient(engine) { install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) } }
            val client = ApiClient(baseUrl = "", httpClient = httpClient)

            val vm = EditDeviceViewModel("d1", client, scope = this)
            val stateDeferred = async { vm.state.first { !it.isLoading } }
            advanceUntilIdle()
            val state = stateDeferred.await()

            assertFalse(state.isLoading)
            assertEquals("Network error. Failed to reach server.", state.errorMessage)
            vm.clear()
        }

    @Test
    fun updateDeviceValidatesAndSucceeds() =
        runTest {
            val client = createClient(defaultMockHandler)
            val vm = EditDeviceViewModel("d1", client, scope = this)
            val loadDeferred = async { vm.state.first { !it.isLoading } }
            advanceUntilIdle()
            loadDeferred.await()

            vm.onHostnameChanged("router-01-updated")
            vm.onIpAddressChanged("10.0.0.1")
            vm.onMacAddressChanged("AA:BB:CC:DD:EE:FF")
            vm.onDeviceTypeChanged("router")
            vm.onStatusChanged("active")

            val stateDeferred = async { vm.state.first { it.isSuccess } }
            vm.updateDevice()
            advanceUntilIdle()

            val state = stateDeferred.await()
            assertTrue(state.isSuccess)
            assertFalse(state.isSubmitting)
            assertNull(state.errorMessage)

            vm.clear()
        }

    @Test
    fun updateDeviceHandlesApiError() =
        runTest {
            val client =
                createClient { method, path ->
                    if (method == "PUT") {
                        HttpStatusCode.BadRequest to
                            """{"error":{"code":"BAD_REQ","message":"Duplicate hostname"},"meta":{"request_id":"r_err"}}"""
                    } else {
                        defaultMockHandler(method, path)
                    }
                }

            val vm = EditDeviceViewModel("d1", client, scope = this)
            val loadDeferred = async { vm.state.first { !it.isLoading } }
            advanceUntilIdle()
            loadDeferred.await()

            vm.onHostnameChanged("duplicate-name")
            val stateDeferred = async { vm.state.first { it.errorMessage != null } }
            vm.updateDevice()
            advanceUntilIdle()

            val state = stateDeferred.await()
            assertEquals("Duplicate hostname", state.errorMessage)
            assertFalse(state.isSubmitting)

            vm.clear()
        }

    @Test
    fun updateDeviceHandlesNetworkError() =
        runTest {
            val engine =
                MockEngine { request ->
                    if (request.method.value == "PUT") {
                        throw RuntimeException("Network down")
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

            val vm = EditDeviceViewModel("d1", client, scope = this)
            val loadDeferred = async { vm.state.first { !it.isLoading } }
            advanceUntilIdle()
            loadDeferred.await()

            val stateDeferred = async { vm.state.first { it.errorMessage != null } }
            vm.updateDevice()
            advanceUntilIdle()

            val state = stateDeferred.await()
            assertEquals("Network error. Failed to update device.", state.errorMessage)
            assertFalse(state.isSubmitting)

            vm.clear()
        }

    @Test
    fun updateDeviceFailsValidationWhenHostnameIsEmpty() =
        runTest {
            val client = createClient(defaultMockHandler)
            val vm = EditDeviceViewModel("d1", client, scope = this)
            val loadDeferred = async { vm.state.first { !it.isLoading } }
            advanceUntilIdle()
            loadDeferred.await()

            vm.onHostnameChanged("")
            vm.updateDevice()

            assertTrue(
                vm.state.value.validationErrors
                    .containsKey("hostname"),
            )
            assertFalse(vm.state.value.isSuccess)

            vm.clear()
        }
}
