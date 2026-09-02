// Package engine implements the identity matching and field reconciliation algorithms for InfraMap.
package engine

import (
	"encoding/json"
	"fmt"
	"net"
	"net/netip"
	"strings"

	"github.com/google/uuid"
	"github.com/matheussouza/inframap/internal/platform/db"
	"github.com/matheussouza/inframap/modules/discovery/dto"
)

// MatchResult represents the outcome of an identity resolution match.
type MatchResult struct {
	DeviceID  *uuid.UUID
	MatchedBy string
}

// IdentityMatcher resolves incoming normalized device observation payloads against existing inventory.
type IdentityMatcher interface {
	MatchDevice(normalized *dto.NormalizedDeviceDTO, activeDevices []db.Device) MatchResult
}

// DeviceMetadataProviderRefKey is the devices.metadata key holding the canonical
// ProviderRef of a workload. The partial unique index uq_devices_provider_ref is built on
// metadata->>'provider_ref', so the value stored there must be the canonical key string.
const DeviceMetadataProviderRefKey = "provider_ref"

// DefaultIdentityMatcher implements 6-tier identity precedence resolution per RFC-024 section 5.1.
type DefaultIdentityMatcher struct{}

// NewDefaultIdentityMatcher creates a new DefaultIdentityMatcher instance.
func NewDefaultIdentityMatcher() *DefaultIdentityMatcher {
	return &DefaultIdentityMatcher{}
}

// MatchDevice evaluates identity precedence in strict order:
// Tier 0: ProviderRef (authoritative workload identity)
// Tier 1: Primary MAC Address
// Tier 2: Provider UUID (legacy Proxmox VM ID / Docker Container ID)
// Tier 3: Hardware Serial Number
// Tier 4: Hostname + IP Address
// Tier 5: No Match (returns nil DeviceID)
func (m *DefaultIdentityMatcher) MatchDevice(norm *dto.NormalizedDeviceDTO, activeDevices []db.Device) MatchResult {
	if norm == nil {
		return MatchResult{DeviceID: nil, MatchedBy: ""}
	}

	normMAC := strings.ToLower(strings.TrimSpace(norm.MACAddress))
	normSerial := strings.TrimSpace(norm.SerialNumber)
	normHost := strings.ToLower(strings.TrimSpace(norm.Hostname))
	normIP := strings.TrimSpace(norm.IPAddress)
	normProviderUUID := strings.TrimSpace(norm.ProviderUUID)

	// Tier 0: ProviderRef match. It outranks the MAC address because a hypervisor or
	// container engine is authoritative about the workloads it owns: a recreated container
	// keeps its identity here while its MAC and IP churn, and a workload that has neither
	// (a stopped container, a VM without a guest agent) can be matched at all.
	if norm.ProviderRef != nil && !norm.ProviderRef.IsZero() {
		refKey := norm.ProviderRef.Key()
		for i := range activeDevices {
			dev := &activeDevices[i]
			if deviceProviderRefKey(dev) == refKey {
				return MatchResult{DeviceID: &dev.ID, MatchedBy: "provider_ref"}
			}
		}
	}

	// Tier 1: MAC Address match
	if normMAC != "" {
		parsedNormMAC, err := net.ParseMAC(normMAC)
		if err == nil {
			for i := range activeDevices {
				dev := &activeDevices[i]
				if len(dev.MacAddress) > 0 && strings.EqualFold(dev.MacAddress.String(), parsedNormMAC.String()) {
					return MatchResult{DeviceID: &dev.ID, MatchedBy: "mac_address"}
				}
			}
		}
	}

	// Tier 2: Provider UUID match (accepts string or numeric JSON values)
	if normProviderUUID != "" {
		for i := range activeDevices {
			dev := &activeDevices[i]
			if len(dev.Metadata) > 0 {
				var meta map[string]interface{}
				if err := json.Unmarshal(dev.Metadata, &meta); err == nil {
					if val, exists := extractProviderUUID(meta); exists && val == normProviderUUID {
						return MatchResult{DeviceID: &dev.ID, MatchedBy: "provider_uuid"}
					}
				}
			}
		}
	}

	// Tier 3: Serial Number match
	if normSerial != "" {
		for i := range activeDevices {
			dev := &activeDevices[i]
			if dev.SerialNumber.Valid && strings.EqualFold(dev.SerialNumber.String, normSerial) {
				return MatchResult{DeviceID: &dev.ID, MatchedBy: "serial_number"}
			}
		}
	}

	// Tier 4: Hostname + IP Address match
	if normHost != "" && normIP != "" {
		normIPAddr, normIPErr := netip.ParseAddr(normIP)
		for i := range activeDevices {
			dev := &activeDevices[i]
			if strings.EqualFold(dev.Hostname, normHost) && dev.IpAddress != nil {
				ipMatches := false
				if normIPErr == nil {
					ipMatches = (*dev.IpAddress == normIPAddr)
				} else {
					ipMatches = (dev.IpAddress.String() == normIP)
				}
				if ipMatches {
					return MatchResult{DeviceID: &dev.ID, MatchedBy: "hostname_ip"}
				}
			}
		}
	}

	// Tier 5: No Match
	return MatchResult{DeviceID: nil, MatchedBy: ""}
}

// deviceProviderRefKey reads the canonical ProviderRef stored on a device, returning an
// empty string for devices discovered by network sweeps, which carry no provider identity.
func deviceProviderRefKey(dev *db.Device) string {
	if len(dev.Metadata) == 0 {
		return ""
	}
	var meta map[string]interface{}
	if err := json.Unmarshal(dev.Metadata, &meta); err != nil {
		return ""
	}
	key, _ := meta[DeviceMetadataProviderRefKey].(string)
	return strings.TrimSpace(key)
}

func extractProviderUUID(metadata map[string]interface{}) (string, bool) {
	if proxmox, ok := metadata["proxmox"].(map[string]interface{}); ok {
		if val, exists := proxmox["vm_id"]; exists && val != nil {
			return strings.TrimSpace(fmt.Sprintf("%v", val)), true
		}
	}
	if docker, ok := metadata["docker"].(map[string]interface{}); ok {
		if val, exists := docker["container_id"]; exists && val != nil {
			return strings.TrimSpace(fmt.Sprintf("%v", val)), true
		}
	}
	return "", false
}
