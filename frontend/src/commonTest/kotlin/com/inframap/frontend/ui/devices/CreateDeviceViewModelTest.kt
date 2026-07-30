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
class CreateDeviceViewModelTest {
    private val jsonHeaders =
        headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString())

    private fun createClient(handler: suspend (String) -> Pair<HttpStatusCode, String>): ApiClient {
        val engine =
            MockEngine { request ->
                val (status, body) = handler(request.url.encodedPath)
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

    @Test
    fun validationFailsWhenHostnameIsEmpty() =
        runTest {
            val client = createClient { HttpStatusCode.OK to "{}" }
            val vm = CreateDeviceViewModel(client, scope = this)

            vm.onHostnameChanged("")
            vm.onDeviceTypeChanged("")
            vm.createDevice()

            assertTrue(
                vm.state.value.validationErrors
                    .containsKey("hostname"),
            )
            assertTrue(
                vm.state.value.validationErrors
                    .containsKey("device_type"),
            )
            assertNull(vm.state.value.createdDeviceId)
        }

    @Test
    fun fieldChangeListenersClearValidationErrors() =
        runTest {
            val client = createClient { HttpStatusCode.OK to "{}" }
            val vm = CreateDeviceViewModel(client, scope = this)

            vm.onHostnameChanged("")
            vm.createDevice()
            assertTrue(
                vm.state.value.validationErrors
                    .containsKey("hostname"),
            )

            vm.onHostnameChanged("router-01")
            vm.onIpAddressChanged("192.168.1.1")
            vm.onMacAddressChanged("00:11:22:33:44:55")
            vm.onDeviceTypeChanged("router")

            assertEquals("router-01", vm.state.value.hostname)
            assertEquals("192.168.1.1", vm.state.value.ipAddress)
            assertEquals("00:11:22:33:44:55", vm.state.value.macAddress)
            assertEquals("router", vm.state.value.deviceType)
            assertFalse(
                vm.state.value.validationErrors
                    .containsKey("hostname"),
            )
        }

    @Test
    fun createDeviceSucceedsWithValidPayload() =
        runTest {
            val client =
                createClient { path ->
                    if (path.endsWith("/devices")) {
                        HttpStatusCode.Created to
                            """{"data":{"id":"d100","hostname":"switch-core","device_type":"switch","status":"active"},"meta":{"request_id":"r1"}}"""
                    } else {
                        HttpStatusCode.NotFound to """{"error":{"code":"NOT_FOUND","message":"Not found"},"meta":{"request_id":"r_err"}}"""
                    }
                }

            val vm = CreateDeviceViewModel(client, scope = this)
            vm.onHostnameChanged("switch-core")
            vm.onIpAddressChanged("192.168.1.50")
            vm.onMacAddressChanged("00:11:22:33:44:55")
            vm.onDeviceTypeChanged("switch")

            val stateDeferred = async { vm.state.first { it.createdDeviceId != null } }
            vm.createDevice()
            advanceUntilIdle()

            val state = stateDeferred.await()
            assertEquals("d100", state.createdDeviceId)
            assertFalse(state.isSubmitting)
            assertNull(state.errorMessage)

            vm.clear()
        }

    @Test
    fun createDeviceHandlesApiError() =
        runTest {
            val client =
                createClient { _ ->
                    HttpStatusCode.BadRequest to
                        """{"error":{"code":"BAD_REQUEST","message":"Invalid IP format"},"meta":{"request_id":"r_err"}}"""
                }

            val vm = CreateDeviceViewModel(client, scope = this)
            vm.onHostnameChanged("invalid-dev")
            vm.onDeviceTypeChanged("router")

            val stateDeferred = async { vm.state.first { it.errorMessage != null } }
            vm.createDevice()
            advanceUntilIdle()

            val state = stateDeferred.await()
            assertEquals("Invalid IP format", state.errorMessage)
            assertFalse(state.isSubmitting)
        }

    @Test
    fun createDeviceHandlesNetworkError() =
        runTest {
            val engine = MockEngine { throw RuntimeException("Network crash") }
            val httpClient = HttpClient(engine) { install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) } }
            val client = ApiClient(baseUrl = "", httpClient = httpClient)

            val vm = CreateDeviceViewModel(client, scope = this)
            vm.onHostnameChanged("router-net")
            vm.onDeviceTypeChanged("router")

            val stateDeferred = async { vm.state.first { it.errorMessage != null } }
            vm.createDevice()
            advanceUntilIdle()

            val state = stateDeferred.await()
            assertEquals("Network error. Failed to create device.", state.errorMessage)
            assertFalse(state.isSubmitting)
        }
}
