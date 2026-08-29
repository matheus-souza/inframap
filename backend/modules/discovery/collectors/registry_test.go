package collectors_test

import (
	"context"
	"errors"
	"testing"

	"github.com/matheussouza/inframap/modules/discovery/collectors"
)

type dummyCollector struct {
	id   string
	name string
}

func (d *dummyCollector) ID() string   { return d.id }
func (d *dummyCollector) Name() string { return d.name }
func (d *dummyCollector) Collect(_ context.Context, _ collectors.DiscoveryTarget) ([]collectors.RawObservation, error) {
	return nil, nil
}

func TestCanonicalType(t *testing.T) {
	tests := []struct {
		input    string
		expected string
	}{
		{"icmp_sweep", "icmp"},
		{"ICMP_SWEEP", "icmp"},
		{"icmp", "icmp"},
		{"arp_sweep", "arp"},
		{"arp", "arp"},
		{"reverse_dns", "reversedns"},
		{"reversedns", "reversedns"},
		{"reverse-dns", "reversedns"},
		{"snmp", "snmp"},
		{"mdns", "mdns"},
		{"proxmox", "proxmox"},
		{"docker", "docker"},
		{"unifi", "unifi"},
		{"unknown_type", "unknown_type"},
	}

	for _, tt := range tests {
		got := collectors.CanonicalType(tt.input)
		if got != tt.expected {
			t.Errorf("CanonicalType(%q) = %q, expected %q", tt.input, got, tt.expected)
		}
	}
}

func TestIsImplemented(t *testing.T) {
	implemented := []string{
		"icmp_sweep", "icmp",
		"arp_sweep", "arp",
		"reverse_dns", "reversedns", "reverse-dns",
		"snmp",
	}

	for _, name := range implemented {
		if !collectors.IsImplemented(name) {
			t.Errorf("expected IsImplemented(%q) = true, got false", name)
		}
	}

	unimplemented := []string{
		"mdns", "proxmox", "docker", "unifi", "unknown_type",
	}

	for _, name := range unimplemented {
		if collectors.IsImplemented(name) {
			t.Errorf("expected IsImplemented(%q) = false, got true", name)
		}
	}
}

func TestRegistry(t *testing.T) {
	t.Run("Register and Get by ID and Alias", func(t *testing.T) {
		reg := collectors.NewRegistry()

		c1 := &dummyCollector{id: "icmp", name: "ICMP Collector"}
		c2 := &dummyCollector{id: "reverse-dns", name: "Reverse DNS Collector"}

		if err := reg.Register(c1); err != nil {
			t.Fatalf("unexpected register error: %v", err)
		}
		if err := reg.Register(c2); err != nil {
			t.Fatalf("unexpected register error: %v", err)
		}

		// Lookup by direct ID
		got1, ok := reg.Get("icmp")
		if !ok || got1 != c1 {
			t.Errorf("expected to find c1 by 'icmp'")
		}

		// Lookup by alias / canonical type
		got1Sweep, ok := reg.Get("icmp_sweep")
		if !ok || got1Sweep != c1 {
			t.Errorf("expected to find c1 by 'icmp_sweep'")
		}

		got2Direct, ok := reg.Get("reverse-dns")
		if !ok || got2Direct != c2 {
			t.Errorf("expected to find c2 by 'reverse-dns'")
		}

		got2Alias, ok := reg.Get("reverse_dns")
		if !ok || got2Alias != c2 {
			t.Errorf("expected to find c2 by 'reverse_dns'")
		}

		got2Canonical, ok := reg.Get("reversedns")
		if !ok || got2Canonical != c2 {
			t.Errorf("expected to find c2 by 'reversedns'")
		}

		// Non-existent collector
		_, notFound := reg.Get("nonexistent")
		if notFound {
			t.Errorf("expected notFound for 'nonexistent'")
		}
	})

	t.Run("Register errors on nil or empty ID", func(t *testing.T) {
		reg := collectors.NewRegistry()

		if err := reg.Register(nil); !errors.Is(err, collectors.ErrNilCollector) {
			t.Errorf("expected ErrNilCollector, got %v", err)
		}

		if err := reg.Register(&dummyCollector{id: "  "}); !errors.Is(err, collectors.ErrEmptyCollectorID) {
			t.Errorf("expected ErrEmptyCollectorID, got %v", err)
		}
	})

	t.Run("List returns deduplicated registered collectors", func(t *testing.T) {
		reg := collectors.NewRegistry()

		c1 := &dummyCollector{id: "icmp", name: "ICMP Collector"}
		c2 := &dummyCollector{id: "arp", name: "ARP Collector"}

		_ = reg.Register(c1)
		_ = reg.Register(c2)

		list := reg.List()
		if len(list) != 2 {
			t.Errorf("expected 2 unique collectors in List, got %d", len(list))
		}
	})
}
