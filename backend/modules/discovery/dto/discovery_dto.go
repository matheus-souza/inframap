// Package dto defines data transfer objects and validation logic for the Discovery Engine.
package dto

import (
	"errors"
	"fmt"
	"strings"
	"time"

	"github.com/google/uuid"
)

var (
	// ErrInvalidDiscoveryType indicates an unsupported discovery source type.
	ErrInvalidDiscoveryType = errors.New("invalid discovery source type; must be one of: icmp_sweep, arp_sweep, reverse_dns, snmp, mdns, proxmox, docker, unifi")

	// ErrEmptySourceName indicates that discovery source name is empty.
	ErrEmptySourceName = errors.New("discovery source name cannot be empty")
)

// ValidDiscoveryTypes lists supported discovery collector types per RFC-007 and RFC-016.
var ValidDiscoveryTypes = map[string]bool{
	"icmp_sweep":  true,
	"arp_sweep":   true,
	"reverse_dns": true,
	"snmp":        true,
	"mdns":        true,
	"proxmox":     true,
	"docker":      true,
	"unifi":       true,
}

// CreateDiscoverySourceRequest represents the HTTP request payload to register a discovery source.
type CreateDiscoverySourceRequest struct {
	Name         string                 `json:"name"`
	Type         string                 `json:"type"`
	Enabled      *bool                  `json:"enabled,omitempty"`
	ScheduleCron string                 `json:"schedule_cron,omitempty"`
	Config       map[string]interface{} `json:"config,omitempty"`
}

// Normalize trims whitespace and sets default values on the request struct.
func (r *CreateDiscoverySourceRequest) Normalize() {
	r.Name = strings.TrimSpace(r.Name)
	r.Type = strings.ToLower(strings.TrimSpace(r.Type))
	r.ScheduleCron = strings.TrimSpace(r.ScheduleCron)
	if r.Enabled == nil {
		defaultVal := true
		r.Enabled = &defaultVal
	}
	if r.Config == nil {
		r.Config = make(map[string]interface{})
	}
}

// Validate executes pure validation rules without mutating state.
func (r *CreateDiscoverySourceRequest) Validate() error {
	if r.Name == "" {
		return ErrEmptySourceName
	}
	if !ValidDiscoveryTypes[r.Type] {
		return ErrInvalidDiscoveryType
	}
	return nil
}

// DiscoverySourceResponse represents a discovery source returned to API clients.
type DiscoverySourceResponse struct {
	ID           uuid.UUID  `json:"id"`
	Name         string     `json:"name"`
	Type         string     `json:"type"`
	Enabled      bool       `json:"enabled"`
	ScheduleCron *string    `json:"schedule_cron,omitempty"`
	ConfigCIDR   string     `json:"config_cidr,omitempty"`
	LastRunAt    *time.Time `json:"last_run_at,omitempty"`
	LastStatus   string     `json:"last_status"`
	CreatedAt    time.Time  `json:"created_at"`
	UpdatedAt    time.Time  `json:"updated_at"`
}

// NormalizedDeviceDTO represents a normalized device observation parsed from raw scanner payloads.
type NormalizedDeviceDTO struct {
	Hostname        string                 `json:"hostname"`
	IPAddress       string                 `json:"ip_address,omitempty"`
	MACAddress      string                 `json:"mac_address,omitempty"`
	Manufacturer    string                 `json:"manufacturer,omitempty"`
	Model           string                 `json:"model,omitempty"`
	SerialNumber    string                 `json:"serial_number,omitempty"`
	DeviceType      string                 `json:"device_type"`
	ProviderUUID    string                 `json:"provider_uuid,omitempty"`
	ConfidenceScore int                    `json:"confidence_score"`
	RawPayload      map[string]interface{} `json:"raw_payload"`
}

// DiscoveryRecordResponse represents a discovery observation record.
type DiscoveryRecordResponse struct {
	ID                uuid.UUID              `json:"id"`
	DeviceID          uuid.UUID              `json:"device_id"`
	DiscoverySourceID uuid.UUID              `json:"discovery_source_id"`
	MatchedBy         string                 `json:"matched_by"`
	RawPayload        map[string]interface{} `json:"raw_payload"`
	LastScannedAt     time.Time              `json:"last_scanned_at"`
}

// CollectorRunDetail represents the execution metrics and outcome of an individual collector in a scan.
type CollectorRunDetail struct {
	CollectorType string `json:"collector_type"`
	Status        string `json:"status"`
	DevicesFound  int    `json:"devices_found"`
	DurationMs    int64  `json:"duration_ms"`
	ErrorMessage  string `json:"error_message,omitempty"`
}

// TriggerScanRequest represents the request payload to initiate an active network scan.
type TriggerScanRequest struct {
	CIDR            string   `json:"cidr"`
	SubnetID        string   `json:"subnet_id,omitempty"`
	CredentialSetID *string  `json:"credential_set_id,omitempty"`
	Collectors      []string `json:"collectors,omitempty"`
}

// Normalize trims whitespace and normalizes payload parameters.
func (r *TriggerScanRequest) Normalize() {
	r.CIDR = strings.TrimSpace(r.CIDR)
	r.SubnetID = strings.TrimSpace(r.SubnetID)
	if r.CredentialSetID != nil {
		val := strings.TrimSpace(*r.CredentialSetID)
		if val == "" {
			r.CredentialSetID = nil
		} else {
			r.CredentialSetID = &val
		}
	}
	if len(r.Collectors) > 0 {
		seen := make(map[string]bool)
		var normalized []string
		for _, col := range r.Collectors {
			c := strings.ToLower(strings.TrimSpace(col))
			if c != "" && !seen[c] {
				seen[c] = true
				normalized = append(normalized, c)
			}
		}
		r.Collectors = normalized
	}
}

// Validate checks that CIDR is present and valid, and validates any requested collectors against ValidDiscoveryTypes.
func (r *TriggerScanRequest) Validate() error {
	if r.CIDR == "" {
		return errors.New("cidr target string cannot be empty")
	}
	for _, col := range r.Collectors {
		if !ValidDiscoveryTypes[col] {
			return fmt.Errorf("%w: %s", ErrInvalidDiscoveryType, col)
		}
	}
	return nil
}

// ScanResultResponse represents the HTTP API response summarizing an active discovery scan execution.
type ScanResultResponse struct {
	CIDR            string               `json:"cidr"`
	TotalCollected  int                  `json:"total_collected"`
	TotalValid      int                  `json:"total_valid"`
	TotalDiscovered int                  `json:"total_discovered"`
	TotalUpdated    int                  `json:"total_updated"`
	DurationMs      int64                `json:"duration_ms"`
	Collectors      []CollectorRunDetail `json:"collectors,omitempty"`
}
