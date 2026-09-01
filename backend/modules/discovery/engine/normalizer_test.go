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

func TestNormalizeObservation_SyntheticHostname(t *testing.T) {
	tests := []struct {
		name     string
		obs      collectors.RawObservation
		expected string
	}{
		{
			name: "unnamed workload is named after its host, kind and native id",
			obs: collectors.RawObservation{
				ProviderRef:       &collectors.ProviderRef{Provider: "proxmox", Scope: "pve-cluster1", Kind: "qemu", NativeID: "101"},
				ParentProviderRef: &collectors.ProviderRef{Provider: "proxmox", Scope: "pve-cluster1", Kind: "node", NativeID: "pve-node1"},
			},
			expected: "pve-node1/qemu/101",
		},
		{
			name: "falls back to the scope when there is no parent",
			obs: collectors.RawObservation{
				ProviderRef: &collectors.ProviderRef{Provider: "docker", Scope: "lab-cluster", Kind: "engine", NativeID: "daemon-1"},
			},
			expected: "lab-cluster/engine/daemon-1",
		},
		{
			name: "a declared hostname is never overwritten",
			obs: collectors.RawObservation{
				Hostname:    "Web-01",
				ProviderRef: &collectors.ProviderRef{Provider: "proxmox", Scope: "pve", Kind: "qemu", NativeID: "101"},
			},
			expected: "web-01",
		},
		{
			name:     "observations without a provider identity stay unnamed",
			obs:      collectors.RawObservation{IPAddress: "192.168.1.10"},
			expected: "",
		},
	}

	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			if got := engine.NormalizeObservation(tt.obs).Hostname; got != tt.expected {
				t.Errorf("Hostname = %q, want %q", got, tt.expected)
			}
		})
	}
}

func TestNormalizeObservation_SyntheticHostnameIsDeterministic(t *testing.T) {
	obs := collectors.RawObservation{
		ProviderRef:       &collectors.ProviderRef{Provider: "proxmox", Scope: "pve", Kind: "lxc", NativeID: "201"},
		ParentProviderRef: &collectors.ProviderRef{Provider: "proxmox", Scope: "pve", Kind: "node", NativeID: "pve-node2"},
	}

	first := engine.NormalizeObservation(obs).Hostname
	for i := 0; i < 5; i++ {
		if got := engine.NormalizeObservation(obs).Hostname; got != first {
			t.Fatalf("synthetic hostname is not stable: %q then %q", first, got)
		}
	}
}
