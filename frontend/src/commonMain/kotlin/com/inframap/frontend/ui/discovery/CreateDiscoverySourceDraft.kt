package com.inframap.frontend.ui.discovery

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class CreateDiscoverySourceDraft(
    val name: String = "",
    @SerialName("selected_collectors") val selectedCollectors: Set<String> = emptySet(),
    @SerialName("schedule_cron") val scheduleCron: String = "",
    @SerialName("config_cidr") val configCidr: String = "",
    val enabled: Boolean = true,
    @SerialName("provider_configs") val providerConfigs: Map<String, Map<String, String>> = emptyMap(),
    @SerialName("active_provider_tab") val activeProviderTab: String? = null,
)

internal fun CreateDiscoverySourceUiState.toDraft(): CreateDiscoverySourceDraft {
    val filteredConfigs =
        providerConfigs
            .filterKeys { it in selectedCollectors }
            .mapNotNull { (providerId, config) ->
                val allowedKeys = ProviderForms.persistableKeys(providerId)
                if (allowedKeys.isEmpty()) return@mapNotNull null
                val sanitized = config.filterKeys { it in allowedKeys }
                providerId to sanitized
            }.toMap()

    return CreateDiscoverySourceDraft(
        name = name,
        selectedCollectors = selectedCollectors,
        scheduleCron = scheduleCron,
        configCidr = configCidr,
        enabled = enabled,
        providerConfigs = filteredConfigs,
        activeProviderTab = activeProviderTab,
    )
}

internal fun CreateDiscoverySourceDraft.applyTo(state: CreateDiscoverySourceUiState): CreateDiscoverySourceUiState {
    val reseededConfigs =
        ProviderForms.ids
            .filter { it in selectedCollectors }
            .associateWith { id ->
                val allowed = ProviderForms.persistableKeys(id)
                ProviderForms.defaults(id) + providerConfigs[id].orEmpty().filterKeys { it in allowed }
            }

    return state.copy(
        name = name,
        selectedCollectors = selectedCollectors,
        scheduleCron = scheduleCron,
        configCidr = configCidr,
        enabled = enabled,
        providerConfigs = reseededConfigs,
        activeProviderTab = activeProviderTab,
        restoredFromDraft = true,
    )
}
