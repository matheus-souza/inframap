// Package sdk defines the standard contracts and interfaces for InfraMap integration providers.
package sdk

import (
	"context"
	"errors"
	"fmt"
)

var (
	// ErrMissingConfigField indicates that a required configuration key is missing.
	ErrMissingConfigField = errors.New("missing required configuration field")

	// ErrInvalidConfigType indicates that a configuration value has an unexpected type.
	ErrInvalidConfigType = errors.New("invalid configuration field type")
)

// ProviderMetadata defines human-readable details for UI rendering and registration.
type ProviderMetadata struct {
	ID          string `json:"id"`          // e.g. "proxmox", "docker", "unifi"
	Name        string `json:"name"`        // e.g. "Proxmox VE"
	Description string `json:"description"` // e.g. "Discovers VMs and LXC containers via Proxmox REST API"
	Version     string `json:"version"`     // e.g. "1.0.0"
	Icon        string `json:"icon"`        // Icon identifier or SVG path
	Category    string `json:"category"`    // hypervisor, container, network, iot
}

// ConfigField defines a dynamic configuration parameter required by the integration.
type ConfigField struct {
	Key         string      `json:"key"`                   // e.g. "api_url", "token_id"
	Label       string      `json:"label"`                 // e.g. "Proxmox API URL"
	Type        string      `json:"type"`                  // text, password, number, boolean
	Required    bool        `json:"required"`
	Default     interface{} `json:"default,omitempty"`
	Description string      `json:"description,omitempty"`
}

// ConfigSchema returns JSON Schema parameters for UI form generation.
type ConfigSchema struct {
	Fields []ConfigField `json:"fields"`
}

// ProviderConfig represents decrypted runtime parameters for provider execution.
type ProviderConfig map[string]interface{}

// GetString extracts a string value from ProviderConfig.
func (c ProviderConfig) GetString(key string) (string, error) {
	val, exists := c[key]
	if !exists || val == nil {
		return "", fmt.Errorf("%w: %s", ErrMissingConfigField, key)
	}
	strVal, ok := val.(string)
	if !ok {
		return "", fmt.Errorf("%w: %s must be a string", ErrInvalidConfigType, key)
	}
	return strVal, nil
}

// GetStringOrDefault extracts a string value or returns fallback if absent.
func (c ProviderConfig) GetStringOrDefault(key, fallback string) string {
	val, err := c.GetString(key)
	if err != nil || val == "" {
		return fallback
	}
	return val
}

// NormalizedDevice represents standardized infrastructure entity output produced by provider discovery.
type NormalizedDevice struct {
	Hostname   string                 `json:"hostname"`
	IPAddress  string                 `json:"ip_address,omitempty"`
	MACAddress string                 `json:"mac_address,omitempty"`
	DeviceType string                 `json:"device_type"`
	Vendor     string                 `json:"vendor,omitempty"`
	Model      string                 `json:"model,omitempty"`
	OSName     string                 `json:"os_name,omitempty"`
	OSVersion  string                 `json:"os_version,omitempty"`
	Metadata   map[string]interface{} `json:"metadata,omitempty"`

	// ProviderRef is the canonical identity of the workload within the provider. Providers
	// MUST populate it so the discovery engine can match entities that carry neither MAC
	// nor IP address (stopped containers, offline VMs without a guest agent).
	ProviderRef *ProviderRef `json:"provider_ref,omitempty"`

	// ParentProviderRef is the identity of the host that contains this workload
	// (the Proxmox node for a VM/LXC, the Docker engine for a container). It is nil for
	// entities that sit at the top of the containment hierarchy.
	ParentProviderRef *ProviderRef `json:"parent_provider_ref,omitempty"`
}

// Provider is the mandatory contract for all InfraMap integrations.
type Provider interface {
	// ID returns the unique provider identifier (e.g. "proxmox", "docker").
	ID() string

	// Metadata returns information for UI representation.
	Metadata() ProviderMetadata

	// ConfigSchema defines input parameters needed by the provider.
	ConfigSchema() ConfigSchema

	// HealthCheck validates connectivity to the target service.
	HealthCheck(ctx context.Context, config ProviderConfig) error

	// Discover executes scanning and returns normalized device DTOs.
	Discover(ctx context.Context, config ProviderConfig) ([]NormalizedDevice, error)
}
