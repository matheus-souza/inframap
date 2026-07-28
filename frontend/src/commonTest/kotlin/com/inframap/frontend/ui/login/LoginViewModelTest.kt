package com.inframap.frontend.ui.login

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
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class LoginViewModelTest {
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

    private val successLoginResponse =
        """
        {"data":{"token":"ims_abc123","user_id":"u1","username":"admin","email":"a@b.com","full_name":"Admin","permissions":["admin"],"expires_at":"2026-08-01T00:00:00Z"},"meta":{"request_id":"r1"}}
        """.trimIndent()

    @Test
    fun initialStateIsEmpty() {
        val client = createClient { _ -> HttpStatusCode.OK to "{}" }
        val vm = LoginViewModel(client, kotlinx.coroutines.test.TestScope())
        val state = vm.state.value

        assertEquals("", state.username)
        assertEquals("", state.password)
        assertFalse(state.isLoading)
        assertNull(state.errorMessage)
    }

    @Test
    fun usernameChangedUpdatesState() {
        val client = createClient { _ -> HttpStatusCode.OK to "{}" }
        val vm = LoginViewModel(client, kotlinx.coroutines.test.TestScope())

        vm.onUsernameChanged("admin")
        assertEquals("admin", vm.state.value.username)
    }

    @Test
    fun passwordChangedUpdatesState() {
        val client = createClient { _ -> HttpStatusCode.OK to "{}" }
        val vm = LoginViewModel(client, kotlinx.coroutines.test.TestScope())

        vm.onPasswordChanged("secret123")
        assertEquals("secret123", vm.state.value.password)
    }

    @Test
    fun usernameChangeClearsError() {
        val client = createClient { _ -> HttpStatusCode.OK to "{}" }
        val vm = LoginViewModel(client, kotlinx.coroutines.test.TestScope())

        vm.login()
        assertTrue(vm.state.value.errorMessage != null)

        vm.onUsernameChanged("admin")
        assertNull(vm.state.value.errorMessage)
    }

    @Test
    fun passwordChangeClearsError() {
        val client = createClient { _ -> HttpStatusCode.OK to "{}" }
        val vm = LoginViewModel(client, kotlinx.coroutines.test.TestScope())

        vm.login()
        assertTrue(vm.state.value.errorMessage != null)

        vm.onPasswordChanged("secret")
        assertNull(vm.state.value.errorMessage)
    }

    @Test
    fun emptyUsernameShowsValidationError() {
        val client = createClient { _ -> HttpStatusCode.OK to "{}" }
        val vm = LoginViewModel(client, kotlinx.coroutines.test.TestScope())

        vm.onPasswordChanged("secret")
        vm.login()

        assertEquals("Username and password are required", vm.state.value.errorMessage)
        assertFalse(vm.state.value.isLoading)
    }

    @Test
    fun emptyPasswordShowsValidationError() {
        val client = createClient { _ -> HttpStatusCode.OK to "{}" }
        val vm = LoginViewModel(client, kotlinx.coroutines.test.TestScope())

        vm.onUsernameChanged("admin")
        vm.login()

        assertEquals("Username and password are required", vm.state.value.errorMessage)
        assertFalse(vm.state.value.isLoading)
    }

    @Test
    fun successfulLoginNavigatesToDashboard() =
        runTest {
            val client = createClient { _ -> HttpStatusCode.OK to successLoginResponse }
            val vm = LoginViewModel(client, this)

            vm.onUsernameChanged("admin")
            vm.onPasswordChanged("correctpassword")

            val deferred = async { vm.effects.first() }
            vm.login()
            advanceUntilIdle()

            assertIs<LoginEffect.NavigateToDashboard>(deferred.await())
            assertFalse(vm.state.value.isLoading)
        }

    @Test
    fun failedLoginShowsErrorMessage() =
        runTest {
            val client =
                createClient { _ ->
                    HttpStatusCode.Unauthorized to
                        """{"error":{"code":"INVALID_CREDENTIALS","message":"Invalid username or password"},"meta":{"request_id":"r1"}}"""
                }
            val vm = LoginViewModel(client, this)

            vm.onUsernameChanged("admin")
            vm.onPasswordChanged("wrongpassword")

            val deferred = async { vm.state.first { !it.isLoading && it.errorMessage != null } }
            vm.login()
            advanceUntilIdle()

            val result = deferred.await()
            assertEquals("Invalid username or password", result.errorMessage)
            assertFalse(result.isLoading)
        }

    @Test
    fun rateLimitShowsSpecificMessage() =
        runTest {
            val client =
                createClient { _ ->
                    HttpStatusCode.TooManyRequests to
                        """{"error":{"code":"RATE_LIMITED","message":"Rate limit exceeded"},"meta":{"request_id":"r1"}}"""
                }
            val vm = LoginViewModel(client, this)

            vm.onUsernameChanged("admin")
            vm.onPasswordChanged("password123")

            val deferred = async { vm.state.first { !it.isLoading && it.errorMessage != null } }
            vm.login()
            advanceUntilIdle()

            assertEquals("Too many attempts. Please wait before trying again.", deferred.await().errorMessage)
        }

    @Test
    fun networkErrorShowsConnectionMessage() =
        runTest {
            val engine = MockEngine { throw RuntimeException("Connection refused") }
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
            val vm = LoginViewModel(client, this)

            vm.onUsernameChanged("admin")
            vm.onPasswordChanged("password123")

            val deferred = async { vm.state.first { !it.isLoading && it.errorMessage != null } }
            vm.login()
            advanceUntilIdle()

            assertEquals("Network error. Check your connection.", deferred.await().errorMessage)
        }

    @Test
    fun duplicateLoginWhileLoadingIsIgnored() =
        runTest {
            var callCount = 0
            val client =
                createClient { _ ->
                    callCount++
                    HttpStatusCode.OK to successLoginResponse
                }
            val vm = LoginViewModel(client, this)

            vm.onUsernameChanged("admin")
            vm.onPasswordChanged("password123")

            val deferred = async { vm.effects.first() }
            vm.login()
            vm.login()
            advanceUntilIdle()
            deferred.await()

            assertEquals(1, callCount)
        }
}
