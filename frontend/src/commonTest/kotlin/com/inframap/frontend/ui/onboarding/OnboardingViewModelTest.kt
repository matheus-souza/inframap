package com.inframap.frontend.ui.onboarding

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
class OnboardingViewModelTest {
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

    private val successOnboardResponse =
        """
        {"data":{"onboarding_completed":true,"system_instance_id":"inst-1","admin_user_id":"u1"},"meta":{"request_id":"r1"}}
        """.trimIndent()

    private fun fillValidFields(vm: OnboardingViewModel) {
        vm.onUsernameChanged("admin")
        vm.onEmailChanged("admin@inframap.local")
        vm.onFullNameChanged("Admin User")
        vm.onPasswordChanged("securepass123!")
        vm.onConfirmPasswordChanged("securepass123!")
    }

    @Test
    fun initialStateIsEmpty() {
        val client = createClient { _ -> HttpStatusCode.OK to "{}" }
        val vm = OnboardingViewModel(client, kotlinx.coroutines.test.TestScope())
        val state = vm.state.value

        assertEquals("", state.username)
        assertEquals("", state.email)
        assertEquals("", state.fullName)
        assertEquals("", state.password)
        assertEquals("", state.confirmPassword)
        assertFalse(state.isLoading)
        assertNull(state.errorMessage)
    }

    @Test
    fun fieldChangesUpdateState() {
        val client = createClient { _ -> HttpStatusCode.OK to "{}" }
        val vm = OnboardingViewModel(client, kotlinx.coroutines.test.TestScope())

        vm.onUsernameChanged("admin")
        vm.onEmailChanged("a@b.com")
        vm.onFullNameChanged("Admin")
        vm.onPasswordChanged("pass")
        vm.onConfirmPasswordChanged("pass")

        val state = vm.state.value
        assertEquals("admin", state.username)
        assertEquals("a@b.com", state.email)
        assertEquals("Admin", state.fullName)
        assertEquals("pass", state.password)
        assertEquals("pass", state.confirmPassword)
    }

    @Test
    fun fieldChangeClearsError() {
        val client = createClient { _ -> HttpStatusCode.OK to "{}" }
        val vm = OnboardingViewModel(client, kotlinx.coroutines.test.TestScope())

        vm.onboard()
        assertTrue(vm.state.value.errorMessage != null)

        vm.onUsernameChanged("admin")
        assertNull(vm.state.value.errorMessage)
    }

    @Test
    fun validateBlankUsername() {
        val vm =
            OnboardingViewModel(
                createClient { _ -> HttpStatusCode.OK to "{}" },
                kotlinx.coroutines.test.TestScope(),
            )
        assertEquals(
            "Username is required",
            vm.validate(OnboardingUiState(email = "a@b.com", fullName = "N", password = "123456789012", confirmPassword = "123456789012")),
        )
    }

    @Test
    fun validateShortUsername() {
        val vm =
            OnboardingViewModel(
                createClient { _ -> HttpStatusCode.OK to "{}" },
                kotlinx.coroutines.test.TestScope(),
            )
        assertEquals(
            "Username must be at least 3 characters",
            vm.validate(
                OnboardingUiState(
                    username = "ab",
                    email = "a@b.com",
                    fullName = "N",
                    password = "123456789012",
                    confirmPassword = "123456789012",
                ),
            ),
        )
    }

    @Test
    fun validateBlankEmail() {
        val vm =
            OnboardingViewModel(
                createClient { _ -> HttpStatusCode.OK to "{}" },
                kotlinx.coroutines.test.TestScope(),
            )
        assertEquals(
            "Email is required",
            vm.validate(OnboardingUiState(username = "admin", fullName = "N", password = "123456789012", confirmPassword = "123456789012")),
        )
    }

    @Test
    fun validateInvalidEmail() {
        val vm =
            OnboardingViewModel(
                createClient { _ -> HttpStatusCode.OK to "{}" },
                kotlinx.coroutines.test.TestScope(),
            )
        assertEquals(
            "Invalid email address",
            vm.validate(
                OnboardingUiState(
                    username = "admin",
                    email = "notanemail",
                    fullName = "N",
                    password = "123456789012",
                    confirmPassword = "123456789012",
                ),
            ),
        )
    }

    @Test
    fun validateBlankFullName() {
        val vm =
            OnboardingViewModel(
                createClient { _ -> HttpStatusCode.OK to "{}" },
                kotlinx.coroutines.test.TestScope(),
            )
        assertEquals(
            "Full name is required",
            vm.validate(
                OnboardingUiState(username = "admin", email = "a@b.com", password = "123456789012", confirmPassword = "123456789012"),
            ),
        )
    }

    @Test
    fun validateBlankPassword() {
        val vm =
            OnboardingViewModel(
                createClient { _ -> HttpStatusCode.OK to "{}" },
                kotlinx.coroutines.test.TestScope(),
            )
        assertEquals(
            "Password is required",
            vm.validate(OnboardingUiState(username = "admin", email = "a@b.com", fullName = "N")),
        )
    }

    @Test
    fun validateShortPassword() {
        val vm =
            OnboardingViewModel(
                createClient { _ -> HttpStatusCode.OK to "{}" },
                kotlinx.coroutines.test.TestScope(),
            )
        assertEquals(
            "Password must be at least 12 characters",
            vm.validate(
                OnboardingUiState(username = "admin", email = "a@b.com", fullName = "N", password = "short", confirmPassword = "short"),
            ),
        )
    }

    @Test
    fun validatePasswordMismatch() {
        val vm =
            OnboardingViewModel(
                createClient { _ -> HttpStatusCode.OK to "{}" },
                kotlinx.coroutines.test.TestScope(),
            )
        assertEquals(
            "Passwords do not match",
            vm.validate(
                OnboardingUiState(
                    username = "admin",
                    email = "a@b.com",
                    fullName = "N",
                    password = "123456789012",
                    confirmPassword = "different1234",
                ),
            ),
        )
    }

    @Test
    fun validateValidStateReturnsNull() {
        val vm =
            OnboardingViewModel(
                createClient { _ -> HttpStatusCode.OK to "{}" },
                kotlinx.coroutines.test.TestScope(),
            )
        assertNull(
            vm.validate(
                OnboardingUiState(
                    username = "admin",
                    email = "a@b.com",
                    fullName = "N",
                    password = "123456789012",
                    confirmPassword = "123456789012",
                ),
            ),
        )
    }

    @Test
    fun successfulOnboardNavigatesToLogin() =
        runTest {
            val client = createClient { _ -> HttpStatusCode.OK to successOnboardResponse }
            val vm = OnboardingViewModel(client, this)
            fillValidFields(vm)

            val deferred = async { vm.effects.first() }
            vm.onboard()
            advanceUntilIdle()

            assertIs<OnboardingEffect.NavigateToLogin>(deferred.await())
            assertFalse(vm.state.value.isLoading)
        }

    @Test
    fun failedOnboardShowsErrorMessage() =
        runTest {
            val client =
                createClient { _ ->
                    HttpStatusCode.Conflict to
                        """{"error":{"code":"ALREADY_ONBOARDED","message":"System is already onboarded"},"meta":{"request_id":"r1"}}"""
                }
            val vm = OnboardingViewModel(client, this)
            fillValidFields(vm)

            val deferred = async { vm.state.first { !it.isLoading && it.errorMessage != null } }
            vm.onboard()
            advanceUntilIdle()

            assertEquals("System is already onboarded", deferred.await().errorMessage)
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
            val vm = OnboardingViewModel(client, this)
            fillValidFields(vm)

            val deferred = async { vm.state.first { !it.isLoading && it.errorMessage != null } }
            vm.onboard()
            advanceUntilIdle()

            assertEquals("Network error. Check your connection.", deferred.await().errorMessage)
        }

    @Test
    fun validationErrorPreventsApiCall() =
        runTest {
            var callCount = 0
            val client =
                createClient { _ ->
                    callCount++
                    HttpStatusCode.OK to successOnboardResponse
                }
            val vm = OnboardingViewModel(client, this)
            vm.onboard()
            advanceUntilIdle()

            assertEquals(0, callCount)
            assertEquals("Username is required", vm.state.value.errorMessage)
        }

    @Test
    fun duplicateOnboardWhileLoadingIsIgnored() =
        runTest {
            var callCount = 0
            val client =
                createClient { _ ->
                    callCount++
                    HttpStatusCode.OK to successOnboardResponse
                }
            val vm = OnboardingViewModel(client, this)
            fillValidFields(vm)

            val deferred = async { vm.effects.first() }
            vm.onboard()
            vm.onboard()
            advanceUntilIdle()
            deferred.await()

            assertEquals(1, callCount)
        }
}
