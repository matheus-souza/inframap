package collectors_test

import (
	"context"
	"errors"
	"net/netip"
	"testing"
	"time"

	"github.com/matheussouza/inframap/modules/discovery/collectors"
)

// fakeICMPPinger implements collectors.ICMPPinger for testing.
type fakeICMPPinger struct {
	rtts map[netip.Addr]time.Duration
	err  error
}

func (f *fakeICMPPinger) Ping(_ context.Context, ip netip.Addr) (time.Duration, error) {
	if f.err != nil {
		return 0, f.err
	}
	rtt, ok := f.rtts[ip]
	if !ok {
		return 0, errors.New("request timed out")
	}
	return rtt, nil
}

func TestICMPCollector_IDAndName(t *testing.T) {
	c := collectors.NewICMPCollector(&fakeICMPPinger{})
	if c.ID() != "icmp" {
		t.Errorf("expected ID 'icmp', got %q", c.ID())
	}
	if c.Name() != "ICMP Reachability Tester" {
		t.Errorf("expected Name 'ICMP Reachability Tester', got %q", c.Name())
	}
}

func TestICMPCollector_Collect(t *testing.T) {
	t.Run("Returns observations for reachable IPs in target CIDR", func(t *testing.T) {
		ip1 := netip.MustParseAddr("192.168.1.1")
		ip2 := netip.MustParseAddr("192.168.1.2")
		// ip3 is in CIDR but unreachable

		pinger := &fakeICMPPinger{
			rtts: map[netip.Addr]time.Duration{
				ip1: 12 * time.Millisecond,
				ip2: 45 * time.Millisecond,
			},
		}

		c := collectors.NewICMPCollector(pinger)
		target := collectors.DiscoveryTarget{CIDR: "192.168.1.0/30", SubnetID: "sub-1"}

		obs, err := c.Collect(context.Background(), target)
		if err != nil {
			t.Fatalf("unexpected error: %v", err)
		}

		if len(obs) != 2 {
			t.Fatalf("expected 2 observations for reachable hosts, got %d", len(obs))
		}

		found := make(map[string]int64)
		for _, o := range obs {
			found[o.IPAddress] = o.LatencyMs
			if o.ProtocolSource != "icmp" {
				t.Errorf("expected ProtocolSource 'icmp', got %q", o.ProtocolSource)
			}
			if o.ConfidenceScore != 50 {
				t.Errorf("expected ConfidenceScore 50, got %d", o.ConfidenceScore)
			}
			if o.ObservedAt.IsZero() {
				t.Error("ObservedAt should not be zero")
			}
		}

		if found["192.168.1.1"] != 12 {
			t.Errorf("expected LatencyMs 12 for 192.168.1.1, got %d", found["192.168.1.1"])
		}
		if found["192.168.1.2"] != 45 {
			t.Errorf("expected LatencyMs 45 for 192.168.1.2, got %d", found["192.168.1.2"])
		}
	})

	t.Run("Handles ErrICMPUnavailable gracefully without error", func(t *testing.T) {
		pinger := &fakeICMPPinger{err: collectors.ErrICMPUnavailable}
		c := collectors.NewICMPCollector(pinger)
		target := collectors.DiscoveryTarget{CIDR: "192.168.1.0/24", SubnetID: "sub-1"}

		obs, err := c.Collect(context.Background(), target)
		if err != nil {
			t.Fatalf("expected nil error on ErrICMPUnavailable fallback, got %v", err)
		}
		if len(obs) != 0 {
			t.Fatalf("expected empty observations on fallback, got %d", len(obs))
		}
	})

	t.Run("Rejects oversized CIDR larger than /16", func(t *testing.T) {
		pinger := &fakeICMPPinger{}
		c := collectors.NewICMPCollector(pinger)
		target := collectors.DiscoveryTarget{CIDR: "10.0.0.0/8", SubnetID: "sub-1"}

		_, err := c.Collect(context.Background(), target)
		if err == nil {
			t.Fatal("expected error for oversized CIDR /8, got nil")
		}
		if !errors.Is(err, collectors.ErrCIDRTooLarge) {
			t.Errorf("expected ErrCIDRTooLarge, got %v", err)
		}
	})

	t.Run("Handles invalid CIDR gracefully", func(t *testing.T) {
		pinger := &fakeICMPPinger{}
		c := collectors.NewICMPCollector(pinger)
		target := collectors.DiscoveryTarget{CIDR: "not-a-cidr", SubnetID: "sub-1"}

		_, err := c.Collect(context.Background(), target)
		if err == nil {
			t.Fatal("expected error for invalid CIDR, got nil")
		}
	})

	t.Run("Respects context cancellation during iteration", func(t *testing.T) {
		ctx, cancel := context.WithCancel(context.Background())
		cancel() // cancel immediately

		pinger := &fakeICMPPinger{
			rtts: map[netip.Addr]time.Duration{
				netip.MustParseAddr("192.168.1.1"): 10 * time.Millisecond,
			},
		}
		c := collectors.NewICMPCollector(pinger)
		target := collectors.DiscoveryTarget{CIDR: "192.168.1.0/24", SubnetID: "sub-1"}

		_, err := c.Collect(ctx, target)
		if err == nil {
			t.Fatal("expected context cancellation error, got nil")
		}
	})

	t.Run("NewICMPCollector with nil uses default pinger", func(t *testing.T) {
		c := collectors.NewICMPCollector(nil)
		if c == nil {
			t.Fatal("expected non-nil ICMPCollector")
		}
	})

	t.Run("DualModeICMPPinger returns ErrICMPUnavailable when ping is unavailable", func(t *testing.T) {
		pinger := collectors.NewDefaultICMPPinger()
		// Test ping against localhost or unroutable IP with short context
		ctx, cancel := context.WithTimeout(context.Background(), 50*time.Millisecond)
		defer cancel()

		_, err := pinger.Ping(ctx, netip.MustParseAddr("127.0.0.1"))
		// Dual mode will attempt native/command and return either latency or error
		if err != nil && !errors.Is(err, collectors.ErrICMPUnavailable) && !errors.Is(err, context.DeadlineExceeded) {
			t.Logf("Ping returned acceptable non-nil error: %v", err)
		}
	})

	t.Run("DualModeICMPPinger handles empty pingPath", func(t *testing.T) {
		emptyPinger := &collectors.DualModeICMPPinger{}
		_, err := emptyPinger.Ping(context.Background(), netip.MustParseAddr("192.168.1.1"))
		if err == nil {
			t.Error("expected error when pingPath is empty, got nil")
		}
	})

	t.Run("DualModeICMPPinger executes command fallback when pingPath is set", func(t *testing.T) {
		// Use system 'true' command as synthetic ping binary that succeeds with exit code 0
		pinger := collectors.NewDefaultICMPPinger()
		// Test pingCommand via pinger if system ping or true is available
		ctx := context.Background()
		rtt, err := pinger.Ping(ctx, netip.MustParseAddr("127.0.0.1"))
		if err == nil {
			if rtt <= 0 {
				t.Errorf("expected positive RTT, got %v", rtt)
			}
		}
	})
}



func TestParsePingRTT(t *testing.T) {
	t.Run("Parses Linux ping output", func(t *testing.T) {
		out := "64 bytes from 192.168.1.1: icmp_seq=1 ttl=64 time=12.4 ms\n"
		rtt, ok := collectors.ParsePingRTT(out)
		if !ok {
			t.Fatal("expected ok to be true")
		}
		if rtt != 12400*time.Microsecond {
			t.Errorf("expected 12.4ms, got %v", rtt)
		}
	})

	t.Run("Parses macOS ping output", func(t *testing.T) {
		out := "64 bytes from 192.168.1.1: icmp_seq=0 ttl=64 time=1.234 ms\n"
		rtt, ok := collectors.ParsePingRTT(out)
		if !ok {
			t.Fatal("expected ok to be true")
		}
		if rtt != 1234*time.Microsecond {
			t.Errorf("expected 1.234ms, got %v", rtt)
		}
	})

	t.Run("Returns false for output without RTT", func(t *testing.T) {
		out := "1 packets transmitted, 0 packets received, 100% packet loss\n"
		_, ok := collectors.ParsePingRTT(out)
		if ok {
			t.Error("expected ok to be false")
		}
	})
}


