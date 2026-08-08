package collectors_test

import (
	"context"
	"errors"
	"testing"

	"github.com/matheussouza/inframap/modules/discovery/collectors"
)

// fakeARPReader implements collectors.ARPTableReader for testing.
type fakeARPReader struct {
	entries []collectors.ARPEntry
	err     error
}

func (f *fakeARPReader) ReadEntries(_ context.Context) ([]collectors.ARPEntry, error) {
	return f.entries, f.err
}

func TestARPCollector_IDAndName(t *testing.T) {
	c := collectors.NewARPCollector(&fakeARPReader{})
	if c.ID() != "arp" {
		t.Errorf("expected ID 'arp', got %q", c.ID())
	}
	if c.Name() != "ARP Table Reader" {
		t.Errorf("expected Name 'ARP Table Reader', got %q", c.Name())
	}
}

func TestARPCollector_Collect(t *testing.T) {
	t.Run("Returns observations filtered by target CIDR", func(t *testing.T) {
		reader := &fakeARPReader{
			entries: []collectors.ARPEntry{
				{IPAddress: "192.168.1.10", MACAddress: "aa:bb:cc:dd:ee:01", Interface: "eth0"},
				{IPAddress: "192.168.1.20", MACAddress: "aa:bb:cc:dd:ee:02", Interface: "eth0"},
				{IPAddress: "10.0.0.5", MACAddress: "aa:bb:cc:dd:ee:03", Interface: "eth1"}, // outside CIDR
			},
		}
		c := collectors.NewARPCollector(reader)
		target := collectors.DiscoveryTarget{CIDR: "192.168.1.0/24", SubnetID: "sub-1"}

		obs, err := c.Collect(context.Background(), target)
		if err != nil {
			t.Fatalf("unexpected error: %v", err)
		}
		if len(obs) != 2 {
			t.Fatalf("expected 2 observations, got %d", len(obs))
		}

		// Verify first observation
		if obs[0].IPAddress != "192.168.1.10" {
			t.Errorf("obs[0].IPAddress: expected 192.168.1.10, got %s", obs[0].IPAddress)
		}
		if obs[0].MACAddress != "aa:bb:cc:dd:ee:01" {
			t.Errorf("obs[0].MACAddress: expected aa:bb:cc:dd:ee:01, got %s", obs[0].MACAddress)
		}
		if obs[0].ProtocolSource != "arp" {
			t.Errorf("obs[0].ProtocolSource: expected 'arp', got %q", obs[0].ProtocolSource)
		}
		if obs[0].ConfidenceScore != 40 {
			t.Errorf("obs[0].ConfidenceScore: expected 40, got %d", obs[0].ConfidenceScore)
		}
		if obs[0].ObservedAt.IsZero() {
			t.Error("obs[0].ObservedAt should not be zero")
		}
	})

	t.Run("Returns empty slice when no entries match CIDR", func(t *testing.T) {
		reader := &fakeARPReader{
			entries: []collectors.ARPEntry{
				{IPAddress: "10.0.0.5", MACAddress: "aa:bb:cc:dd:ee:03", Interface: "eth1"},
			},
		}
		c := collectors.NewARPCollector(reader)
		target := collectors.DiscoveryTarget{CIDR: "192.168.1.0/24", SubnetID: "sub-1"}

		obs, err := c.Collect(context.Background(), target)
		if err != nil {
			t.Fatalf("unexpected error: %v", err)
		}
		if len(obs) != 0 {
			t.Fatalf("expected 0 observations, got %d", len(obs))
		}
	})

	t.Run("Propagates reader error", func(t *testing.T) {
		reader := &fakeARPReader{err: errors.New("read failed")}
		c := collectors.NewARPCollector(reader)
		target := collectors.DiscoveryTarget{CIDR: "192.168.1.0/24", SubnetID: "sub-1"}

		_, err := c.Collect(context.Background(), target)
		if err == nil {
			t.Fatal("expected error, got nil")
		}
	})

	t.Run("Handles invalid CIDR gracefully", func(t *testing.T) {
		reader := &fakeARPReader{}
		c := collectors.NewARPCollector(reader)
		target := collectors.DiscoveryTarget{CIDR: "invalid", SubnetID: "sub-1"}

		_, err := c.Collect(context.Background(), target)
		if err == nil {
			t.Fatal("expected error for invalid CIDR, got nil")
		}
	})

	t.Run("Skips entries with unparseable IPs", func(t *testing.T) {
		reader := &fakeARPReader{
			entries: []collectors.ARPEntry{
				{IPAddress: "not-an-ip", MACAddress: "aa:bb:cc:dd:ee:01", Interface: "eth0"},
				{IPAddress: "192.168.1.10", MACAddress: "aa:bb:cc:dd:ee:02", Interface: "eth0"},
			},
		}
		c := collectors.NewARPCollector(reader)
		target := collectors.DiscoveryTarget{CIDR: "192.168.1.0/24", SubnetID: "sub-1"}

		obs, err := c.Collect(context.Background(), target)
		if err != nil {
			t.Fatalf("unexpected error: %v", err)
		}
		if len(obs) != 1 {
			t.Fatalf("expected 1 observation (skipping invalid IP), got %d", len(obs))
		}
	})

	t.Run("Respects context cancellation", func(t *testing.T) {
		ctx, cancel := context.WithCancel(context.Background())
		cancel() // cancel immediately

		reader := &fakeARPReader{
			entries: []collectors.ARPEntry{
				{IPAddress: "192.168.1.10", MACAddress: "aa:bb:cc:dd:ee:01", Interface: "eth0"},
			},
		}
		c := collectors.NewARPCollector(reader)
		target := collectors.DiscoveryTarget{CIDR: "192.168.1.0/24", SubnetID: "sub-1"}

		_, err := c.Collect(ctx, target)
		if err == nil {
			t.Fatal("expected context cancellation error, got nil")
		}
	})
}
