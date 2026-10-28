package collectors

import (
	"bytes"
	"context"
	"errors"
	"fmt"
	"net"
	"net/netip"
	"os"
	"os/exec"
	"regexp"
	"strconv"
	"sync/atomic"
	"time"

	"golang.org/x/net/icmp"
	"golang.org/x/net/ipv4"
)

// ErrICMPUnavailable is returned when this process can open no ICMP socket and has no system
// ping binary to fall back to, so no address can be probed at all. It matches
// ErrCollectorUnavailable.
var ErrICMPUnavailable error = unavailableError("ICMP unavailable: unprivileged ICMP sockets are not permitted for this process " +
	"(net.ipv4.ping_group_range), raw sockets need CAP_NET_RAW, and no ping binary is installed")

// errNoICMPSocket reports that neither an unprivileged nor a raw ICMP socket could be opened,
// as opposed to a host that did not answer.
var errNoICMPSocket = errors.New("no ICMP socket could be opened")

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
//
// When the environment allows no ICMP at all, the first address already fails with
// ErrICMPUnavailable and Collect returns it at once instead of an empty result, so the
// run is recorded as skipped rather than as a sweep that found nothing.
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

	for _, addr := range addrs {
		if err := ctx.Err(); err != nil {
			return nil, fmt.Errorf("icmp collector: %w", err)
		}

		rtt, pingErr := c.pinger.Ping(ctx, addr)
		if pingErr != nil {
			if errors.Is(pingErr, ErrICMPUnavailable) {
				return nil, ErrICMPUnavailable
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
//  1. Native ICMP socket (golang.org/x/net/icmp): an unprivileged datagram socket ("udp4",
//     allowed when the process GID is within net.ipv4.ping_group_range), else a raw socket
//     ("ip4:icmp", which needs CAP_NET_RAW).
//  2. System ping CLI (exec.CommandContext), only when no socket could be opened.
//  3. ErrICMPUnavailable, when no socket could be opened and there is no ping binary.
//
// A socket that opens but gets no reply is an unreachable host, not an unavailable
// environment: it is reported as a plain error and does not fall back to the CLI.
type DualModeICMPPinger struct {
	pingPath         string
	listenPacketFunc func(network, address string) (PacketConn, error)
	seq              atomic.Uint32
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
	if !errors.Is(err, errNoICMPSocket) {
		return 0, err // The socket works; the host did not answer.
	}

	// Level 3: No socket and no CLI to fall back to
	if p.pingPath == "" {
		return 0, ErrICMPUnavailable
	}

	// Level 2: System ping CLI
	rtt, err = p.pingCommand(ctx, ip)
	if err != nil {
		if ctx.Err() != nil {
			return 0, ctx.Err()
		}
		return 0, err
	}
	return rtt, nil
}

// pingNative sends one echo request over an unprivileged ICMP socket, or a raw one when
// that is not permitted, and waits up to one second for the matching reply. It returns an
// error matching errNoICMPSocket when neither socket can be opened.
func (p *DualModeICMPPinger) pingNative(_ context.Context, ip netip.Addr) (time.Duration, error) {
	if p.listenPacketFunc == nil {
		return 0, fmt.Errorf("%w: listenPacketFunc not set", errNoICMPSocket)
	}

	// An unprivileged socket takes a UDP address and the kernel owns the echo ID; a raw
	// socket takes an IP address and sees every ICMP packet, so the ID must be checked.
	raw := false
	conn, udpErr := p.listenPacketFunc("udp4", "0.0.0.0")
	if udpErr != nil {
		var rawErr error
		conn, rawErr = p.listenPacketFunc("ip4:icmp", "0.0.0.0")
		if rawErr != nil {
			return 0, fmt.Errorf("%w: unprivileged: %v; raw: %v", errNoICMPSocket, udpErr, rawErr)
		}
		raw = true
	}
	defer func() { _ = conn.Close() }()

	id := os.Getpid() & 0xffff
	seq := int(p.seq.Add(1) & 0xffff)
	msg := icmp.Message{
		Type: ipv4.ICMPTypeEcho, Code: 0,
		Body: &icmp.Echo{
			ID:   id,
			Seq:  seq,
			Data: []byte("INFRAMAP_PING"),
		},
	}
	b, err := msg.Marshal(nil)
	if err != nil {
		return 0, err
	}

	var dst net.Addr = &net.UDPAddr{IP: ip.AsSlice()}
	if raw {
		dst = &net.IPAddr{IP: ip.AsSlice()}
	}

	start := time.Now()
	deadline := start.Add(1 * time.Second)
	if err := conn.SetDeadline(deadline); err != nil {
		return 0, err
	}
	if _, err := conn.WriteTo(b, dst); err != nil {
		return 0, err
	}

	reply := make([]byte, 1500)
	for {
		n, peer, err := conn.ReadFrom(reply)
		if err != nil {
			return 0, err
		}
		rtt := time.Since(start)

		parsed, err := icmp.ParseMessage(1, reply[:n])
		if err == nil && parsed.Type == ipv4.ICMPTypeEchoReply && peerIs(peer, ip) {
			if echo, ok := parsed.Body.(*icmp.Echo); ok && echo.Seq == seq && (!raw || echo.ID == id) {
				return rtt, nil
			}
		}
		// Another packet (a reply to someone else, or a late one): keep waiting.
		if !time.Now().Before(deadline) {
			return 0, fmt.Errorf("no echo reply from %s", ip)
		}
	}
}

// peerIs reports whether a socket peer address is ip.
func peerIs(peer net.Addr, ip netip.Addr) bool {
	var peerIP net.IP
	switch a := peer.(type) {
	case *net.UDPAddr:
		peerIP = a.IP
	case *net.IPAddr:
		peerIP = a.IP
	default:
		return false
	}
	addr, ok := netip.AddrFromSlice(peerIP)
	return ok && addr.Unmap() == ip.Unmap()
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
