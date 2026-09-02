package engine_test

import (
	"encoding/json"
	"net"
	"net/netip"
	"testing"

	"github.com/google/uuid"
	"github.com/matheussouza/inframap/internal/platform/db"
	"github.com/matheussouza/inframap/modules/discovery/collectors"
	"github.com/matheussouza/inframap/modules/discovery/dto"
	"github.com/matheussouza/inframap/modules/discovery/engine"
)

func TestFieldReconciler_Reconcile(t *testing.T) {
	reconciler := engine.NewDefaultFieldReconciler()

	t.Run("Nil Input Returns Unchanged", func(t *testing.T) {
		res, changed := reconciler.Reconcile(nil, &dto.NormalizedDeviceDTO{}, "proxmox")
		if changed || res != nil {
			t.Error("expected false and nil for nil existing")
		}
		res2, changed2 := reconciler.Reconcile(&db.Device{}, nil, "proxmox")
		if changed2 || res2 == nil {
			t.Error("expected false for nil incoming")
		}
	})

	t.Run("Higher confidence score updates device fields", func(t *testing.T) {
		meta, _ := json.Marshal(map[string]interface{}{
			"field_confidence_scores": map[string]interface{}{
				"hostname":      20,
				"ip_address":    20,
				"mac_address":   20,
				"manufacturer":  20,
				"model":         20,
				"serial_number": 20,
			},
		})
		existing := &db.Device{
			ID:         uuid.New(),
			Hostname:   "generic-host",
			DeviceType: "unknown",
			Metadata:   meta,
		}

		norm := &dto.NormalizedDeviceDTO{
			Hostname:     "pve-node-01",
			IPAddress:    "192.168.1.100",
			MACAddress:   "aa:bb:cc:dd:ee:ff",
			Manufacturer: "Dell",
			Model:        "PowerEdge R740",
			SerialNumber: "SN-990011",
			DeviceType:   "hypervisor",
			RawPayload:   map[string]interface{}{"vms": 5},
		}

		updated, changed := reconciler.Reconcile(existing, norm, "proxmox")
		if !changed {
			t.Fatal("expected changed = true")
		}
		if updated.Hostname != "pve-node-01" {
			t.Errorf("expected hostname pve-node-01, got %s", updated.Hostname)
		}
		if updated.IpAddress == nil || updated.IpAddress.String() != "192.168.1.100" {
			t.Errorf("expected IP 192.168.1.100, got %v", updated.IpAddress)
		}
		if len(updated.MacAddress) == 0 {
			t.Errorf("expected non-empty MAC address")
		}
		if updated.Manufacturer.String != "Dell" || updated.Model.String != "PowerEdge R740" || updated.SerialNumber.String != "SN-990011" {
			t.Errorf("expected Dell PowerEdge R740 SN-990011")
		}
		if updated.DeviceType != "hypervisor" {
			t.Errorf("expected device_type hypervisor, got %s", updated.DeviceType)
		}
	})

	t.Run("Invalid IP and MAC format ignored gracefully", func(t *testing.T) {
		existing := &db.Device{
			ID:       uuid.New(),
			Hostname: "valid-host",
		}
		norm := &dto.NormalizedDeviceDTO{
			IPAddress:  "invalid-ip",
			MACAddress: "invalid-mac",
		}

		updated, changed := reconciler.Reconcile(existing, norm, "unifi")
		if changed {
			t.Error("expected changed = false for invalid IP and MAC formats")
		}
		if updated.IpAddress != nil {
			t.Errorf("expected nil IP for invalid format, got %v", updated.IpAddress)
		}
		if len(updated.MacAddress) != 0 {
			t.Errorf("expected empty MAC for invalid format, got %v", updated.MacAddress)
		}
	})

	t.Run("User locked field is protected from scan update", func(t *testing.T) {
		meta, _ := json.Marshal(map[string]interface{}{
			"user_locked_fields": []interface{}{"hostname"},
			"field_confidence_scores": map[string]interface{}{
				"hostname": 100,
			},
		})
		existing := &db.Device{
			ID:       uuid.New(),
			Hostname: "user-curated-name",
			Metadata: meta,
		}

		norm := &dto.NormalizedDeviceDTO{
			Hostname: "scanner-discovered-name",
		}

		updated, _ := reconciler.Reconcile(existing, norm, "proxmox")
		if updated.Hostname != "user-curated-name" {
			t.Errorf("expected hostname to remain user-curated-name, got %s", updated.Hostname)
		}
	})

	t.Run("Lower confidence score does not overwrite fields", func(t *testing.T) {
		meta, _ := json.Marshal(map[string]interface{}{
			"field_confidence_scores": map[string]interface{}{
				"hostname": 80,
			},
		})
		existing := &db.Device{
			ID:         uuid.New(),
			Hostname:   "authoritative-host",
			DeviceType: "hypervisor",
			Metadata:   meta,
		}

		norm := &dto.NormalizedDeviceDTO{
			Hostname:   "sweep-host",
			DeviceType: "unknown",
		}

		updated, _ := reconciler.Reconcile(existing, norm, "arp_sweep")
		if updated.Hostname != "authoritative-host" {
			t.Errorf("expected hostname to remain authoritative-host, got %s", updated.Hostname)
		}
	})

	t.Run("RawPayload deep merge does not report changed if identical", func(t *testing.T) {
		ip, _ := netip.ParseAddr("192.168.1.100")
		mac, _ := net.ParseMAC("aa:bb:cc:dd:ee:ff")
		meta, _ := json.Marshal(map[string]interface{}{
			"proxmox": map[string]interface{}{"vms": 5.0},
			"field_confidence_scores": map[string]interface{}{
				"hostname": 80, "ip_address": 80, "mac_address": 80,
			},
		})
		existing := &db.Device{
			ID:         uuid.New(),
			Hostname:   "pve-node-01",
			IpAddress:  &ip,
			MacAddress: mac,
			Metadata:   meta,
		}

		norm := &dto.NormalizedDeviceDTO{
			Hostname:   "pve-node-01",
			IPAddress:  "192.168.1.100",
			MACAddress: "aa:bb:cc:dd:ee:ff",
			RawPayload: map[string]interface{}{"vms": 5.0},
		}

		_, changed := reconciler.Reconcile(existing, norm, "proxmox")
		if changed {
			t.Error("expected changed = false when payload and fields are identical")
		}
	})
}

func TestReconcile_PersistsProviderIdentityOnUpdate(t *testing.T) {
	reconciler := engine.NewDefaultFieldReconciler()

	t.Run("adopts the identity of a device first seen by a network sweep", func(t *testing.T) {
		// The device was discovered by ICMP and carries no provider identity. A Proxmox
		// observation then matches it by MAC. Without persisting the reference here, Tier 0
		// could never match it again, and the workload would be recreated as a duplicate the
		// moment it lost its address.
		existingMeta, _ := json.Marshal(map[string]interface{}{"icmp": map[string]interface{}{"seen": true}})
		existing := &db.Device{ID: uuid.New(), Hostname: "vm-101", DeviceType: "host", Metadata: existingMeta}

		incoming := &dto.NormalizedDeviceDTO{
			Hostname:          "vm-101",
			ConfidenceScore:   85,
			ProviderRef:       &collectors.ProviderRef{Provider: "proxmox", Scope: "pve", Kind: "qemu", NativeID: "101"},
			ParentProviderRef: &collectors.ProviderRef{Provider: "proxmox", Scope: "pve", Kind: "node", NativeID: "pve-node1"},
		}

		updated, changed := reconciler.Reconcile(existing, incoming, "proxmox")
		if !changed {
			t.Fatal("expected the device to be marked changed so the identity is persisted")
		}

		var meta map[string]interface{}
		if err := json.Unmarshal(updated.Metadata, &meta); err != nil {
			t.Fatalf("failed to decode reconciled metadata: %v", err)
		}
		if meta["provider_ref"] != "proxmox:pve:qemu:101" {
			t.Errorf("provider_ref = %v, want proxmox:pve:qemu:101", meta["provider_ref"])
		}
		if meta["parent_provider_ref"] != "proxmox:pve:node:pve-node1" {
			t.Errorf("parent_provider_ref = %v, want proxmox:pve:node:pve-node1", meta["parent_provider_ref"])
		}
		if _, kept := meta["icmp"]; !kept {
			t.Error("expected the existing sweep metadata to be preserved")
		}
	})

	t.Run("leaves sweep-only devices without an identity", func(t *testing.T) {
		existing := &db.Device{ID: uuid.New(), Hostname: "printer", DeviceType: "host"}
		incoming := &dto.NormalizedDeviceDTO{Hostname: "printer", ConfidenceScore: 20}

		updated, _ := reconciler.Reconcile(existing, incoming, "icmp")
		if len(updated.Metadata) > 0 {
			var meta map[string]interface{}
			_ = json.Unmarshal(updated.Metadata, &meta)
			if _, present := meta["provider_ref"]; present {
				t.Error("an observation without provider identity must not invent one")
			}
		}
	})
}
