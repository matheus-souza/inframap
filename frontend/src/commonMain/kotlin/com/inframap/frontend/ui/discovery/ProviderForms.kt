package com.inframap.frontend.ui.discovery

import com.inframap.frontend.generated.resources.Res
import com.inframap.frontend.generated.resources.provider_field_docker_socket_path
import com.inframap.frontend.generated.resources.provider_field_docker_tcp_url
import com.inframap.frontend.generated.resources.provider_field_docker_tls_ca
import com.inframap.frontend.generated.resources.provider_field_docker_tls_cert
import com.inframap.frontend.generated.resources.provider_field_docker_tls_key
import com.inframap.frontend.generated.resources.provider_field_proxmox_api_url
import com.inframap.frontend.generated.resources.provider_field_proxmox_tls_verify
import com.inframap.frontend.generated.resources.provider_field_proxmox_token_id
import com.inframap.frontend.generated.resources.provider_field_proxmox_token_secret
import org.jetbrains.compose.resources.StringResource

/**
 * A single input of a provider configuration form.
 *
 * [secret] drives password masking, and it is what keeps a Proxmox token or a TLS private
 * key from being rendered in plain text on screen.
 */
data class ProviderField(
    val key: String,
    val label: StringResource,
    val placeholder: String,
    val required: Boolean = false,
    val secret: Boolean = false,
    /**
     * Rendered as a checkbox instead of a text input. The value travels as the string "true"
     * or "false", because the collector config is a string map end to end.
     */
    val boolean: Boolean = false,
    /** Value used when the operator never touches the field. */
    val default: String = "",
)

data class ProviderForm(
    val id: String,
    val fields: List<ProviderField>,
)

/**
 * Field definitions for the providers that can be configured inside a discovery plan.
 *
 * The keys mirror the `ConfigSchema` each backend provider declares, because the values are
 * sent verbatim both to the health-check endpoint and to the collector config on the plan.
 */
object ProviderForms {
    const val PROXMOX = "proxmox"
    const val DOCKER = "docker"

    private val proxmox =
        ProviderForm(
            id = PROXMOX,
            fields =
                listOf(
                    ProviderField(
                        key = "api_url",
                        label = Res.string.provider_field_proxmox_api_url,
                        placeholder = "https://proxmox.local:8006",
                        required = true,
                    ),
                    ProviderField(
                        key = "token_id",
                        label = Res.string.provider_field_proxmox_token_id,
                        placeholder = "root@pam!inframap",
                        required = true,
                    ),
                    ProviderField(
                        key = "token_secret",
                        label = Res.string.provider_field_proxmox_token_secret,
                        placeholder = "",
                        required = true,
                        secret = true,
                    ),
                    // Homelab Proxmox installs commonly use a self-signed certificate, so the
                    // operator needs a way to turn verification off. It defaults to on, which
                    // matches the backend default (CONTEXT.md guideline #174).
                    ProviderField(
                        key = "tls_verify",
                        label = Res.string.provider_field_proxmox_tls_verify,
                        placeholder = "",
                        boolean = true,
                        default = "true",
                    ),
                ),
        )

    private val docker =
        ProviderForm(
            id = DOCKER,
            fields =
                listOf(
                    ProviderField(
                        key = "socket_path",
                        label = Res.string.provider_field_docker_socket_path,
                        placeholder = "unix:///var/run/docker.sock",
                    ),
                    ProviderField(
                        key = "tcp_url",
                        label = Res.string.provider_field_docker_tcp_url,
                        placeholder = "tcp://192.168.1.50:2376",
                    ),
                    ProviderField(
                        key = "tls_ca",
                        label = Res.string.provider_field_docker_tls_ca,
                        placeholder = "",
                        secret = true,
                    ),
                    ProviderField(
                        key = "tls_cert",
                        label = Res.string.provider_field_docker_tls_cert,
                        placeholder = "",
                        secret = true,
                    ),
                    ProviderField(
                        key = "tls_key",
                        label = Res.string.provider_field_docker_tls_key,
                        placeholder = "",
                        secret = true,
                    ),
                ),
        )

    private val byId = listOf(proxmox, docker).associateBy { it.id }

    /** Provider ids in the order their chips are offered. */
    val ids: List<String> = listOf(PROXMOX, DOCKER)

    fun formFor(providerId: String): ProviderForm? = byId[providerId]

    /**
     * Reports which required fields of a selected provider are still blank.
     *
     * Docker is the exception: it accepts either a socket path or a TCP URL, so neither is
     * individually required but at least one has to be present — leaving both empty would
     * silently fall back to the local daemon socket on the server side.
     */
    fun missingFields(
        providerId: String,
        config: Map<String, String>,
    ): List<ProviderField> {
        val form = formFor(providerId)
        // A stored credential supplies the required values, so the inline fields stop being
        // mandatory — otherwise picking one would still demand retyping the secret it holds.
        if (form == null || usesStoredCredential(config)) {
            return emptyList()
        }
        return form.fields.filter { it.required && !it.boolean && config[it.key].isNullOrBlank() }
    }

    /**
     * Config key holding a reference to a stored credential. The backend resolves it at
     * execution time and merges the secrets into the provider config, with values written
     * directly on the collector taking precedence.
     */
    const val CREDENTIAL_KEY = "credential_id"

    /**
     * Whether a provider's own required fields still have to be filled in.
     *
     * Referencing a stored credential supplies them, so the inline fields stop being
     * mandatory — otherwise picking a credential would still demand retyping the secret it
     * exists to hold.
     */
    fun usesStoredCredential(config: Map<String, String>): Boolean = !config[CREDENTIAL_KEY].isNullOrBlank()

    /**
     * The values a provider form starts with, so a field the operator never touches still
     * reaches the backend with its intended default rather than absent.
     */
    fun defaults(providerId: String): Map<String, String> =
        formFor(providerId)
            ?.fields
            ?.filter { it.default.isNotEmpty() }
            ?.associate { it.key to it.default }
            .orEmpty()

    /** Key under which a provider's validation error is stored in the form error map. */
    fun labelKey(providerId: String): String = "provider:$providerId"

    fun isEndpointMissing(
        providerId: String,
        config: Map<String, String>,
    ): Boolean =
        providerId == DOCKER &&
            !usesStoredCredential(config) &&
            config["socket_path"].isNullOrBlank() &&
            config["tcp_url"].isNullOrBlank()
}
