package com.inframap.frontend.data.storage.draft

enum class DraftForm(
    val storageKey: String,
) {
    CreateSubnet("inframap_draft_create_subnet"),
    CreateDiscoverySource("inframap_draft_create_discovery_source"),
}
