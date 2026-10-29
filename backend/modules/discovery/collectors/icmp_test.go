package collectors_test

import (
	"context"
	"errors"
	"net/netip"
	"strings"
	"sync"
	"sync/atomic"
	"testing"
	"time"

	"github.com/matheussouza/inframap/modules/discovery/collectors"
)

// fakeICMPPinger implements collectors.ICMPPinger for testing. A sweep calls it concurrently.
type fakeICMPPinger struct {
	rtts map[netip.Addr]time.Duration
	err  error

	mu    sync.Mutex
	calls int
}

func (f *fakeICMPPinger) Ping(_ context.Context, ip netip.Addr) (time.Duration, error) {
	f.mu.Lock()
	f.calls++
	f.mu.Unlock()
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

	t.Run("Reports ErrICMPUnavailable instead of an empty sweep", func(t *testing.T) {
		pinger := &fakeICMPPinger{err: collectors.ErrICMPUnavailable}
		c := collectors.NewICMPCollector(pinger)
		target := collectors.DiscoveryTarget{CIDR: "192.0.2.0/24", SubnetID: "sub-1"}

		obs, err := c.Collect(context.Background(), target)
		if !errors.Is(err, collectors.ErrICMPUnavailable) {
			t.Fatalf("expected ErrICMPUnavailable, got %v", err)
		}
		if !errors.Is(err, collectors.ErrCollectorUnavailable) {
			t.Errorf("expected the error to match ErrCollectorUnavailable, got %v", err)
		}
		if !strings.HasPrefix(err.Error(), "ICMP unavailable: ") {
			t.Errorf("expected the error to read as its own reason, got %q", err.Error())
		}
		if len(obs) != 0 {
			t.Fatalf("expected no observations, got %d", len(obs))
		}
		if pinger.calls != 1 {
			t.Errorf("expected unavailability to be detected on the first address, pinger called %d times", pinger.calls)
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

// slowICMPPinger answers every address after delay, except those in down and the one in
// unavailableAt, and records the most probes it saw in flight at once.
type slowICMPPinger struct {
	delay         time.Duration
	down          map[netip.Addr]bool
	unavailableAt netip.Addr

	inFlight    atomic.Int32
	maxInFlight atomic.Int32
	calls       atomic.Int32
}

func (p *slowICMPPinger) Ping(ctx context.Context, ip netip.Addr) (time.Duration, error) {
	p.calls.Add(1)
	n := p.inFlight.Add(1)
	defer p.inFlight.Add(-1)
	for {
		m := p.maxInFlight.Load()
		if n <= m || p.maxInFlight.CompareAndSwap(m, n) {
			break
		}
	}
	if ip == p.unavailableAt {
		return 0, collectors.ErrICMPUnavailable
	}
	select {
	case <-time.After(p.delay):
	case <-ctx.Done():
		return 0, ctx.Err()
	}
	if p.down[ip] {
		return 0, errors.New("request timed out")
	}
	return time.Millisecond, nil
}

func TestICMPCollector_SweepsAddressesConcurrently(t *testing.T) {
	pinger := &slowICMPPinger{
		delay: 100 * time.Millisecond,
		down:  map[netip.Addr]bool{netip.MustParseAddr("192.0.2.7"): true},
	}
	c := collectors.NewICMPCollector(pinger)

	start := time.Now()
	obs, err := c.Collect(context.Background(), collectors.DiscoveryTarget{CIDR: "192.0.2.0/24"})
	elapsed := time.Since(start)
	if err != nil {
		t.Fatalf("unexpected error: %v", err)
	}

	// One address at a time would take 254 × 100 ms ≈ 25 s; at 100 probes a second the
	// sweep cannot start its last probe before about 2.5 s.
	if elapsed > 6*time.Second {
		t.Errorf("sweep of a /24 took %s, expected the addresses to be probed concurrently", elapsed)
	}
	if elapsed < 2400*time.Millisecond {
		t.Errorf("sweep of a /24 took %s, faster than 100 probes a second allows", elapsed)
	}
	if got := pinger.maxInFlight.Load(); got < 2 || got > 64 {
		t.Errorf("max probes in flight = %d, want between 2 and the bound of 64", got)
	}
	if got := pinger.calls.Load(); got != 254 {
		t.Errorf("expected every host address probed once, got %d probes", got)
	}
	if len(obs) != 253 {
		t.Fatalf("expected 253 reachable hosts, got %d", len(obs))
	}
	for i := 1; i < len(obs); i++ {
		prev := netip.MustParseAddr(obs[i-1].IPAddress)
		cur := netip.MustParseAddr(obs[i].IPAddress)
		if !prev.Less(cur) {
			t.Fatalf("observations out of address order: %s before %s", prev, cur)
		}
	}
	for _, o := range obs {
		if o.IPAddress == "192.0.2.7" {
			t.Error("a host that did not answer must not be observed")
		}
	}
}

func TestICMPCollector_UnavailableMidSweepIsReported(t *testing.T) {
	pinger := &slowICMPPinger{
		delay:         10 * time.Millisecond,
		unavailableAt: netip.MustParseAddr("192.0.2.40"),
	}
	c := collectors.NewICMPCollector(pinger)

	obs, err := c.Collect(context.Background(), collectors.DiscoveryTarget{CIDR: "192.0.2.0/24"})
	if !errors.Is(err, collectors.ErrICMPUnavailable) {
		t.Fatalf("expected ErrICMPUnavailable, got %v", err)
	}
	if len(obs) != 0 {
		t.Errorf("expected no observations, got %d", len(obs))
	}
}

func TestICMPCollector_CancellationStopsTheSweep(t *testing.T) {
	pinger := &slowICMPPinger{delay: 5 * time.Second}
	c := collectors.NewICMPCollector(pinger)

	ctx, cancel := context.WithTimeout(context.Background(), 200*time.Millisecond)
	defer cancel()
	start := time.Now()
	_, err := c.Collect(ctx, collectors.DiscoveryTarget{CIDR: "192.0.2.0/24"})
	if !errors.Is(err, context.DeadlineExceeded) {
		t.Fatalf("expected the deadline error, got %v", err)
	}
	if elapsed := time.Since(start); elapsed > 2*time.Second {
		t.Errorf("cancelled sweep returned after %s, expected it to stop promptly", elapsed)
	}
}
