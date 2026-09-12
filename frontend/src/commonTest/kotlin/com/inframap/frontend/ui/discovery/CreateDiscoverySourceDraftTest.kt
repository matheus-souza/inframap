package com.inframap.frontend.ui.discovery

import com.inframap.frontend.data.storage.draft.DraftJson
import com.inframap.frontend.domain.model.CredentialSummary
import com.inframap.frontend.domain.model.SubnetSummary
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CreateDiscoverySourceDraftTest {
    @Test
    fun toDraftDropsProxmoxTokenSecret() {
        val state =
            CreateDiscoverySourceUiState(
                selectedCollectors = setOf("proxmox"),
                providerConfigs =
                    mapOf(
                        "proxmox" to
                            mapOf(
                                "api_url" to "https://pve.home:8006",
                                "token_id" to "root@pam!token",
                                "token_secret" to "SUPER-SECRET-UUID-TOKEN",
                                "tls_verify" to "false",
                            ),
                    ),
            )

        val draft = state.toDraft()
        val proxmoxConfig = draft.providerConfigs["proxmox"] ?: emptyMap()

        assertEquals("https://pve.home:8006", proxmoxConfig["api_url"])
        assertEquals("root@pam!token", proxmoxConfig["token_id"])
        assertEquals("false", proxmoxConfig["tls_verify"])
        assertFalse(proxmoxConfig.containsKey("token_secret"))
    }

    @Test
    fun toDraftDropsDockerTlsMaterial() {
        val state =
            CreateDiscoverySourceUiState(
                selectedCollectors = setOf("docker"),
                providerConfigs =
                    mapOf(
                        "docker" to
                            mapOf(
                                "tcp_url" to "tcp://docker.home:2376",
                                "tls_ca" to "-----BEGIN CERTIFICATE-----\nCA...",
                                "tls_cert" to "-----BEGIN CERTIFICATE-----\nCERT...",
                                "tls_key" to "-----BEGIN PRIVATE KEY-----\nKEY...",
                            ),
                    ),
            )

        val draft = state.toDraft()
        val dockerConfig = draft.providerConfigs["docker"] ?: emptyMap()

        assertEquals("tcp://docker.home:2376", dockerConfig["tcp_url"])
        assertFalse(dockerConfig.containsKey("tls_ca"))
        assertFalse(dockerConfig.containsKey("tls_cert"))
        assertFalse(dockerConfig.containsKey("tls_key"))
    }

    @Test
    fun toDraftKeepsNonSecretProviderFieldsAndCredentialId() {
        val state =
            CreateDiscoverySourceUiState(
                selectedCollectors = setOf("proxmox", "docker"),
                providerConfigs =
                    mapOf(
                        "proxmox" to
                            mapOf(
                                "api_url" to "https://pve:8006",
                                "credential_id" to "cred-proxmox-1",
                            ),
                        "docker" to
                            mapOf(
                                "socket_path" to "unix:///var/run/docker.sock",
                                "credential_id" to "cred-docker-1",
                            ),
                    ),
            )

        val draft = state.toDraft()
        assertEquals("cred-proxmox-1", draft.providerConfigs["proxmox"]?.get("credential_id"))
        assertEquals("cred-docker-1", draft.providerConfigs["docker"]?.get("credential_id"))
        assertEquals("unix:///var/run/docker.sock", draft.providerConfigs["docker"]?.get("socket_path"))
    }

    @Test
    fun toDraftDropsUnknownKeysAndUnknownProviders() {
        val state =
            CreateDiscoverySourceUiState(
                selectedCollectors = setOf("proxmox", "unknown_provider"),
                providerConfigs =
                    mapOf(
                        "proxmox" to
                            mapOf(
                                "api_url" to "https://pve:8006",
                                "unknown_key" to "malicious_or_leaked_value",
                            ),
                        "unknown_provider" to
                            mapOf(
                                "some_field" to "value",
                            ),
                    ),
            )

        val draft = state.toDraft()
        assertFalse(draft.providerConfigs.containsKey("unknown_provider"))
        assertFalse(draft.providerConfigs["proxmox"]?.containsKey("unknown_key") == true)
        assertEquals("https://pve:8006", draft.providerConfigs["proxmox"]?.get("api_url"))
    }

    @Test
    fun serializedDraftNeverContainsSecretValues() {
        val secretValue = "SUPER-SECRET-PASSWORD-OR-KEY-12345"
        val state =
            CreateDiscoverySourceUiState(
                name = "Secret Plan",
                selectedCollectors = setOf("proxmox", "docker"),
                providerConfigs =
                    mapOf(
                        "proxmox" to mapOf("api_url" to "https://pve:8006", "token_secret" to secretValue),
                        "docker" to mapOf("tcp_url" to "tcp://docker:2376", "tls_key" to secretValue),
                    ),
            )

        val draft = state.toDraft()
        val json = DraftJson.encodeToString(CreateDiscoverySourceDraft.serializer(), draft)

        assertFalse(
            json.contains(secretValue),
            "Serialized JSON must never contain raw secret values under any circumstances",
        )
    }

    @Test
    fun toDraftExcludesTransientState() {
        val state =
            CreateDiscoverySourceUiState(
                name = "My Discovery",
                selectedCollectors = setOf("icmp_sweep", "proxmox"),
                scheduleCron = "0 0 * * *",
                configCidr = "10.0.0.0/24",
                enabled = false,
                isSubmitting = true,
                isSuccess = true,
                subnets = listOf(SubnetSummary("s1", "LAN", "10.0.0.0/24")),
                isLoadingSubnets = true,
                connectionTests = mapOf("proxmox" to ConnectionTest.Healthy),
                credentials = listOf(CredentialSummary("c1", "Cred 1", "proxmox")),
                activeProviderTab = "proxmox",
            )

        val draft = state.toDraft()

        assertEquals("My Discovery", draft.name)
        assertEquals(setOf("icmp_sweep", "proxmox"), draft.selectedCollectors)
        assertEquals("0 0 * * *", draft.scheduleCron)
        assertEquals("10.0.0.0/24", draft.configCidr)
        assertFalse(draft.enabled)
        assertEquals("proxmox", draft.activeProviderTab)
    }

    @Test
    fun applyToReseedsProviderDefaults() {
        val draft =
            CreateDiscoverySourceDraft(
                name = "Restored Discovery",
                selectedCollectors = setOf("proxmox"),
                providerConfigs =
                    mapOf(
                        "proxmox" to
                            mapOf(
                                "api_url" to "https://proxmox.home:8006",
                                "token_id" to "root@pam!tok",
                            ),
                    ),
            )

        val restored = draft.applyTo(CreateDiscoverySourceUiState())

        assertEquals("Restored Discovery", restored.name)
        assertTrue(restored.restoredFromDraft)
        // Defaults reseeded (e.g. tls_verify default "true")
        val proxmoxConfig = restored.providerConfigs["proxmox"] ?: emptyMap()
        assertEquals("https://proxmox.home:8006", proxmoxConfig["api_url"])
        assertEquals("true", proxmoxConfig["tls_verify"])
    }

    @Test
    fun applyToKeepsRestoredValueOverDefault() {
        val draft =
            CreateDiscoverySourceDraft(
                selectedCollectors = setOf("proxmox"),
                providerConfigs =
                    mapOf(
                        "proxmox" to
                            mapOf(
                                "tls_verify" to "false",
                            ),
                    ),
            )

        val restored = draft.applyTo(CreateDiscoverySourceUiState())
        val proxmoxConfig = restored.providerConfigs["proxmox"] ?: emptyMap()
        assertEquals("false", proxmoxConfig["tls_verify"])
    }

    @Test
    fun applyToDropsSecretsFromLegacyDraft() {
        val draft =
            CreateDiscoverySourceDraft(
                selectedCollectors = setOf("proxmox", "docker"),
                providerConfigs =
                    mapOf(
                        "proxmox" to
                            mapOf(
                                "api_url" to "https://proxmox.home:8006",
                                "token_secret" to "leaked-secret",
                            ),
                        "docker" to
                            mapOf(
                                "socket_path" to "unix:///var/run/docker.sock",
                                "tls_key" to "leaked-private-key",
                            ),
                    ),
            )

        val restored = draft.applyTo(CreateDiscoverySourceUiState())
        assertFalse(restored.providerConfigs["proxmox"]?.containsKey("token_secret") == true)
        assertFalse(restored.providerConfigs["docker"]?.containsKey("tls_key") == true)
        assertEquals("https://proxmox.home:8006", restored.providerConfigs["proxmox"]?.get("api_url"))
        assertEquals("unix:///var/run/docker.sock", restored.providerConfigs["docker"]?.get("socket_path"))
    }

    @Test
    fun applyToDropsConfigsOfUnselectedProviders() {
        val draft =
            CreateDiscoverySourceDraft(
                selectedCollectors = setOf("proxmox"),
                providerConfigs =
                    mapOf(
                        "proxmox" to mapOf("api_url" to "https://proxmox.home:8006"),
                        "docker" to mapOf("socket_path" to "unix:///var/run/docker.sock"),
                    ),
            )

        val restored = draft.applyTo(CreateDiscoverySourceUiState())
        assertTrue(restored.providerConfigs.containsKey("proxmox"))
        assertFalse(restored.providerConfigs.containsKey("docker"))
    }

    @Test
    fun applyToDropsUnknownProviders() {
        val draft =
            CreateDiscoverySourceDraft(
                selectedCollectors = setOf("proxmox", "unknown_provider"),
                providerConfigs =
                    mapOf(
                        "proxmox" to mapOf("api_url" to "https://proxmox.home:8006"),
                        "unknown_provider" to mapOf("key" to "value"),
                    ),
            )

        val restored = draft.applyTo(CreateDiscoverySourceUiState())
        assertTrue(restored.providerConfigs.containsKey("proxmox"))
        assertFalse(restored.providerConfigs.containsKey("unknown_provider"))
    }

    @Test
    fun applyToSeedsDefaultsForSelectedProviderMissingFromDraft() {
        val draft =
            CreateDiscoverySourceDraft(
                selectedCollectors = setOf("proxmox"),
                providerConfigs = emptyMap(),
            )

        val restored = draft.applyTo(CreateDiscoverySourceUiState())
        val proxmoxConfig = restored.providerConfigs["proxmox"] ?: emptyMap()
        assertEquals("true", proxmoxConfig["tls_verify"])
    }
}
