package engine_test

import (
	"testing"
	"time"

	"github.com/matheussouza/inframap/modules/discovery/collectors"
	"github.com/matheussouza/inframap/modules/discovery/engine"
)

func TestNormalizer_NormalizeObservation(t *testing.T) {
	now := time.Now()
	raw := collectors.RawObservation{
		IPAddress:       "  192.168.1.50  ",
		MACAddress:      "AA-BB-CC-11-22-33",
		Hostname:        "  PVE-NODE.local\n",
		Vendor:          "  Proxmox Server  ",
		OS:              " Linux 6.8 ",
		ProtocolSource:  " snmp ",
		ConfidenceScore: 80,
		ObservedAt:      now,
	}

	norm := engine.NormalizeObservation(raw)

	if norm.IPAddress != "192.168.1.50" {
		t.Errorf("expected IP 192.168.1.50, got %q", norm.IPAddress)
	}
	if norm.MACAddress != "aa:bb:cc:11:22:33" {
		t.Errorf("expected MAC aa:bb:cc:11:22:33, got %q", norm.MACAddress)
	}
	if norm.Hostname != "pve-node.local" {
		t.Errorf("expected Hostname pve-node.local, got %q", norm.Hostname)
	}
	if norm.Vendor != "Proxmox Server" {
		t.Errorf("expected Vendor Proxmox Server, got %q", norm.Vendor)
	}
	if norm.OS != "Linux 6.8" {
		t.Errorf("expected OS Linux 6.8, got %q", norm.OS)
	}
	if norm.ProtocolSource != "snmp" {
		t.Errorf("expected ProtocolSource snmp, got %q", norm.ProtocolSource)
	}
	if norm.ObservedAt.IsZero() {
		t.Errorf("expected non-zero ObservedAt")
	}
}
