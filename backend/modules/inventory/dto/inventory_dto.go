// Package dto provides request and response structures for inventory operations.
package dto

import (
	"time"
)

// DeviceResponse represents a network device in API responses.
type DeviceResponse struct {
	ID               string    `json:"id"`
	Hostname         string    `json:"hostname"`
	IPAddress        string    `json:"ip_address,omitempty"`
	MACAddress       string    `json:"mac_address,omitempty"`
	Manufacturer     string    `json:"manufacturer,omitempty"`
	Model            string    `json:"model,omitempty"`
	SerialNumber     string    `json:"serial_number,omitempty"`
	DeviceType       string    `json:"device_type"`
	Status           string    `json:"status"`
	FirstSeenAt      time.Time `json:"first_seen_at"`
	LastSeenAt       time.Time `json:"last_seen_at"`
	UserLockedFields []string  `json:"user_locked_fields,omitempty"`
	CreatedAt        time.Time `json:"created_at"`
	UpdatedAt        time.Time `json:"updated_at"`
}

// CreateDeviceRequest represents payload for manual device registration.
type CreateDeviceRequest struct {
	Hostname     string `json:"hostname"`
	IPAddress    string `json:"ip_address,omitempty"`
	MACAddress   string `json:"mac_address,omitempty"`
	Manufacturer string `json:"manufacturer,omitempty"`
	Model        string `json:"model,omitempty"`
	SerialNumber string `json:"serial_number,omitempty"`
	DeviceType   string `json:"device_type"`
}

// FieldError represents a validation issue on a request attribute.
type FieldError struct {
	Field string `json:"field"`
	Issue string `json:"issue"`
}

// Validate validates CreateDeviceRequest attributes.
func (r *CreateDeviceRequest) Validate() []FieldError {
	var errs []FieldError
	if len(r.Hostname) == 0 {
		errs = append(errs, FieldError{Field: "hostname", Issue: "hostname is required"})
	}
	if len(r.DeviceType) == 0 {
		r.DeviceType = "unknown"
	}
	return errs
}

// UpdateDeviceRequest represents payload for updating an existing device.
type UpdateDeviceRequest struct {
	Hostname     *string `json:"hostname,omitempty"`
	IPAddress    *string `json:"ip_address,omitempty"`
	MACAddress   *string `json:"mac_address,omitempty"`
	Manufacturer *string `json:"manufacturer,omitempty"`
	Model        *string `json:"model,omitempty"`
	SerialNumber *string `json:"serial_number,omitempty"`
	DeviceType   *string `json:"device_type,omitempty"`
	Status       *string `json:"status,omitempty"`
}

// StagingDeviceResponse represents an unverified device in the staging queue.
type StagingDeviceResponse struct {
	ID                string    `json:"id"`
	Hostname          string    `json:"hostname"`
	IPAddress         string    `json:"ip_address,omitempty"`
	MACAddress        string    `json:"mac_address,omitempty"`
	Manufacturer      string    `json:"manufacturer,omitempty"`
	Model             string    `json:"model,omitempty"`
	DeviceType        string    `json:"device_type"`
	DiscoverySourceID string    `json:"discovery_source_id,omitempty"`
	Status            string    `json:"status"`
	CreatedAt         time.Time `json:"created_at"`
}

// CreateSubnetRequest represents payload for registering a new subnet.
type CreateSubnetRequest struct {
	Name             string `json:"name"`
	CIDR             string `json:"cidr"`
	VLANID           *int32 `json:"vlan_id,omitempty"`
	GatewayIP        string `json:"gateway_ip,omitempty"`
	Description      string `json:"description,omitempty"`
	DiscoveryEnabled bool   `json:"discovery_enabled"`
}

// SubnetResponse represents a network subnet configuration.
type SubnetResponse struct {
	ID               string    `json:"id"`
	Name             string    `json:"name"`
	CIDR             string    `json:"cidr"`
	VLANID           *int32    `json:"vlan_id,omitempty"`
	GatewayIP        string    `json:"gateway_ip,omitempty"`
	Description      string    `json:"description,omitempty"`
	DiscoveryEnabled bool      `json:"discovery_enabled"`
	CreatedAt        time.Time `json:"created_at"`
}
