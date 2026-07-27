// Package dto defines data transfer objects for the Integrations module.
package dto

import (
	"errors"

	"github.com/matheussouza/inframap/internal/platform/sdk"
)

var (
	// ErrInvalidConfig indicates that the provider configuration is missing or invalid.
	ErrInvalidConfig = errors.New("provider config payload cannot be nil")
)

// ProviderSchemaResponse represents a single registered integration provider's metadata and configuration schema.
type ProviderSchemaResponse struct {
	Metadata sdk.ProviderMetadata `json:"metadata"`
	Schema   sdk.ConfigSchema     `json:"schema"`
}

// HealthCheckRequest represents payload for checking provider connectivity.
type HealthCheckRequest struct {
	Config map[string]interface{} `json:"config"`
}

// Normalize initializes empty fields.
func (r *HealthCheckRequest) Normalize() {
	if r.Config == nil {
		r.Config = make(map[string]interface{})
	}
}

// Validate checks request validity.
func (r *HealthCheckRequest) Validate() error {
	if len(r.Config) == 0 {
		return ErrInvalidConfig
	}
	return nil
}

// HealthCheckResponse represents health check status response.
type HealthCheckResponse struct {
	ProviderID string `json:"provider_id"`
	Status     string `json:"status"` // "ok" or "error"
	Message    string `json:"message,omitempty"`
}
