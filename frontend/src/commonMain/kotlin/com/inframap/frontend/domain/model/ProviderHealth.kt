package com.inframap.frontend.domain.model

/**
 * Outcome of a provider connectivity check.
 *
 * The endpoint answers 200 for both outcomes and reports the verdict in the body, so a
 * transport-level success is not the same as a healthy provider: callers must read [isHealthy].
 */
data class ProviderHealth(
    val providerId: String,
    val isHealthy: Boolean,
    val message: String?,
)
