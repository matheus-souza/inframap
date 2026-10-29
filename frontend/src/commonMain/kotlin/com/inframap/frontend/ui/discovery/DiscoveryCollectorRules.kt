package com.inframap.frontend.ui.discovery

import com.inframap.frontend.generated.resources.Res
import com.inframap.frontend.generated.resources.discovery_validation_collector_required
import com.inframap.frontend.generated.resources.provider_validation_docker_endpoint_required
import com.inframap.frontend.generated.resources.provider_validation_field_required
import com.inframap.frontend.ui.util.UiText

/** Validation key of the collector selection. */
internal const val COLLECTORS_ERROR_KEY = "collectors"

/**
 * The rules of a discovery plan's collector selection, shared by the create and edit forms so
 * both accept exactly the same plans.
 */
internal object DiscoveryCollectorRules {
    /**
     * Seeds every provider switched on in [next] with its defaults, so a field the operator
     * never touches still reaches the backend with its intended value. Values already typed
     * (or loaded) win over the defaults.
     */
    fun seedDefaults(
        previous: Set<String>,
        next: Set<String>,
        providerConfigs: Map<String, Map<String, String>>,
    ): Map<String, Map<String, String>> {
        val seeded =
            next
                .filter { it !in previous }
                .mapNotNull { id -> ProviderForms.defaults(id).takeIf { it.isNotEmpty() }?.let { id to it } }
                .toMap()
        return seeded + providerConfigs
    }

    /**
     * The provider tab to show after the selection changed: a provider just switched on is the
     * one the operator is about to configure; otherwise the active tab while it is still
     * selected, else the first selected provider.
     */
    fun providerTabAfter(
        previous: Set<String>,
        next: Set<String>,
        activeTab: String?,
    ): String? {
        val selectedProviders = ProviderForms.ids.filter { it in next }
        return selectedProviders.firstOrNull { it !in previous }
            ?: activeTab?.takeIf { it in selectedProviders }
            ?: selectedProviders.firstOrNull()
    }

    /** A plan runs at least one collector. */
    fun selectionError(selected: Set<String>): UiText? =
        if (selected.isEmpty()) UiText.Resource(Res.string.discovery_validation_collector_required) else null

    /**
     * Validates the configuration of the given providers, keyed by [ProviderForms.labelKey].
     *
     * A connectivity check is deliberately not required to submit: the daemon may be temporarily
     * unreachable while the plan is still worth saving.
     */
    fun providerErrors(
        providers: List<String>,
        providerConfigs: Map<String, Map<String, String>>,
    ): Map<String, UiText> =
        providers
            .mapNotNull { providerId ->
                val config = providerConfigs[providerId].orEmpty()
                val error =
                    when {
                        ProviderForms.missingFields(providerId, config).isNotEmpty() ->
                            UiText.Resource(Res.string.provider_validation_field_required)
                        ProviderForms.isEndpointMissing(providerId, config) ->
                            UiText.Resource(Res.string.provider_validation_docker_endpoint_required)
                        else -> null
                    }
                error?.let { ProviderForms.labelKey(providerId) to it }
            }.toMap()
}
