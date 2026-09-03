package com.inframap.frontend.domain.model

/**
 * A stored credential, as offered when configuring a provider.
 *
 * Carries no secret: the list endpoint never returns one, and the plan only stores the id.
 * The secrets are resolved server-side at execution time.
 */
data class CredentialSummary(
    val id: String,
    val name: String,
    val type: String,
)
