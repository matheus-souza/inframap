package com.inframap.frontend.ui.staging

import com.inframap.frontend.data.api.ApiClient
import com.inframap.frontend.data.dto.StagingDeviceDto
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
class StagingViewModelTest {
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
            method.equals("GET", ignoreCase = true) && path.endsWith("/devices/staging") ->
                HttpStatusCode.OK to
                    """{"data":{"items":[{"id":"st1","hostname":"stg-switch-01","device_type":"switch","status":"pending"}],"total":1,"page":1,"per_page":50},"meta":{"request_id":"r1"}}"""
            method.equals("POST", ignoreCase = true) && path.contains("/approve") ->
                HttpStatusCode.OK to
                    """{"data":{"id":"st1","hostname":"stg-switch-01","device_type":"switch","status":"active"},"meta":{"request_id":"r2"}}"""
            method.equals("POST", ignoreCase = true) && path.contains("/dismiss") ->
                HttpStatusCode.OK to
                    """{"data":{"message":"dismissed"},"meta":{"request_id":"r3"}}"""
            else ->
                HttpStatusCode.NotFound to """{"error":{"code":"NOT_FOUND","message":"Not found"},"meta":{"request_id":"r_err"}}"""
        }
    }

    @Test
    fun loadStagingDevicesPopulatesListSuccessfully() =
        runTest {
            val client = createClient(defaultMockHandler)
            val vm = StagingViewModel(client, scope = this)

            val stateDeferred = async { vm.state.first { !it.isLoading } }
            advanceUntilIdle()
            val state = stateDeferred.await()

            assertFalse(state.isLoading)
            assertNull(state.errorMessage)
            assertEquals(1, state.devices.size)
            assertEquals("stg-switch-01", state.devices.first().hostname)
        }

    @Test
    fun loadStagingDevicesHandlesApiError() =
        runTest {
            val client =
                createClient { _, path ->
                    if (path.endsWith("/devices/staging")) {
                        HttpStatusCode.InternalServerError to
                            """{"error":{"code":"DB_ERR","message":"DB Error"},"meta":{"request_id":"r_err"}}"""
                    } else {
                        defaultMockHandler("GET", path)
                    }
                }

            val vm = StagingViewModel(client, scope = this)
            val stateDeferred = async { vm.state.first { !it.isLoading } }
            advanceUntilIdle()
            val state = stateDeferred.await()

            assertFalse(state.isLoading)
            assertEquals("DB Error", state.errorMessage)
        }

    @Test
    fun loadStagingDevicesHandlesNetworkError() =
        runTest {
            val engine = MockEngine { throw RuntimeException("Network issue") }
            val httpClient = HttpClient(engine) { install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) } }
            val client = ApiClient(baseUrl = "", httpClient = httpClient)

            val vm = StagingViewModel(client, scope = this)
            val stateDeferred = async { vm.state.first { !it.isLoading } }
            advanceUntilIdle()
            val state = stateDeferred.await()

            assertFalse(state.isLoading)
            assertEquals("Erro de rede. Não foi possível conectar ao servidor.", state.errorMessage)
        }

    @Test
    fun approveDeviceWorkflowCompletesSuccessfully() =
        runTest {
            val client = createClient(defaultMockHandler)
            val vm = StagingViewModel(client, scope = this)
            advanceUntilIdle()

            val device = StagingDeviceDto(id = "st1", hostname = "stg-switch-01", deviceType = "switch", status = "pending")
            val stateDeferred = async { vm.state.first { !it.isProcessingAction && it.toastMessage != null } }
            vm.approveDevice(device)
            advanceUntilIdle()

            val state = stateDeferred.await()
            assertFalse(state.isProcessingAction)
            assertTrue(state.toastMessage?.contains("stg-switch-01") == true)
        }

    @Test
    fun approveDeviceIgnoresReentrantCallsWhenProcessing() =
        runTest {
            val client = createClient(defaultMockHandler)
            val vm = StagingViewModel(client, scope = this)
            advanceUntilIdle()

            val device = StagingDeviceDto(id = "st1", hostname = "stg-switch-01", deviceType = "switch", status = "pending")
            vm.approveDevice(device)
            assertTrue(vm.state.value.isProcessingAction)

            vm.approveDevice(device)
            assertTrue(vm.state.value.isProcessingAction)

            advanceUntilIdle()
            vm.clear()
        }

    @Test
    fun dismissDeviceWorkflowCompletesSuccessfully() =
        runTest {
            val client = createClient(defaultMockHandler)
            val vm = StagingViewModel(client, scope = this)
            advanceUntilIdle()

            val device = StagingDeviceDto(id = "st1", hostname = "stg-switch-01", deviceType = "switch", status = "pending")
            vm.confirmDismissDevice(device)
            assertEquals(
                "st1",
                vm.state.value.deviceToDismiss
                    ?.id,
            )

            val stateDeferred = async { vm.state.first { !it.isProcessingAction && it.toastMessage != null } }
            vm.dismissDevice()
            advanceUntilIdle()

            val state = stateDeferred.await()
            assertNull(state.deviceToDismiss)
            assertFalse(state.isProcessingAction)
            assertTrue(state.toastMessage?.contains("stg-switch-01") == true)

            vm.dismissToast()
            assertNull(vm.state.value.toastMessage)
        }

    @Test
    fun dismissDeviceHandlesApiError() =
        runTest {
            val client =
                createClient { method, path ->
                    if (method == "POST" && path.contains("/dismiss")) {
                        HttpStatusCode.BadRequest to
                            """{"error":{"code":"BAD_REQ","message":"Cannot dismiss device"},"meta":{"request_id":"r_err"}}"""
                    } else {
                        defaultMockHandler(method, path)
                    }
                }

            val vm = StagingViewModel(client, scope = this)
            advanceUntilIdle()

            val device = StagingDeviceDto(id = "st1", hostname = "stg-switch-01", deviceType = "switch", status = "pending")
            vm.confirmDismissDevice(device)

            val stateDeferred = async { vm.state.first { it.actionErrorMessage != null } }
            vm.dismissDevice()
            advanceUntilIdle()

            val state = stateDeferred.await()
            assertEquals("Cannot dismiss device", state.actionErrorMessage)
            assertFalse(state.isProcessingAction)

            vm.dismissActionError()
            assertNull(vm.state.value.actionErrorMessage)

            vm.cancelDismissDevice()
            assertNull(vm.state.value.deviceToDismiss)
        }
}
