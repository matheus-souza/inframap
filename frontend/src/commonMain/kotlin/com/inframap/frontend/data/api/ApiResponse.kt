package com.inframap.frontend.data.api

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class SuccessEnvelope<T>(
    val data: T,
    val meta: Meta,
)

@Serializable
data class ErrorEnvelope(
    val error: ErrorBody,
    val meta: Meta,
)

@Serializable
data class Meta(
    @SerialName("request_id") val requestId: String = "",
)

@Serializable
data class ErrorBody(
    val code: String,
    val message: String,
    val details: List<FieldError>? = null,
)

@Serializable
data class FieldError(
    val field: String,
    val issue: String,
)

sealed class ApiResult<out T> {
    data class Success<T>(
        val data: T,
        val requestId: String,
    ) : ApiResult<T>()

    data class Error(
        val code: String,
        val message: String,
        val requestId: String,
        val httpStatus: Int,
    ) : ApiResult<Nothing>()

    data class NetworkError(
        val throwable: Throwable,
    ) : ApiResult<Nothing>()
}
