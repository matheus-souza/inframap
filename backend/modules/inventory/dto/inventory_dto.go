// Package dto provides request and response structures for inventory operations.
package dto

import (
	"net/netip"
	"strings"
	"time"
)

// DeviceResponse represents a network device in API responses.
type DeviceResponse struct {
	ID           string `json:"id"`
	Hostname     string `json:"hostname"`
	IPAddress    string `json:"ip_address,omitempty"`
	MACAddress   string `json:"mac_address,omitempty"`
	Manufacturer string `json:"manufacturer,omitempty"`
	Model        string `json:"model,omitempty"`
	SerialNumber string `json:"serial_number,omitempty"`
	DeviceType   string `json:"device_type"`
	Status       string `json:"status"`
	// PowerState is the runtime state a provider reports for a workload (running, stopped,
	// paused). It is distinct from Status, which is what InfraMap observed: a stopped
	// container is still an active, healthy inventory entry. Empty for devices that no
	// provider owns.
	PowerState       string    `json:"power_state,omitempty"`
	ParentDeviceID   string    `json:"parent_device_id,omitempty"`
	FirstSeenAt      time.Time `json:"first_seen_at"`
	LastSeenAt       time.Time `json:"last_seen_at"`
	UserLockedFields        []string  `json:"user_locked_fields,omitempty"`
	DiscoverySourceInactive bool      `json:"discovery_source_inactive,omitempty"`
	CreatedAt               time.Time `json:"created_at"`
	UpdatedAt               time.Time `json:"updated_at"`
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

// Normalize applies defaults and trims whitespace before validation.
func (r *CreateDeviceRequest) Normalize() {
	r.Hostname = strings.TrimSpace(r.Hostname)
	r.DeviceType = strings.TrimSpace(r.DeviceType)
	if r.DeviceType == "" {
		r.DeviceType = "unknown"
	}
}

// Validate validates CreateDeviceRequest attributes.
// Call Normalize() before Validate().
func (r *CreateDeviceRequest) Validate() []FieldError {
	var errs []FieldError
	if len(r.Hostname) == 0 {
		errs = append(errs, FieldError{Field: "hostname", Issue: "hostname is required"})
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
	PreviouslyDeleted bool      `json:"previously_deleted"`
	MatchedDeviceID   *string   `json:"matched_device_id,omitempty"`
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

// Validate validates CreateSubnetRequest attributes.
func (r *CreateSubnetRequest) Validate() []FieldError {
	var errs []FieldError
	if strings.TrimSpace(r.Name) == "" {
		errs = append(errs, FieldError{Field: "name", Issue: "subnet name is required"})
	}
	cidr := strings.TrimSpace(r.CIDR)
	if cidr == "" {
		errs = append(errs, FieldError{Field: "cidr", Issue: "CIDR is required"})
	} else {
		if _, err := netip.ParsePrefix(cidr); err != nil {
			errs = append(errs, FieldError{Field: "cidr", Issue: "invalid CIDR notation"})
		}
	}
	return errs
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
	UpdatedAt        time.Time `json:"updated_at,omitempty"`
}

// UpdateSubnetRequest represents the payload to update an existing subnet.
type UpdateSubnetRequest struct {
	Name             string  `json:"name"`
	CIDR             string  `json:"cidr"`
	VLANID           *int32  `json:"vlan_id,omitempty"`
	GatewayIP        *string `json:"gateway_ip,omitempty"`
	Description      *string `json:"description,omitempty"`
	DiscoveryEnabled *bool   `json:"discovery_enabled,omitempty"`
}

// Validate validates UpdateSubnetRequest attributes.
func (r *UpdateSubnetRequest) Validate() []FieldError {
	var errs []FieldError
	if strings.TrimSpace(r.Name) == "" {
		errs = append(errs, FieldError{Field: "name", Issue: "subnet name is required"})
	}
	cidr := strings.TrimSpace(r.CIDR)
	if cidr == "" {
		errs = append(errs, FieldError{Field: "cidr", Issue: "CIDR is required"})
	} else {
		if _, err := netip.ParsePrefix(cidr); err != nil {
			errs = append(errs, FieldError{Field: "cidr", Issue: "invalid CIDR notation"})
		}
	}
	if r.GatewayIP != nil && strings.TrimSpace(*r.GatewayIP) != "" {
		if _, err := netip.ParseAddr(strings.TrimSpace(*r.GatewayIP)); err != nil {
			errs = append(errs, FieldError{Field: "gateway_ip", Issue: "invalid gateway IP address"})
		}
	}
	return errs
}

// SubnetCIDRImpactRequest represents the payload to check the impact of a subnet CIDR change.
type SubnetCIDRImpactRequest struct {
	NewCIDR string `json:"new_cidr"`
}

// Validate validates SubnetCIDRImpactRequest attributes.
func (r *SubnetCIDRImpactRequest) Validate() []FieldError {
	var errs []FieldError
	cidr := strings.TrimSpace(r.NewCIDR)
	if cidr == "" {
		errs = append(errs, FieldError{Field: "new_cidr", Issue: "new_cidr is required"})
	} else {
		if _, err := netip.ParsePrefix(cidr); err != nil {
			errs = append(errs, FieldError{Field: "new_cidr", Issue: "invalid CIDR notation"})
		}
	}
	return errs
}

// SubnetCIDRImpactResponse represents the count of devices affected by a CIDR change.
type SubnetCIDRImpactResponse struct {
	CurrentCIDR          string `json:"current_cidr"`
	NewCIDR              string `json:"new_cidr"`
	AffectedDevicesCount int    `json:"affected_devices_count"`
}

// SubnetDeletionImpact contains counts of entities affected by a subnet deletion.
type SubnetDeletionImpact struct {
	AffectedDevices        int `json:"affected_devices"`
	UnlinkedTopologyEdges int `json:"unlinked_topology_edges"`
}

// SubnetDeletionImpactResponse represents the impact of deleting a subnet.
type SubnetDeletionImpactResponse struct {
	SubnetID string               `json:"subnet_id"`
	Impact   SubnetDeletionImpact `json:"impact"`
}

// DeleteSubnetResponse represents the result of soft-deleting a subnet.
type DeleteSubnetResponse struct {
	DeletedID string               `json:"deleted_id"`
	Impact    SubnetDeletionImpact `json:"impact"`
}


