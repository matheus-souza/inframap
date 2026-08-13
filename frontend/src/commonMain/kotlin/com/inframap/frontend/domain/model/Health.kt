package com.inframap.frontend.domain.model

data class Health(
    val status: String,
    val version: String,
) {
    val isHealthy: Boolean get() = status == "ok"
}
