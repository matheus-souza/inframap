package com.inframap.frontend.ui.splash

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
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertIs
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class SplashViewModelTest {
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
    fun notOnboardedNavigatesToOnboarding() =
        runTest {
            val client =
                createClient { path ->
                    when {
                        path.contains("setup/status") ->
                            HttpStatusCode.OK to
                                """{"data":{"onboarding_completed":false,"system_instance_id":""},"meta":{"request_id":"r1"}}"""
                        else ->
                            HttpStatusCode.NotFound to
                                """{"error":{"code":"NOT_FOUND","message":"Not found"},"meta":{"request_id":"r2"}}"""
                    }
                }

            val viewModel = SplashViewModel(client, this)
            val deferred = async { viewModel.effects.first() }
            viewModel.checkAuthState()
            advanceUntilIdle()

            assertIs<SplashEffect.NavigateToOnboarding>(deferred.await())
        }

    @Test
    fun onboardedAndAuthenticatedNavigatesToDashboard() =
        runTest {
            val client =
                createClient { path ->
                    when {
                        path.contains("setup/status") ->
                            HttpStatusCode.OK to
                                """{"data":{"onboarding_completed":true,"system_instance_id":"inst-1"},"meta":{"request_id":"r1"}}"""
                        path.contains("auth/me") ->
                            HttpStatusCode.OK to
                                """{"data":{"id":"u1","username":"admin","email":"a@b.com","full_name":"Admin","is_active":true,"permissions":["admin"]},"meta":{"request_id":"r2"}}"""
                        else ->
                            HttpStatusCode.NotFound to
                                """{"error":{"code":"NOT_FOUND","message":"Not found"},"meta":{"request_id":"r3"}}"""
                    }
                }

            val viewModel = SplashViewModel(client, this)
            val deferred = async { viewModel.effects.first() }
            viewModel.checkAuthState()
            advanceUntilIdle()

            assertIs<SplashEffect.NavigateToDashboard>(deferred.await())
        }

    @Test
    fun onboardedButUnauthenticatedNavigatesToLogin() =
        runTest {
            val client =
                createClient { path ->
                    when {
                        path.contains("setup/status") ->
                            HttpStatusCode.OK to
                                """{"data":{"onboarding_completed":true,"system_instance_id":"inst-1"},"meta":{"request_id":"r1"}}"""
                        path.contains("auth/me") ->
                            HttpStatusCode.Unauthorized to
                                """{"error":{"code":"UNAUTHENTICATED","message":"Missing or invalid session token"},"meta":{"request_id":"r2"}}"""
                        else ->
                            HttpStatusCode.NotFound to
                                """{"error":{"code":"NOT_FOUND","message":"Not found"},"meta":{"request_id":"r3"}}"""
                    }
                }

            val viewModel = SplashViewModel(client, this)
            val deferred = async { viewModel.effects.first() }
            viewModel.checkAuthState()
            advanceUntilIdle()

            assertIs<SplashEffect.NavigateToLogin>(deferred.await())
        }

    @Test
    fun setupStatusErrorNavigatesToLogin() =
        runTest {
            val client =
                createClient { _ ->
                    HttpStatusCode.InternalServerError to
                        """{"error":{"code":"INTERNAL_ERROR","message":"Server error"},"meta":{"request_id":"r1"}}"""
                }

            val viewModel = SplashViewModel(client, this)
            val deferred = async { viewModel.effects.first() }
            viewModel.checkAuthState()
            advanceUntilIdle()

            assertIs<SplashEffect.NavigateToLogin>(deferred.await())
        }

    @Test
    fun initialStateIsLoading() {
        val client = createClient { _ -> HttpStatusCode.OK to "{}" }
        val scope = TestScope(StandardTestDispatcher())
        val viewModel = SplashViewModel(client, scope)

        assertTrue(viewModel.state.value.isLoading)
    }

    @Test
    fun authMeNetworkErrorNavigatesToLogin() =
        runTest {
            var callCount = 0
            val engine =
                MockEngine { request ->
                    callCount++
                    if (request.url.encodedPath.contains("setup/status")) {
                        respond(
                            """{"data":{"onboarding_completed":true,"system_instance_id":"inst-1"},"meta":{"request_id":"r1"}}""",
                            HttpStatusCode.OK,
                            jsonHeaders,
                        )
                    } else {
                        throw RuntimeException("Network failure")
                    }
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
            val client = ApiClient(baseUrl = "", httpClient = httpClient)

            val viewModel = SplashViewModel(client, this)
            val deferred = async { viewModel.effects.first() }
            viewModel.checkAuthState()
            advanceUntilIdle()

            assertIs<SplashEffect.NavigateToLogin>(deferred.await())
        }
}
