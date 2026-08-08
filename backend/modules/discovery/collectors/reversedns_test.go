package collectors_test

import (
	"context"
	"testing"

	"github.com/matheussouza/inframap/modules/discovery/collectors"
)


// fakeDNSResolver implements collectors.DNSResolver for testing.
type fakeDNSResolver struct {
	results map[string][]string
	err     error
}

func (f *fakeDNSResolver) LookupAddr(_ context.Context, addr string) ([]string, error) {
	if f.err != nil {
		return nil, f.err
	}
	names, ok := f.results[addr]
	if !ok {
		return nil, &noSuchHostError{addr: addr}
	}
	return names, nil
}

type noSuchHostError struct {
	addr string
}

func (e *noSuchHostError) Error() string {
	return "no such host: " + e.addr
}

func TestReverseDNSCollector_IDAndName(t *testing.T) {
	c := collectors.NewReverseDNSCollector(&fakeDNSResolver{})
	if c.ID() != "reverse-dns" {
		t.Errorf("expected ID 'reverse-dns', got %q", c.ID())
	}
	if c.Name() != "Reverse DNS Resolver" {
		t.Errorf("expected Name 'Reverse DNS Resolver', got %q", c.Name())
	}
}

func TestReverseDNSCollector_Collect(t *testing.T) {
	t.Run("Returns hostname observations for IPs with PTR records", func(t *testing.T) {
		resolver := &fakeDNSResolver{
			results: map[string][]string{
				"192.168.1.1": {"gateway.local."},
				"192.168.1.2": {"server.local."},
			},
		}
		c := collectors.NewReverseDNSCollector(resolver)
		target := collectors.DiscoveryTarget{CIDR: "192.168.1.0/30", SubnetID: "sub-1"}

		obs, err := c.Collect(context.Background(), target)
		if err != nil {
			t.Fatalf("unexpected error: %v", err)
		}
		if len(obs) != 2 {
			t.Fatalf("expected 2 observations, got %d", len(obs))
		}

		// Verify observations have correct fields
		found := make(map[string]string) // ip -> hostname
		for _, o := range obs {
			found[o.IPAddress] = o.Hostname
			if o.ProtocolSource != "reverse-dns" {
				t.Errorf("expected ProtocolSource 'reverse-dns', got %q", o.ProtocolSource)
			}
			if o.ConfidenceScore != 30 {
				t.Errorf("expected ConfidenceScore 30, got %d", o.ConfidenceScore)
			}
			if o.ObservedAt.IsZero() {
				t.Error("ObservedAt should not be zero")
			}
		}
		if found["192.168.1.1"] != "gateway.local." {
			t.Errorf("expected hostname 'gateway.local.' for 192.168.1.1, got %q", found["192.168.1.1"])
		}
		if found["192.168.1.2"] != "server.local." {
			t.Errorf("expected hostname 'server.local.' for 192.168.1.2, got %q", found["192.168.1.2"])
		}
	})

	t.Run("Skips IPs without PTR records", func(t *testing.T) {
		resolver := &fakeDNSResolver{
			results: map[string][]string{
				"192.168.1.1": {"gateway.local."},
				// 192.168.1.2 has no PTR
			},
		}
		c := collectors.NewReverseDNSCollector(resolver)
		target := collectors.DiscoveryTarget{CIDR: "192.168.1.0/30", SubnetID: "sub-1"}

		obs, err := c.Collect(context.Background(), target)
		if err != nil {
			t.Fatalf("unexpected error: %v", err)
		}
		if len(obs) != 1 {
			t.Fatalf("expected 1 observation (only IPs with PTR), got %d", len(obs))
		}
		if obs[0].IPAddress != "192.168.1.1" {
			t.Errorf("expected IP 192.168.1.1, got %s", obs[0].IPAddress)
		}
	})

	t.Run("Returns empty for no PTR records at all", func(t *testing.T) {
		resolver := &fakeDNSResolver{results: map[string][]string{}}
		c := collectors.NewReverseDNSCollector(resolver)
		target := collectors.DiscoveryTarget{CIDR: "192.168.1.1/32", SubnetID: "sub-1"}

		obs, err := c.Collect(context.Background(), target)
		if err != nil {
			t.Fatalf("unexpected error: %v", err)
		}
		if len(obs) != 0 {
			t.Fatalf("expected 0 observations, got %d", len(obs))
		}
	})

	t.Run("Handles invalid CIDR", func(t *testing.T) {
		resolver := &fakeDNSResolver{}
		c := collectors.NewReverseDNSCollector(resolver)
		target := collectors.DiscoveryTarget{CIDR: "invalid", SubnetID: "sub-1"}

		_, err := c.Collect(context.Background(), target)
		if err == nil {
			t.Fatal("expected error for invalid CIDR, got nil")
		}
	})

	t.Run("Rejects oversized CIDR", func(t *testing.T) {
		resolver := &fakeDNSResolver{}
		c := collectors.NewReverseDNSCollector(resolver)
		target := collectors.DiscoveryTarget{CIDR: "10.0.0.0/8", SubnetID: "sub-1"}

		_, err := c.Collect(context.Background(), target)
		if err == nil {
			t.Fatal("expected error for oversized CIDR, got nil")
		}
	})

	t.Run("Respects context cancellation", func(t *testing.T) {
		ctx, cancel := context.WithCancel(context.Background())
		cancel() // cancel immediately

		resolver := &fakeDNSResolver{
			results: map[string][]string{
				"192.168.1.1": {"gateway.local."},
			},
		}
		c := collectors.NewReverseDNSCollector(resolver)
		target := collectors.DiscoveryTarget{CIDR: "192.168.1.1/32", SubnetID: "sub-1"}

		_, err := c.Collect(ctx, target)
		if err == nil {
			t.Fatal("expected context cancellation error, got nil")
		}
	})

	t.Run("Uses first PTR name when multiple exist", func(t *testing.T) {
		resolver := &fakeDNSResolver{
			results: map[string][]string{
				"192.168.1.1": {"primary.local.", "alias.local."},
			},
		}
		c := collectors.NewReverseDNSCollector(resolver)
		target := collectors.DiscoveryTarget{CIDR: "192.168.1.1/32", SubnetID: "sub-1"}

		obs, err := c.Collect(context.Background(), target)
		if err != nil {
			t.Fatalf("unexpected error: %v", err)
		}
		if len(obs) != 1 {
			t.Fatalf("expected 1 observation, got %d", len(obs))
		}
		if obs[0].Hostname != "primary.local." {
			t.Errorf("expected first PTR name 'primary.local.', got %q", obs[0].Hostname)
		}
	})
}
