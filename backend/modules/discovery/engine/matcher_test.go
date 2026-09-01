package engine_test

import (
	"encoding/json"
	"net"
	"net/netip"
	"testing"

	"github.com/google/uuid"
	"github.com/jackc/pgx/v5/pgtype"
	"github.com/matheussouza/inframap/internal/platform/db"
	"github.com/matheussouza/inframap/modules/discovery/collectors"
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

	t.Run("Nil Input Returns No Match", func(t *testing.T) {
		res := matcher.MatchDevice(nil, activeDevices)
		if res.DeviceID != nil {
			t.Errorf("expected nil DeviceID, got %v", res.DeviceID)
		}
	})

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

	t.Run("Tier 1: Invalid MAC string ignored", func(t *testing.T) {
		norm := &dto.NormalizedDeviceDTO{
			MACAddress: "invalid-mac",
		}
		res := matcher.MatchDevice(norm, activeDevices)
		if res.DeviceID != nil {
			t.Errorf("expected no match for invalid mac, got %v", res.DeviceID)
		}
	})

	t.Run("Tier 2: Match by Provider UUID (String)", func(t *testing.T) {
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

	t.Run("Tier 2: Match by Provider UUID (Numeric JSON)", func(t *testing.T) {
		metaNum, _ := json.Marshal(map[string]interface{}{
			"proxmox": map[string]interface{}{"vm_id": 100},
		})
		devNumID := uuid.New()
		devicesWithNum := append(activeDevices, db.Device{
			ID:       devNumID,
			Hostname: "pve-num-node",
			Metadata: metaNum,
		})
		norm := &dto.NormalizedDeviceDTO{
			ProviderUUID: "100",
		}
		res := matcher.MatchDevice(norm, devicesWithNum)
		if res.DeviceID == nil {
			t.Fatal("expected non-nil match for numeric vm_id")
		}
		if res.MatchedBy != "provider_uuid" {
			t.Errorf("expected matchedBy provider_uuid, got %s", res.MatchedBy)
		}
	})

	t.Run("Tier 2: Docker Provider UUID Match", func(t *testing.T) {
		metaDocker, _ := json.Marshal(map[string]interface{}{
			"docker": map[string]interface{}{"container_id": "c12345"},
		})
		devDockerID := uuid.New()
		devs := []db.Device{
			{ID: devDockerID, Hostname: "doc-container", Metadata: metaDocker},
		}
		norm := &dto.NormalizedDeviceDTO{ProviderUUID: "c12345"}
		res := matcher.MatchDevice(norm, devs)
		if res.DeviceID == nil || *res.DeviceID != devDockerID {
			t.Errorf("expected match with devDockerID, got %v", res.DeviceID)
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

func TestIdentityMatcher_MatchDevice_Tier0ProviderRef(t *testing.T) {
	matcher := engine.NewDefaultIdentityMatcher()

	containerID := uuid.New()
	otherID := uuid.New()

	staleMAC, _ := net.ParseMAC("02:42:ac:11:00:02")
	containerMeta, _ := json.Marshal(map[string]interface{}{
		"provider_ref": "docker:lab:container:abc123",
		"docker":       map[string]interface{}{"image": "redis:7-alpine"},
	})
	otherMeta, _ := json.Marshal(map[string]interface{}{
		"provider_ref": "docker:lab:container:def456",
	})

	activeDevices := []db.Device{
		{ID: containerID, Hostname: "redis", MacAddress: staleMAC, Metadata: containerMeta},
		{ID: otherID, Hostname: "nginx", Metadata: otherMeta},
	}

	t.Run("matches a recreated container whose MAC has churned", func(t *testing.T) {
		freshMAC, _ := net.ParseMAC("02:42:ac:11:00:99")
		norm := &dto.NormalizedDeviceDTO{
			Hostname:    "redis",
			MACAddress:  freshMAC.String(),
			ProviderRef: &collectors.ProviderRef{Provider: "docker", Scope: "lab", Kind: "container", NativeID: "abc123"},
		}

		res := matcher.MatchDevice(norm, activeDevices)
		if res.DeviceID == nil || *res.DeviceID != containerID {
			t.Fatalf("expected match on %s, got %v", containerID, res.DeviceID)
		}
		if res.MatchedBy != "provider_ref" {
			t.Errorf("MatchedBy = %q, want %q", res.MatchedBy, "provider_ref")
		}
	})

	t.Run("outranks a MAC address belonging to another device", func(t *testing.T) {
		// The stale MAC still sits on the container device, but the observation declares a
		// different workload: the provider is authoritative, so Tier 0 must win.
		norm := &dto.NormalizedDeviceDTO{
			Hostname:    "nginx",
			MACAddress:  staleMAC.String(),
			ProviderRef: &collectors.ProviderRef{Provider: "docker", Scope: "lab", Kind: "container", NativeID: "def456"},
		}

		res := matcher.MatchDevice(norm, activeDevices)
		if res.DeviceID == nil || *res.DeviceID != otherID {
			t.Fatalf("expected Tier 0 to outrank the MAC match, got %v", res.DeviceID)
		}
	})

	t.Run("matches a workload that has neither MAC nor IP", func(t *testing.T) {
		norm := &dto.NormalizedDeviceDTO{
			Hostname:    "redis",
			ProviderRef: &collectors.ProviderRef{Provider: "docker", Scope: "lab", Kind: "container", NativeID: "abc123"},
		}

		res := matcher.MatchDevice(norm, activeDevices)
		if res.DeviceID == nil || *res.DeviceID != containerID {
			t.Fatalf("expected a stopped container to still match, got %v", res.DeviceID)
		}
	})

	t.Run("falls through when the reference is unknown", func(t *testing.T) {
		norm := &dto.NormalizedDeviceDTO{
			Hostname:    "brand-new",
			ProviderRef: &collectors.ProviderRef{Provider: "docker", Scope: "lab", Kind: "container", NativeID: "unseen"},
		}

		if res := matcher.MatchDevice(norm, activeDevices); res.DeviceID != nil {
			t.Errorf("expected no match, got %v matched by %q", res.DeviceID, res.MatchedBy)
		}
	})

	t.Run("ignores a partial reference", func(t *testing.T) {
		// Scope and kind alone carry no identity, so such an observation must not collapse
		// onto an unrelated device.
		norm := &dto.NormalizedDeviceDTO{
			Hostname:    "redis",
			ProviderRef: &collectors.ProviderRef{Scope: "lab", Kind: "container"},
		}

		if res := matcher.MatchDevice(norm, activeDevices); res.MatchedBy == "provider_ref" {
			t.Errorf("expected a partial reference to be ignored, matched %v", res.DeviceID)
		}
	})
}
