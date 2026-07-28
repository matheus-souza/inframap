// Package dto defines data transfer objects for the Credentials module.
package dto

import (
	"errors"
	"strings"
	"time"
)

var (
	// ErrMissingCredentialName indicates name field is empty.
	ErrMissingCredentialName = errors.New("credential name is required")

	// ErrInvalidCredentialType indicates an unsupported credential type string.
	ErrInvalidCredentialType = errors.New("invalid credential type: must be snmp_v2c, snmp_v3, ssh_key, api_token, or custom_secret")

	// ErrMissingSecretData indicates secret payload is empty.
	ErrMissingSecretData = errors.New("credential secret data is required")
)

// SupportedTypes contains all valid credential type identifiers.
var SupportedTypes = map[string]bool{
	"snmp_v2c":      true,
	"snmp_v3":       true,
	"ssh_key":       true,
	"api_token":     true,
	"custom_secret": true,
}

// CreateCredentialRequest represents the payload to register a new credential.
type CreateCredentialRequest struct {
	Name        string `json:"name"`
	Type        string `json:"type"`
	SecretData  string `json:"secret_data"`
	Description string `json:"description"`
}

// Normalize trims whitespace from string fields.
func (r *CreateCredentialRequest) Normalize() {
	r.Name = strings.TrimSpace(r.Name)
	r.Type = strings.ToLower(strings.TrimSpace(r.Type))
	r.SecretData = strings.TrimSpace(r.SecretData)
	r.Description = strings.TrimSpace(r.Description)
}

// Validate checks request validity.
func (r *CreateCredentialRequest) Validate() error {
	if r.Name == "" {
		return ErrMissingCredentialName
	}
	if !SupportedTypes[r.Type] {
		return ErrInvalidCredentialType
	}
	if r.SecretData == "" {
		return ErrMissingSecretData
	}
	return nil
}

// CredentialResponse represents credential metadata returned in REST responses.
type CredentialResponse struct {
	ID          string    `json:"id"`
	Name        string    `json:"name"`
	Type        string    `json:"type"`
	Description string    `json:"description,omitempty"`
	SecretData  string    `json:"secret_data,omitempty"`
	CreatedAt   time.Time `json:"created_at"`
	UpdatedAt   time.Time `json:"updated_at"`
}
