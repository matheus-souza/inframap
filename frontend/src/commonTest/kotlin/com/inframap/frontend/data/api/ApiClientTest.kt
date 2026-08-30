package com.inframap.frontend.data.api

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class ApiClientTest {
    private fun mockClient(
        status: HttpStatusCode = HttpStatusCode.OK,
        responseBody: String,
    ): HttpClient =
        HttpClient(MockEngine) {
            engine {
                addHandler {
                    respond(
                        content = responseBody,
                        status = status,
                        headers =
                            headersOf(
                                HttpHeaders.ContentType,
                                ContentType.Application.Json.toString(),
                            ),
                    )
                }
            }
            install(ContentNegotiation) {
                json(
                    Json {
                        ignoreUnknownKeys = true
                        isLenient = true
                    },
                )
            }
        }

    @Test
    fun getReturnsSuccessOnValidResponse() =
        runTest {
            val json =
                """
                {"data":{"status":"ok","version":"0.1.0"},"meta":{"request_id":"req-123"}}
                """.trimIndent()
            val client = ApiClient("http://localhost", mockClient(responseBody = json))

            val result = client.get<Map<String, String>>("/api/v1/health")

            assertIs<ApiResult.Success<Map<String, String>>>(result)
            assertEquals("ok", result.data["status"])
            assertEquals("req-123", result.requestId)
        }

    @Test
    fun getReturnsErrorOnApiError() =
        runTest {
            val json =
                """
                {"error":{"code":"NOT_FOUND","message":"Device not found","details":null},"meta":{"request_id":"req-456"}}
                """.trimIndent()
            val client =
                ApiClient(
                    "http://localhost",
                    mockClient(status = HttpStatusCode.NotFound, responseBody = json),
                )

            val result = client.get<Map<String, String>>("/api/v1/devices/123")

            assertIs<ApiResult.Error>(result)
            assertEquals("NOT_FOUND", result.code)
            assertEquals("Device not found", result.message)
            assertEquals(404, result.httpStatus)
            assertEquals("req-456", result.requestId)
        }

    @Test
    fun getWithParamsPassesQueryParameters() =
        runTest {
            val json =
                """
                {"data":{"devices":[],"total":0},"meta":{"request_id":"req-params"}}
                """.trimIndent()
            var capturedUrl = ""
            val mockEngine =
                HttpClient(MockEngine) {
                    engine {
                        addHandler { request ->
                            capturedUrl = request.url.toString()
                            respond(
                                content = json,
                                status = HttpStatusCode.OK,
                                headers =
                                    headersOf(
                                        HttpHeaders.ContentType,
                                        ContentType.Application.Json.toString(),
                                    ),
                            )
                        }
                    }
                    install(ContentNegotiation) {
                        json(
                            Json {
                                ignoreUnknownKeys = true
                                isLenient = true
                            },
                        )
                    }
                }
            val client = ApiClient("http://localhost", mockEngine)

            client.get<Map<String, Any>>(
                "/api/v1/devices",
                mapOf("page" to "1", "per_page" to "50"),
            )

            assertTrue(capturedUrl.contains("page=1"))
            assertTrue(capturedUrl.contains("per_page=50"))
        }

    @Test
    fun apiResultSuccessPropertiesAreAccessible() {
        val result = ApiResult.Success(data = "hello", requestId = "req-1")
        assertEquals("hello", result.data)
        assertEquals("req-1", result.requestId)
    }

    @Test
    fun apiResultErrorPropertiesAreAccessible() {
        val result = ApiResult.Error(code = "ERR", message = "msg", requestId = "req-2", httpStatus = 500)
        assertEquals("ERR", result.code)
        assertEquals("msg", result.message)
        assertEquals("req-2", result.requestId)
        assertEquals(500, result.httpStatus)
    }

    @Test
    fun apiResultNetworkErrorPropertiesAreAccessible() {
        val ex = RuntimeException("fail")
        val result = ApiResult.NetworkError(throwable = ex)
        assertEquals(ex, result.throwable)
    }

    @Test
    fun getReturnsErrorOn401Unauthorized() =
        runTest {
            val json =
                """
                {"error":{"code":"UNAUTHORIZED","message":"Invalid session"},"meta":{"request_id":"req-789"}}
                """.trimIndent()
            val client =
                ApiClient(
                    "http://localhost",
                    mockClient(status = HttpStatusCode.Unauthorized, responseBody = json),
                )

            val result = client.get<Map<String, String>>("/api/v1/auth/me")

            assertIs<ApiResult.Error>(result)
            assertEquals(401, result.httpStatus)
            assertEquals("UNAUTHORIZED", result.code)
        }

    @Test
    fun networkErrorReturnedOnException() =
        runTest {
            val failingClient =
                HttpClient(MockEngine) {
                    engine {
                        addHandler {
                            throw RuntimeException("connection refused")
                        }
                    }
                    install(ContentNegotiation) {
                        json(
                            Json {
                                ignoreUnknownKeys = true
                                isLenient = true
                            },
                        )
                    }
                }
            val client = ApiClient("http://localhost", failingClient)

            val result = client.get<Map<String, String>>("/api/v1/health")

            assertIs<ApiResult.NetworkError>(result)
            assertEquals("connection refused", result.throwable.message)
        }

    @Test
    fun postSendsBodyAndReturnsSuccess() =
        runTest {
            val json =
                """
                {"data":{"id":"user-1","username":"admin"},"meta":{"request_id":"req-post"}}
                """.trimIndent()
            val client = ApiClient("http://localhost", mockClient(responseBody = json))

            val body = mapOf("username" to "admin", "password" to "secret")
            val result = client.post<Map<String, String>, Map<String, String>>("/api/v1/auth/login", body)

            assertIs<ApiResult.Success<Map<String, String>>>(result)
            assertEquals("admin", result.data["username"])
            assertEquals("req-post", result.requestId)
        }

    @Test
    fun putSendsBodyAndReturnsSuccess() =
        runTest {
            val json =
                """
                {"data":{"id":"dev-1","hostname":"updated-host"},"meta":{"request_id":"req-put"}}
                """.trimIndent()
            val client = ApiClient("http://localhost", mockClient(responseBody = json))

            val body = mapOf("hostname" to "updated-host")
            val result =
                client.put<Map<String, String>, Map<String, String>>(
                    "/api/v1/devices/dev-1",
                    body,
                )

            assertIs<ApiResult.Success<Map<String, String>>>(result)
            assertEquals("updated-host", result.data["hostname"])
            assertEquals("req-put", result.requestId)
        }

    @Test
    fun deleteReturnsSuccessOnValidResponse() =
        runTest {
            val json =
                """
                {"data":{"deleted":true},"meta":{"request_id":"req-del"}}
                """.trimIndent()
            val client = ApiClient("http://localhost", mockClient(responseBody = json))

            val result = client.delete<Map<String, Boolean>>("/api/v1/devices/123")

            assertIs<ApiResult.Success<Map<String, Boolean>>>(result)
        }

    @Test
    fun postWithoutBodyReturnsSuccess() =
        runTest {
            var capturedMethod = ""
            var capturedPath = ""
            val json =
                """
                {"data":{"message":"ok"},"meta":{"request_id":"req-post-nobody"}}
                """.trimIndent()
            val mockEngine =
                HttpClient(MockEngine) {
                    engine {
                        addHandler { request ->
                            capturedMethod = request.method.value
                            capturedPath = request.url.encodedPath
                            respond(
                                content = json,
                                status = HttpStatusCode.OK,
                                headers =
                                    headersOf(
                                        HttpHeaders.ContentType,
                                        ContentType.Application.Json.toString(),
                                    ),
                            )
                        }
                    }
                    install(ContentNegotiation) {
                        json(
                            Json {
                                ignoreUnknownKeys = true
                                isLenient = true
                            },
                        )
                    }
                }
            val client = ApiClient("http://localhost", mockEngine)

            val result = client.post<Map<String, String>>("/api/v1/devices/staging/s1/approve")

            assertIs<ApiResult.Success<Map<String, String>>>(result)
            assertEquals("POST", capturedMethod)
            assertEquals("/api/v1/devices/staging/s1/approve", capturedPath)
            assertEquals("ok", result.data["message"])
            assertEquals("req-post-nobody", result.requestId)
        }

    @Test
    fun unauthorized401TriggersOnSessionExpiredCallback() =
        runTest {
            val json =
                """
                {"error":{"code":"UNAUTHORIZED","message":"Invalid or expired session token","details":null},"meta":{"request_id":"req-401"}}
                """.trimIndent()
            val client =
                ApiClient(
                    "http://localhost",
                    mockClient(status = HttpStatusCode.Unauthorized, responseBody = json),
                )
            var sessionExpiredCalled = false
            client.onSessionExpired = { sessionExpiredCalled = true }

            val result = client.get<Map<String, String>>("/api/v1/devices")

            assertIs<ApiResult.Error>(result)
            assertEquals(401, result.httpStatus)
            assertEquals("UNAUTHORIZED", result.code)
            assertTrue(sessionExpiredCalled)
        }

    @Test
    fun non401ErrorDoesNotTriggerOnSessionExpiredCallback() =
        runTest {
            val json =
                """
                {"error":{"code":"NOT_FOUND","message":"Not found","details":null},"meta":{"request_id":"req-404"}}
                """.trimIndent()
            val client =
                ApiClient(
                    "http://localhost",
                    mockClient(status = HttpStatusCode.NotFound, responseBody = json),
                )
            var sessionExpiredCalled = false
            client.onSessionExpired = { sessionExpiredCalled = true }

            val result = client.get<Map<String, String>>("/api/v1/devices/999")

            assertIs<ApiResult.Error>(result)
            assertEquals(404, result.httpStatus)
            assertEquals(false, sessionExpiredCalled)
        }
}
