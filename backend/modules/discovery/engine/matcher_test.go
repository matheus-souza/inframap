package engine_test

import (
	"encoding/json"
	"net"
	"net/netip"
	"testing"

	"github.com/google/uuid"
	"github.com/jackc/pgx/v5/pgtype"
	"github.com/matheussouza/inframap/internal/platform/db"
	"github.com/matheussouza/inframap/modules/discovery/dto"
	"github.com/matheussouza/inframap/modules/discovery/engine"
)

func TestIdentityMatcher_MatchDevice(t *testing.T) {
	matcher := engine.NewDefaultIdentityMatcher()

	devID1 := uuid.New()
	devID2 := uuid.New()

	mac1, _ := net.ParseMAC("aa:bb:cc:11:22:33")
	ip1, _ := netip.ParseAddr("192.168.1.10")
	ip2, _ := netip.ParseAddr("192.168.1.20")
	meta1, _ := json.Marshal(map[string]interface{}{
		"proxmox": map[string]interface{}{"vm_id": "100"},
	})

	activeDevices := []db.Device{
		{
			ID:         devID1,
			Hostname:   "pve-node-01",
			MacAddress: mac1,
			IpAddress:  &ip1,
			Metadata:   meta1,
		},
		{
			ID:           devID2,
			Hostname:     "docker-host",
			SerialNumber: pgtype.Text{String: "SN-99887766", Valid: true},
			IpAddress:    &ip2,
		},
	}

	t.Run("Tier 1: Match by MAC Address", func(t *testing.T) {
		norm := &dto.NormalizedDeviceDTO{
			MACAddress: "AA:BB:CC:11:22:33",
			Hostname:   "different-hostname",
		}
		res := matcher.MatchDevice(norm, activeDevices)
		if res.DeviceID == nil || *res.DeviceID != devID1 {
			t.Errorf("expected match with devID1, got %v", res.DeviceID)
		}
		if res.MatchedBy != "mac_address" {
			t.Errorf("expected matchedBy mac_address, got %s", res.MatchedBy)
		}
	})

	t.Run("Tier 2: Match by Provider UUID", func(t *testing.T) {
		norm := &dto.NormalizedDeviceDTO{
			ProviderUUID: "100",
		}
		res := matcher.MatchDevice(norm, activeDevices)
		if res.DeviceID == nil || *res.DeviceID != devID1 {
			t.Errorf("expected match with devID1, got %v", res.DeviceID)
		}
		if res.MatchedBy != "provider_uuid" {
			t.Errorf("expected matchedBy provider_uuid, got %s", res.MatchedBy)
		}
	})

	t.Run("Tier 3: Match by Serial Number", func(t *testing.T) {
		norm := &dto.NormalizedDeviceDTO{
			SerialNumber: "SN-99887766",
		}
		res := matcher.MatchDevice(norm, activeDevices)
		if res.DeviceID == nil || *res.DeviceID != devID2 {
			t.Errorf("expected match with devID2, got %v", res.DeviceID)
		}
		if res.MatchedBy != "serial_number" {
			t.Errorf("expected matchedBy serial_number, got %s", res.MatchedBy)
		}
	})

	t.Run("Tier 4: Match by Hostname + IP Address", func(t *testing.T) {
		norm := &dto.NormalizedDeviceDTO{
			Hostname:  "docker-host",
			IPAddress: "192.168.1.20",
		}
		res := matcher.MatchDevice(norm, activeDevices)
		if res.DeviceID == nil || *res.DeviceID != devID2 {
			t.Errorf("expected match with devID2, got %v", res.DeviceID)
		}
		if res.MatchedBy != "hostname_ip" {
			t.Errorf("expected matchedBy hostname_ip, got %s", res.MatchedBy)
		}
	})

	t.Run("Tier 5: No Match", func(t *testing.T) {
		norm := &dto.NormalizedDeviceDTO{
			Hostname:   "unknown-device",
			IPAddress:  "10.0.0.99",
			MACAddress: "00:11:22:33:44:55",
		}
		res := matcher.MatchDevice(norm, activeDevices)
		if res.DeviceID != nil {
			t.Errorf("expected nil DeviceID for unknown device, got %v", res.DeviceID)
		}
	})
}
