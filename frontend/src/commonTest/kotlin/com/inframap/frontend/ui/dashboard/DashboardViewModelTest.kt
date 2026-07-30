package com.inframap.frontend.ui.dashboard

import com.inframap.frontend.data.api.ApiClient
import com.inframap.frontend.data.dto.DeviceDto
import com.inframap.frontend.data.dto.DeviceListResponse
import com.inframap.frontend.data.dto.DiscoverySourceDto
import com.inframap.frontend.data.dto.StagingDeviceDto
import com.inframap.frontend.data.dto.StagingListResponse
import com.inframap.frontend.data.sse.SSEClient
import com.inframap.frontend.data.sse.SSEEvent
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
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class FakeSSEClient : SSEClient {
    val events = MutableSharedFlow<SSEEvent>()

    override fun connect(url: String): SharedFlow<SSEEvent> = events.asSharedFlow()

    override fun disconnect() {}
}

@OptIn(ExperimentalCoroutinesApi::class)
class DashboardViewModelTest {
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

    private val defaultMockHandler: suspend (String) -> Pair<HttpStatusCode, String> = { path ->
        when {
            path.endsWith("/devices/staging") ->
                HttpStatusCode.OK to
                    """{"data":{"items":[{"id":"s1","hostname":"new-switch","device_type":"switch"}],"total":3,"page":1,"per_page":50},"meta":{"request_id":"r2"}}"""
            path.endsWith("/devices") ->
                HttpStatusCode.OK to
                    """{"data":{"items":[{"id":"d1","hostname":"router-01","device_type":"router","status":"active"}],"total":15,"page":1,"per_page":50},"meta":{"request_id":"r1"}}"""
            path.endsWith("/health") ->
                HttpStatusCode.OK to
                    """{"data":{"status":"ok","version":"v1.2.3"},"meta":{"request_id":"r3"}}"""
            path.endsWith("/sources") ->
                HttpStatusCode.OK to
                    """{"data":{"items":[{"id":"src1","name":"homelab-subnet"}],"total":2},"meta":{"request_id":"r4"}}"""
            else ->
                HttpStatusCode.NotFound to
                    """{"error":{"code":"NOT_FOUND","message":"Not found"},"meta":{"request_id":"r-err"}}"""
        }
    }

    @Test
    fun loadDataPopulatesMetricsSuccessfully() =
        runTest {
            val client = createClient(defaultMockHandler)
            val vm = DashboardViewModel(client, scope = this, autoRefreshIntervalMs = 0L)

            val stateDeferred = async { vm.state.first { !it.isLoading } }
            advanceUntilIdle()
            val state = stateDeferred.await()

            assertFalse(state.isLoading)
            assertNull(state.errorMessage)
            assertEquals(15L, state.totalActiveDevices)
            assertEquals(3L, state.totalStagedDevices)
            assertEquals(true, state.isSystemHealthy)
            assertEquals("v1.2.3", state.systemVersion)
            assertEquals(2L, state.totalDiscoverySources)
        }

    @Test
    fun loadDataHandlesApiError() =
        runTest {
            val client =
                createClient { path ->
                    if (path.endsWith("/devices")) {
                        HttpStatusCode.InternalServerError to
                            """{"error":{"code":"INTERNAL_ERROR","message":"DB connection failed"},"meta":{"request_id":"r1"}}"""
                    } else {
                        defaultMockHandler(path)
                    }
                }

            val vm = DashboardViewModel(client, scope = this, autoRefreshIntervalMs = 0L)
            val stateDeferred = async { vm.state.first { !it.isLoading } }
            advanceUntilIdle()
            val state = stateDeferred.await()

            assertFalse(state.isLoading)
            assertEquals("DB connection failed", state.errorMessage)
        }

    @Test
    fun loadDataHandlesNetworkError() =
        runTest {
            val engine = MockEngine { throw RuntimeException("Network error") }
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
            val client = ApiClient(baseUrl = "", httpClient = httpClient)
            val vm = DashboardViewModel(client, scope = this, autoRefreshIntervalMs = 0L)

            val stateDeferred = async { vm.state.first { !it.isLoading } }
            advanceUntilIdle()
            val state = stateDeferred.await()

            assertFalse(state.isLoading)
            assertEquals("Network error. Failed to reach server.", state.errorMessage)
        }

    @Test
    fun manualRefreshReloadsData() =
        runTest {
            var callCount = 0
            val client =
                createClient { path ->
                    callCount++
                    defaultMockHandler(path)
                }

            val vm = DashboardViewModel(client, scope = this, autoRefreshIntervalMs = 0L)
            val firstStateDeferred = async { vm.state.first { !it.isLoading } }
            advanceUntilIdle()
            firstStateDeferred.await()

            val initialCalls = callCount
            val refreshStateDeferred = async { vm.state.first { !it.isLoading } }
            vm.refresh()
            advanceUntilIdle()
            refreshStateDeferred.await()

            assertTrue(callCount > initialCalls)
            assertFalse(vm.state.value.isLoading)
        }

    @Test
    fun periodicAutoRefreshUpdatesHealth() =
        runTest {
            var isHealthyResponse = true
            val client =
                createClient { path ->
                    if (path.endsWith("/health")) {
                        val status = if (isHealthyResponse) "ok" else "degraded"
                        HttpStatusCode.OK to """{"data":{"status":"$status","version":"v1.2.3"},"meta":{"request_id":"r3"}}"""
                    } else {
                        defaultMockHandler(path)
                    }
                }

            val vm = DashboardViewModel(client, scope = this, autoRefreshIntervalMs = 1000L)
            val firstStateDeferred = async { vm.state.first { !it.isLoading } }
            advanceUntilIdle()
            firstStateDeferred.await()
            assertEquals(true, vm.state.value.isSystemHealthy)

            isHealthyResponse = false
            val unhealthyDeferred = async { vm.state.first { it.isSystemHealthy == false } }
            advanceTimeBy(1001L)
            advanceUntilIdle()

            val state = unhealthyDeferred.await()
            vm.stopAutoRefresh()
            assertEquals(false, state.isSystemHealthy)
        }

    @Test
    fun sseEventsTriggerMetricsReload() =
        runTest {
            val fakeSse = FakeSSEClient()
            var activeDevicesCount = 10L

            val client =
                createClient { path ->
                    when {
                        path.endsWith("/devices/staging") ->
                            HttpStatusCode.OK to
                                """{"data":{"items":[],"total":2},"meta":{"request_id":"r2"}}"""
                        path.endsWith("/devices") ->
                            HttpStatusCode.OK to
                                """{"data":{"items":[],"total":$activeDevicesCount},"meta":{"request_id":"r1"}}"""
                        path.endsWith("/health") ->
                            HttpStatusCode.OK to
                                """{"data":{"status":"ok","version":"v1.0"},"meta":{"request_id":"r3"}}"""
                        path.endsWith("/sources") ->
                            HttpStatusCode.OK to
                                """{"data":{"items":[],"total":1},"meta":{"request_id":"r4"}}"""
                        else -> HttpStatusCode.NotFound to "{}"
                    }
                }

            val vm = DashboardViewModel(client, sseClient = fakeSse, scope = this, autoRefreshIntervalMs = 0L)
            val firstStateDeferred = async { vm.state.first { !it.isLoading } }
            advanceUntilIdle()
            firstStateDeferred.await()

            // Test DeviceCreated
            activeDevicesCount = 11L
            var sseStateDeferred = async { vm.state.first { it.totalActiveDevices == 11L } }
            fakeSse.events.emit(SSEEvent.DeviceCreated(id = "e1", data = "{}"))
            advanceUntilIdle()
            assertEquals(11L, sseStateDeferred.await().totalActiveDevices)

            // Test DeviceUpdated
            activeDevicesCount = 12L
            sseStateDeferred = async { vm.state.first { it.totalActiveDevices == 12L } }
            fakeSse.events.emit(SSEEvent.DeviceUpdated(id = "e2", data = "{}"))
            advanceUntilIdle()
            assertEquals(12L, sseStateDeferred.await().totalActiveDevices)

            // Test TopologyUpdated
            activeDevicesCount = 13L
            sseStateDeferred = async { vm.state.first { it.totalActiveDevices == 13L } }
            fakeSse.events.emit(SSEEvent.TopologyUpdated(id = "e3", data = "{}"))
            advanceUntilIdle()
            assertEquals(13L, sseStateDeferred.await().totalActiveDevices)

            // Test DiscoveryProgress
            activeDevicesCount = 14L
            sseStateDeferred = async { vm.state.first { it.totalActiveDevices == 14L } }
            fakeSse.events.emit(SSEEvent.DiscoveryProgress(id = "e4", data = "{}"))
            advanceUntilIdle()
            assertEquals(14L, sseStateDeferred.await().totalActiveDevices)

            // Test SystemNotification (ignored event)
            fakeSse.events.emit(SSEEvent.SystemNotification(id = "e5", data = "{}"))
            runCurrent()

            vm.stopSseListening()
        }

    @Test
    fun disconnectedSseEventHandlesReconnection() =
        runTest {
            val fakeSse = FakeSSEClient()
            val client = createClient(defaultMockHandler)
            val vm = DashboardViewModel(client, sseClient = fakeSse, scope = this, autoRefreshIntervalMs = 0L)

            val firstStateDeferred = async { vm.state.first { !it.isLoading } }
            runCurrent()
            firstStateDeferred.await()

            fakeSse.events.emit(SSEEvent.Disconnected())
            runCurrent()

            vm.stopSseListening()
        }

    @Test
    fun fetchHealthErrorDoesNotCrashOrMutateHealthyState() =
        runTest {
            var healthFails = false
            val client =
                createClient { path ->
                    if (path.endsWith("/health") && healthFails) {
                        HttpStatusCode.InternalServerError to """{"error":{"message":"Health check failed"}}"""
                    } else {
                        defaultMockHandler(path)
                    }
                }

            val vm = DashboardViewModel(client, scope = this, autoRefreshIntervalMs = 0L)
            val firstStateDeferred = async { vm.state.first { !it.isLoading } }
            advanceUntilIdle()
            firstStateDeferred.await()
            assertEquals(true, vm.state.value.isSystemHealthy)

            healthFails = true
            vm.fetchHealth()
            advanceUntilIdle()
            assertEquals(true, vm.state.value.isSystemHealthy)
        }

    @Test
    fun clearDisposesAllBackgroundJobs() =
        runTest {
            val fakeSse = FakeSSEClient()
            val client = createClient(defaultMockHandler)
            val vm = DashboardViewModel(client, sseClient = fakeSse, scope = this, autoRefreshIntervalMs = 1000L)

            val firstStateDeferred = async { vm.state.first { !it.isLoading } }
            runCurrent()
            firstStateDeferred.await()

            vm.clear()
            advanceUntilIdle()
        }

    @Test
    fun dtoGettersAndDefaultsWorkCorrectly() {
        val device = DeviceDto(id = "d1", hostname = "router-01", deviceType = "router", status = "active")
        val deviceList = DeviceListResponse(items = listOf(device))
        assertEquals(1, deviceList.devices.size)
        assertEquals("d1", deviceList.devices.first().id)

        val stagingDevice = StagingDeviceDto(id = "s1", hostname = "switch-01", deviceType = "switch")
        val stagingList = StagingListResponse(items = listOf(stagingDevice))
        assertEquals(1, stagingList.items.size)

        val source = DiscoverySourceDto(id = "src1", name = "Subnet 1", sourceType = "snmp", enabled = true)
        assertEquals("src1", source.id)
        assertEquals("Subnet 1", source.name)
        assertEquals("snmp", source.sourceType)
        assertTrue(source.enabled)
    }
}
