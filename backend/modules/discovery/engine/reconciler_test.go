package engine_test

import (
	"encoding/json"
	"testing"

	"github.com/google/uuid"
	"github.com/matheussouza/inframap/internal/platform/db"
	"github.com/matheussouza/inframap/modules/discovery/dto"
	"github.com/matheussouza/inframap/modules/discovery/engine"
)

func TestFieldReconciler_Reconcile(t *testing.T) {
	reconciler := engine.NewDefaultFieldReconciler()

	t.Run("Higher confidence score updates device fields", func(t *testing.T) {
		meta, _ := json.Marshal(map[string]interface{}{
			"source_confidence_score": 20,
		})
		existing := &db.Device{
			ID:         uuid.New(),
			Hostname:   "generic-host",
			DeviceType: "unknown",
			Metadata:   meta,
		}

		norm := &dto.NormalizedDeviceDTO{
			Hostname:   "pve-node-01",
			DeviceType: "hypervisor",
			RawPayload: map[string]interface{}{"vms": 5},
		}

		updated, changed := reconciler.Reconcile(existing, norm, "proxmox")
		if !changed {
			t.Fatal("expected changed = true")
		}
		if updated.Hostname != "pve-node-01" {
			t.Errorf("expected hostname pve-node-01, got %s", updated.Hostname)
		}
		if updated.DeviceType != "hypervisor" {
			t.Errorf("expected device_type hypervisor, got %s", updated.DeviceType)
		}
	})

	t.Run("User locked field is protected from scan update", func(t *testing.T) {
		meta, _ := json.Marshal(map[string]interface{}{
			"user_locked_fields":      []interface{}{"hostname"},
			"source_confidence_score": 100,
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
			"source_confidence_score": 80,
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
}
