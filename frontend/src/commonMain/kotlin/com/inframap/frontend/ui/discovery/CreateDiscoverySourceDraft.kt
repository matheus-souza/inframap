package com.inframap.frontend.ui.discovery

import com.inframap.frontend.domain.model.DiscoveryTarget
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class CreateDiscoverySourceDraft(
    val name: String = "",
    @SerialName("selected_collectors") val selectedCollectors: Set<String> = emptySet(),
    @SerialName("schedule_cron") val scheduleCron: String = "",
    /**
     * The single CIDR of drafts saved before sources held a list of targets. It is only read:
     * an old draft restores it as one free-form target, and new drafts leave it empty.
     */
    @SerialName("config_cidr") val configCidr: String = "",
    val targets: List<CreateDiscoverySourceDraftTarget> = emptyList(),
    @SerialName("target_input") val targetInput: String = "",
    val enabled: Boolean = true,
    @SerialName("provider_configs") val providerConfigs: Map<String, Map<String, String>> = emptyMap(),
    @SerialName("active_provider_tab") val activeProviderTab: String? = null,
)

/** A target as a draft keeps it: addresses and Subnet links only, never a credential. */
@Serializable
data class CreateDiscoverySourceDraftTarget(
    val cidr: String,
    @SerialName("subnet_id") val subnetId: String? = null,
    @SerialName("subnet_name") val subnetName: String? = null,
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
        targets = targets.map { CreateDiscoverySourceDraftTarget(it.cidr, it.subnetId, it.subnetName) },
        targetInput = targetInput,
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

    val restoredTargets =
        if (targets.isEmpty() && configCidr.isNotBlank()) {
            listOf(DiscoveryTarget(cidr = configCidr.trim()))
        } else {
            targets.map { DiscoveryTarget(cidr = it.cidr, subnetId = it.subnetId, subnetName = it.subnetName) }
        }

    return state.copy(
        name = name,
        selectedCollectors = selectedCollectors,
        scheduleCron = scheduleCron,
        targets = restoredTargets,
        targetInput = targetInput,
        enabled = enabled,
        providerConfigs = reseededConfigs,
        activeProviderTab = activeProviderTab,
        restoredFromDraft = true,
    )
}
