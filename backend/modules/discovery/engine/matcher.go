// Package engine implements the identity matching and field reconciliation algorithms for InfraMap.
package engine

import (
	"encoding/json"
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

// DefaultIdentityMatcher implements 5-tier identity precedence resolution per RFC-007.
type DefaultIdentityMatcher struct{}

// NewDefaultIdentityMatcher creates a new DefaultIdentityMatcher instance.
func NewDefaultIdentityMatcher() *DefaultIdentityMatcher {
	return &DefaultIdentityMatcher{}
}

// MatchDevice evaluates identity precedence in strict order:
// Priority 1: Primary MAC Address
// Priority 2: Provider UUID (Proxmox VM ID / Docker Container ID)
// Priority 3: Hardware Serial Number
// Priority 4: Hostname + IP Address
// Priority 5: No Match (returns nil DeviceID)
func (m *DefaultIdentityMatcher) MatchDevice(norm *dto.NormalizedDeviceDTO, activeDevices []db.Device) MatchResult {
	if norm == nil {
		return MatchResult{DeviceID: nil, MatchedBy: ""}
	}

	normMAC := strings.ToLower(strings.TrimSpace(norm.MACAddress))
	normSerial := strings.TrimSpace(norm.SerialNumber)
	normHost := strings.ToLower(strings.TrimSpace(norm.Hostname))
	normIP := strings.TrimSpace(norm.IPAddress)
	normProviderUUID := strings.TrimSpace(norm.ProviderUUID)

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

	// Tier 2: Provider UUID match
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

func extractProviderUUID(metadata map[string]interface{}) (string, bool) {
	if proxmox, ok := metadata["proxmox"].(map[string]interface{}); ok {
		if vmID, exists := proxmox["vm_id"].(string); exists {
			return vmID, true
		}
	}
	if docker, ok := metadata["docker"].(map[string]interface{}); ok {
		if containerID, exists := docker["container_id"].(string); exists {
			return containerID, true
		}
	}
	return "", false
}
