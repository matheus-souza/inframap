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
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class CreateSubnetViewModelTest {
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
            method.equals("POST", ignoreCase = true) && path.endsWith("/subnets") ->
                HttpStatusCode.Created to
                    """{"data":{"id":"sub1","name":"Management","cidr":"192.168.1.0/24","vlan_id":10,"gateway_ip":"192.168.1.1","discovery_enabled":true},"meta":{"request_id":"r1"}}"""
            else ->
                HttpStatusCode.NotFound to """{"error":{"code":"NOT_FOUND","message":"Not found"},"meta":{"request_id":"r_err"}}"""
        }
    }

    @Test
    fun validationFailsOnEmptyFields() =
        runTest {
            val client = createClient(defaultMockHandler)
            val vm = CreateSubnetViewModel(client, scope = this)

            assertFalse(vm.validate())
            val errors = vm.state.value.validationErrors
            assertTrue(errors.containsKey("name"))
            assertTrue(errors.containsKey("cidr"))
        }

    @Test
    fun validationFailsOnInvalidCidrAndVlan() =
        runTest {
            val client = createClient(defaultMockHandler)
            val vm = CreateSubnetViewModel(client, scope = this)

            vm.onNameChanged("Servers")
            vm.onCidrChanged("invalid-cidr")
            vm.onVlanIdChanged("99999")
            vm.onGatewayIpChanged("bad-ip")

            assertFalse(vm.validate())
            val errors = vm.state.value.validationErrors
            assertTrue(errors.containsKey("cidr"))
            assertTrue(errors.containsKey("vlan_id"))
            assertTrue(errors.containsKey("gateway_ip"))
        }

    @Test
    fun createSubnetWorkflowCompletesSuccessfully() =
        runTest {
            var onSuccessCalled = false
            val client = createClient(defaultMockHandler)
            val vm = CreateSubnetViewModel(client, scope = this)

            vm.onNameChanged("Management")
            vm.onCidrChanged("192.168.1.0/24")
            vm.onVlanIdChanged("10")
            vm.onGatewayIpChanged("192.168.1.1")
            vm.onDescriptionChanged("Core mgmt subnet")
            vm.onDiscoveryEnabledChanged(true)

            val stateDeferred = async { vm.state.first { !it.isSubmitting && it.isSuccess } }
            vm.createSubnet { onSuccessCalled = true }
            advanceUntilIdle()

            val state = stateDeferred.await()
            assertTrue(state.isSuccess)
            assertFalse(state.isSubmitting)
            assertNull(state.errorMessage)
            assertTrue(onSuccessCalled)
        }

    @Test
    fun createSubnetIgnoresReentrantCallsWhenSubmitting() =
        runTest {
            val client = createClient(defaultMockHandler)
            val vm = CreateSubnetViewModel(client, scope = this)
            vm.onNameChanged("Management")
            vm.onCidrChanged("192.168.1.0/24")

            vm.createSubnet()
            assertTrue(vm.state.value.isSubmitting)

            vm.createSubnet()
            assertTrue(vm.state.value.isSubmitting)

            advanceUntilIdle()
            vm.clear()
        }

    @Test
    fun createSubnetHandlesApiError() =
        runTest {
            val client =
                createClient { method, path ->
                    if (method == "POST" && path.endsWith("/subnets")) {
                        HttpStatusCode.Conflict to
                            """{"error":{"code":"CONFLICT","message":"Subnet CIDR already registered"},"meta":{"request_id":"r_err"}}"""
                    } else {
                        defaultMockHandler(method, path)
                    }
                }

            val vm = CreateSubnetViewModel(client, scope = this)
            vm.onNameChanged("Management")
            vm.onCidrChanged("192.168.1.0/24")

            val stateDeferred = async { vm.state.first { !it.isSubmitting && it.errorMessage != null } }
            vm.createSubnet()
            advanceUntilIdle()

            val state = stateDeferred.await()
            assertFalse(state.isSubmitting)
            assertFalse(state.isSuccess)
            assertEquals("Subnet CIDR already registered", state.errorMessage)
        }
}
