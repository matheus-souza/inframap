package com.inframap.frontend.data.api

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json

class ApiClient(
    @PublishedApi internal val baseUrl: String,
    httpClient: HttpClient? = null,
) {
    @PublishedApi internal val client: HttpClient =
        httpClient ?: HttpClient {
            install(ContentNegotiation) {
                json(
                    Json {
                        ignoreUnknownKeys = true
                        isLenient = true
                    },
                )
            }
        }

    suspend inline fun <reified T> get(
        path: String,
        params: Map<String, String> = emptyMap(),
    ): ApiResult<T> =
        safeCall {
            client.get("$baseUrl$path") {
                params.forEach { (key, value) -> parameter(key, value) }
            }
        }

    suspend inline fun <reified T, reified B> post(
        path: String,
        body: B,
    ): ApiResult<T> =
        safeCall {
            client.post("$baseUrl$path") {
                contentType(ContentType.Application.Json)
                setBody(body)
            }
        }

    suspend inline fun <reified T> post(path: String): ApiResult<T> =
        safeCall {
            client.post("$baseUrl$path")
        }

    suspend inline fun <reified T, reified B> put(
        path: String,
        body: B,
    ): ApiResult<T> =
        safeCall {
            client.put("$baseUrl$path") {
                contentType(ContentType.Application.Json)
                setBody(body)
            }
        }

    suspend inline fun <reified T> delete(path: String): ApiResult<T> =
        safeCall {
            client.delete("$baseUrl$path")
        }

    @Suppress("TooGenericExceptionCaught")
    suspend inline fun <reified T> safeCall(block: () -> HttpResponse): ApiResult<T> =
        try {
            val response = block()
            val status = response.status.value
            if (status in 200..299) {
                val envelope: SuccessEnvelope<T> = response.body()
                ApiResult.Success(
                    data = envelope.data,
                    requestId = envelope.meta.requestId,
                )
            } else {
                val envelope: ErrorEnvelope = response.body()
                val errCode = envelope.error.code
                val errMsg = envelope.error.message
                println("[InfraMap-API] [WARN] HTTP $status: $errCode - $errMsg")
                ApiResult.Error(
                    code = envelope.error.code,
                    message = envelope.error.message,
                    requestId = envelope.meta.requestId,
                    httpStatus = status,
                )
            }
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Throwable) {
            println("[InfraMap-API] [ERROR] Network/Serialization error: ${e.message}")
            ApiResult.NetworkError(throwable = e)
        }
}
