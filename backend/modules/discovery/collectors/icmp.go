package collectors

import (
	"bytes"
	"context"
	"errors"
	"fmt"
	"log/slog"
	"net"
	"net/netip"
	"os/exec"
	"regexp"
	"strconv"
	"sync"
	"time"


	"golang.org/x/net/icmp"
	"golang.org/x/net/ipv4"
)

// ErrICMPUnavailable is returned when neither raw ICMP sockets nor the system ping CLI are available.
var ErrICMPUnavailable = errors.New("ICMP reachability unavailable: raw sockets and system ping binary restricted")

// ICMPPinger abstracts ICMP ping operations for testability and resilience.
type ICMPPinger interface {
	Ping(ctx context.Context, ip netip.Addr) (time.Duration, error)
}

// ICMPCollector tests L3 reachability and measures RTT latency using ICMP.
type ICMPCollector struct {
	pinger ICMPPinger
}

// NewICMPCollector creates a new ICMPCollector with the given pinger.
// If pinger is nil, NewDefaultICMPPinger() is used.
func NewICMPCollector(pinger ICMPPinger) *ICMPCollector {
	if pinger == nil {
		pinger = NewDefaultICMPPinger()
	}
	return &ICMPCollector{pinger: pinger}
}

// ID returns the unique collector identifier.
func (c *ICMPCollector) ID() string { return "icmp" }

// Name returns the human-readable collector name.
func (c *ICMPCollector) Name() string { return "ICMP Reachability Tester" }

// Collect tests reachability for all host IPs within the target CIDR.
// Returns observations for reachable IPs with LatencyMs and ConfidenceScore 50.
func (c *ICMPCollector) Collect(ctx context.Context, target DiscoveryTarget) ([]RawObservation, error) {
	if err := ctx.Err(); err != nil {
		return nil, fmt.Errorf("icmp collector: %w", err)
	}

	addrs, err := ExpandCIDR(target.CIDR)
	if err != nil {
		return nil, fmt.Errorf("icmp collector: %w", err)
	}

	now := time.Now()
	var observations []RawObservation
	var warnOnce sync.Once

	for _, addr := range addrs {
		if err := ctx.Err(); err != nil {
			return nil, fmt.Errorf("icmp collector: %w", err)
		}

		rtt, pingErr := c.pinger.Ping(ctx, addr)
		if pingErr != nil {
			if errors.Is(pingErr, ErrICMPUnavailable) {
				warnOnce.Do(func() {
					slog.Warn("ICMP reachability check skipped: neither raw sockets nor system ping binary available in environment")
				})
				return nil, nil // Graceful bypass for restricted/distroless environments
			}
			continue // Unreachable host or request timeout
		}

		observations = append(observations, RawObservation{
			IPAddress:       addr.String(),
			LatencyMs:       rtt.Milliseconds(),
			ProtocolSource:  "icmp",
			ConfidenceScore: 50,
			ObservedAt:      now,
		})
	}

	return observations, nil
}

// PacketConn abstracts net.PacketConn / icmp.PacketConn for unit testing.
type PacketConn interface {
	SetDeadline(t time.Time) error
	WriteTo(b []byte, dst net.Addr) (int, error)
	ReadFrom(b []byte) (int, net.Addr, error)
	Close() error
}

// DualModeICMPPinger implements ICMPPinger with 3-level fallback:
// 1. Native ICMP Socket (golang.org/x/net/icmp)
// 2. System Ping CLI (exec.CommandContext)
// 3. ErrICMPUnavailable
type DualModeICMPPinger struct {
	pingPath         string
	listenPacketFunc func(network, address string) (PacketConn, error)
}

// NewDefaultICMPPinger creates a DualModeICMPPinger.
func NewDefaultICMPPinger() *DualModeICMPPinger {
	pingPath, _ := exec.LookPath("ping")
	return &DualModeICMPPinger{
		pingPath: pingPath,
		listenPacketFunc: func(network, address string) (PacketConn, error) {
			c, err := icmp.ListenPacket(network, address)
			if err != nil {
				return nil, err
			}
			return c, nil
		},
	}
}

// Ping attempts a native ICMP socket first, then falls back to system ping CLI.
func (p *DualModeICMPPinger) Ping(ctx context.Context, ip netip.Addr) (time.Duration, error) {
	// Level 1: Attempt native ICMP socket
	rtt, err := p.pingNative(ctx, ip)
	if err == nil {
		return rtt, nil
	}

	// If native fails due to permission error (or is unsupported), attempt Level 2 fallback
	if p.pingPath != "" {
		rtt, cmdErr := p.pingCommand(ctx, ip)
		if cmdErr == nil {
			return rtt, nil
		}
		if ctx.Err() != nil {
			return 0, ctx.Err()
		}
	}

	// Level 3: Neither worked or available
	return 0, ErrICMPUnavailable
}

func (p *DualModeICMPPinger) pingNative(_ context.Context, ip netip.Addr) (time.Duration, error) {

	if p.listenPacketFunc == nil {
		return 0, errors.New("listenPacketFunc not set")
	}

	conn, err := p.listenPacketFunc("udp4", "0.0.0.0")
	if err != nil {
		conn, err = p.listenPacketFunc("ip4:icmp", "0.0.0.0")
		if err != nil {
			return 0, err
		}
	}
	defer func() { _ = conn.Close() }()

	msg := icmp.Message{
		Type: ipv4.ICMPTypeEcho, Code: 0,
		Body: &icmp.Echo{
			ID:   1,
			Seq:  1,
			Data: []byte("INFRAMAP_PING"),
		},
	}
	b, err := msg.Marshal(nil)
	if err != nil {
		return 0, err
	}

	dst := &net.UDPAddr{IP: net.ParseIP(ip.String())}
	start := time.Now()
	if err := conn.SetDeadline(time.Now().Add(1 * time.Second)); err != nil {
		return 0, err
	}
	if _, err := conn.WriteTo(b, dst); err != nil {
		return 0, err
	}

	reply := make([]byte, 1500)
	n, _, err := conn.ReadFrom(reply)
	if err != nil {
		return 0, err
	}

	rtt := time.Since(start)
	parsedMsg, err := icmp.ParseMessage(1, reply[:n])
	if err != nil {
		return 0, err
	}

	if parsedMsg.Type == ipv4.ICMPTypeEchoReply {
		return rtt, nil
	}

	return 0, fmt.Errorf("unexpected ICMP message type: %v", parsedMsg.Type)
}


var pingRTTRegex = regexp.MustCompile(`time[=<]([\d\.]+)\s*ms`)

// ParsePingRTT parses RTT latency duration from standard ping command output.
// Supports Linux (time=12.4 ms), macOS (time=1.234 ms), and BusyBox (time<1ms).
func ParsePingRTT(output string) (time.Duration, bool) {
	matches := pingRTTRegex.FindStringSubmatch(output)
	if len(matches) > 1 {
		ms, parseErr := strconv.ParseFloat(matches[1], 64)
		if parseErr == nil {
			return time.Duration(ms * float64(time.Millisecond)), true
		}
	}
	return 0, false
}

func (p *DualModeICMPPinger) pingCommand(ctx context.Context, ip netip.Addr) (time.Duration, error) {
	// System ping with 1s timeout and 1 packet
	//nolint:gosec // pingPath is resolved via exec.LookPath("ping") and ip is validated netip.Addr
	cmd := exec.CommandContext(ctx, p.pingPath, "-c", "1", "-W", "1", ip.String())
	var out bytes.Buffer
	cmd.Stdout = &out
	cmd.Stderr = &out

	if err := cmd.Run(); err != nil {
		return 0, fmt.Errorf("ping command failed: %w", err)
	}

	if rtt, ok := ParsePingRTT(out.String()); ok {
		return rtt, nil
	}

	// Default fallback RTT if parsing fails but command succeeded
	return 1 * time.Millisecond, nil
}
