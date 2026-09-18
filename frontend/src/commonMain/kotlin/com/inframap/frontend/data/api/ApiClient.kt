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
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

class ApiClient(
    @PublishedApi internal val baseUrl: String,
    httpClient: HttpClient? = null,
) {
    var onSessionExpired: (() -> Unit)? = null

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
                val responseText = response.bodyAsText()
                val errorResult = parseErrorResponse(status, responseText)
                val errCode = errorResult.code
                val errMsg = errorResult.message
                println("[InfraMap-API] [WARN] HTTP $status: $errCode - $errMsg")
                if (status == 401 || errCode == "UNAUTHORIZED") {
                    onSessionExpired?.invoke()
                }
                errorResult
            }
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Throwable) {
            println("[InfraMap-API] [ERROR] Network/Serialization error: ${e.message}")
            ApiResult.NetworkError(throwable = e)
        }

    @PublishedApi
    internal fun parseErrorResponse(
        status: Int,
        responseText: String,
    ): ApiResult.Error {
        val json =
            Json {
                ignoreUnknownKeys = true
                isLenient = true
            }
        return try {
            val envelope = json.decodeFromString<ErrorEnvelope>(responseText)
            ApiResult.Error(
                code = envelope.error.code,
                message = envelope.error.message,
                requestId = envelope.meta.requestId,
                httpStatus = status,
                detailsJson = responseText,
            )
        } catch (_: Throwable) {
            parseFallbackError(status, responseText, json)
        }
    }

    @PublishedApi
    internal fun parseFallbackError(
        status: Int,
        responseText: String,
        json: Json,
    ): ApiResult.Error =
        try {
            val element = json.parseToJsonElement(responseText)
            val obj = element as? JsonObject
            val errCode =
                obj?.get("error")?.let {
                    if (it is JsonPrimitive) it.content else null
                } ?: "HTTP_$status"
            val errMsg =
                obj?.get("message")?.let {
                    if (it is JsonPrimitive) it.content else null
                } ?: errCode
            ApiResult.Error(
                code = errCode,
                message = errMsg,
                requestId = "",
                httpStatus = status,
                detailsJson = responseText,
            )
        } catch (_: Throwable) {
            ApiResult.Error(
                code = "HTTP_$status",
                message = responseText,
                requestId = "",
                httpStatus = status,
                detailsJson = responseText,
            )
        }
}
