package com.inframap.frontend.ui.discovery

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ProviderFormsTest {
    @Test
    fun offersProxmoxAndDockerInChipOrder() {
        assertEquals(listOf("proxmox", "docker"), ProviderForms.ids)
    }

    @Test
    fun proxmoxRequiresEndpointAndCredentials() {
        val missing = ProviderForms.missingFields(ProviderForms.PROXMOX, emptyMap())

        assertEquals(listOf("api_url", "token_id", "token_secret"), missing.map { it.key })
    }

    @Test
    fun proxmoxTokenSecretIsMasked() {
        val secret = ProviderForms.formFor(ProviderForms.PROXMOX)!!.fields.single { it.key == "token_secret" }

        assertTrue(secret.secret, "the API token must never be rendered in plain text")
    }

    @Test
    fun dockerTlsMaterialIsMasked() {
        val form = ProviderForms.formFor(ProviderForms.DOCKER)!!

        listOf("tls_ca", "tls_cert", "tls_key").forEach { key ->
            assertTrue(form.fields.single { it.key == key }.secret, "$key must be masked")
        }
    }

    @Test
    fun dockerAcceptsEitherSocketOrTcpButNotNeither() {
        // Neither field is individually required, so missingFields stays empty either way:
        // the endpoint rule is what has to catch a form with both left blank.
        assertTrue(ProviderForms.missingFields(ProviderForms.DOCKER, emptyMap()).isEmpty())

        assertTrue(ProviderForms.isEndpointMissing(ProviderForms.DOCKER, emptyMap()))
        assertTrue(ProviderForms.isEndpointMissing(ProviderForms.DOCKER, mapOf("socket_path" to " ")))
        assertFalse(
            ProviderForms.isEndpointMissing(
                ProviderForms.DOCKER,
                mapOf("socket_path" to "unix:///var/run/docker.sock"),
            ),
        )
        assertFalse(ProviderForms.isEndpointMissing(ProviderForms.DOCKER, mapOf("tcp_url" to "tcp://host:2376")))
    }

    @Test
    fun endpointRuleAppliesOnlyToDocker() {
        assertFalse(ProviderForms.isEndpointMissing(ProviderForms.PROXMOX, emptyMap()))
    }

    @Test
    fun unknownProviderHasNoForm() {
        assertNull(ProviderForms.formFor("unifi"))
        assertTrue(ProviderForms.missingFields("unifi", emptyMap()).isEmpty())
    }

    @Test
    fun validationErrorsAreKeyedPerProvider() {
        assertEquals("provider:docker", ProviderForms.labelKey(ProviderForms.DOCKER))
    }

    @Test
    fun fieldKeysMatchTheBackendConfigSchema() {
        // The values are sent verbatim to the health endpoint and stored on the collector
        // config, so a renamed key here silently stops reaching the provider.
        assertEquals(
            listOf("api_url", "token_id", "token_secret"),
            ProviderForms.formFor(ProviderForms.PROXMOX)!!.fields.map { it.key },
        )
        assertEquals(
            listOf("socket_path", "tcp_url", "tls_ca", "tls_cert", "tls_key"),
            ProviderForms.formFor(ProviderForms.DOCKER)!!.fields.map { it.key },
        )
    }
}
