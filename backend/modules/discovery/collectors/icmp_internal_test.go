package collectors

import (
	"context"
	"errors"
	"net"
	"net/netip"
	"os/exec"
	"strings"
	"testing"
	"time"

	"golang.org/x/net/icmp"
	"golang.org/x/net/ipv4"
)

// fakeReply is one packet a fakePacketConn hands to ReadFrom.
type fakeReply struct {
	msg  []byte
	peer net.Addr
}

type fakePacketConn struct {
	replies []fakeReply
	readErr error
	dst     net.Addr
	sent    []byte
}

func (f *fakePacketConn) SetDeadline(_ time.Time) error { return nil }
func (f *fakePacketConn) WriteTo(b []byte, dst net.Addr) (int, error) {
	f.dst = dst
	f.sent = append([]byte(nil), b...)
	return len(b), nil
}
func (f *fakePacketConn) ReadFrom(b []byte) (int, net.Addr, error) {
	if len(f.replies) == 0 {
		if f.readErr != nil {
			return 0, nil, f.readErr
		}
		return 0, nil, errors.New("i/o timeout")
	}
	reply := f.replies[0]
	f.replies = f.replies[1:]
	n := copy(b, reply.msg)
	return n, reply.peer, nil
}
func (f *fakePacketConn) Close() error { return nil }

// echoReply marshals an echo reply answering the request sent over conn, with its ID
// replaced by id when id is not negative.
func echoReply(t *testing.T, conn *fakePacketConn, id int) []byte {
	t.Helper()
	req, err := icmp.ParseMessage(1, conn.sent)
	if err != nil {
		t.Fatalf("failed to parse sent request: %v", err)
	}
	echo := *req.Body.(*icmp.Echo)
	if id >= 0 {
		echo.ID = id
	}
	b, err := (&icmp.Message{Type: ipv4.ICMPTypeEchoReply, Body: &echo}).Marshal(nil)
	if err != nil {
		t.Fatalf("failed to marshal icmp reply: %v", err)
	}
	return b
}

// replyingConn is a fakePacketConn that answers the request it is sent with the replies
// built by answer.
type replyingConn struct {
	fakePacketConn
	answer func(conn *fakePacketConn) []fakeReply
}

func (r *replyingConn) WriteTo(b []byte, dst net.Addr) (int, error) {
	n, err := r.fakePacketConn.WriteTo(b, dst)
	r.replies = r.answer(&r.fakePacketConn)
	return n, err
}

func TestDualModeICMPPinger_PingCommandDirect(t *testing.T) {
	echoPath, err := exec.LookPath("echo")
	if err != nil {
		t.Skip("echo binary not found")
	}

	pinger := &DualModeICMPPinger{pingPath: echoPath}
	ctx := context.Background()
	ip := netip.MustParseAddr("127.0.0.1")

	rtt, err := pinger.pingCommand(ctx, ip)
	if err != nil {
		t.Fatalf("unexpected pingCommand error: %v", err)
	}
	if rtt != 1*time.Millisecond {
		t.Errorf("expected 1ms fallback RTT, got %v", rtt)
	}
}

func TestDualModeICMPPinger_PingNativeSuccess(t *testing.T) {
	ip := netip.MustParseAddr("192.0.2.10")
	conn := &replyingConn{answer: func(c *fakePacketConn) []fakeReply {
		// The kernel owns the ID of an unprivileged socket, so any ID is accepted.
		return []fakeReply{{msg: echoReply(t, c, 4242), peer: &net.UDPAddr{IP: ip.AsSlice()}}}
	}}

	pinger := &DualModeICMPPinger{
		listenPacketFunc: func(network, _ string) (PacketConn, error) {
			if network != "udp4" {
				t.Errorf("expected the unprivileged socket first, got %q", network)
			}
			return conn, nil
		},
	}

	rtt, err := pinger.pingNative(context.Background(), ip)
	if err != nil {
		t.Fatalf("unexpected pingNative error: %v", err)
	}
	if rtt <= 0 {
		t.Errorf("expected positive RTT, got %v", rtt)
	}
	if _, ok := conn.dst.(*net.UDPAddr); !ok {
		t.Errorf("expected a UDP destination on the unprivileged socket, got %T", conn.dst)
	}
}

func TestDualModeICMPPinger_PingNativeRawFallback(t *testing.T) {
	ip := netip.MustParseAddr("192.0.2.10")
	other := netip.MustParseAddr("192.0.2.99")
	conn := &replyingConn{answer: func(c *fakePacketConn) []fakeReply {
		return []fakeReply{
			// A raw socket sees every ICMP packet: replies from another host, or to another
			// process, must not be taken for this one.
			{msg: echoReply(t, c, -1), peer: &net.IPAddr{IP: other.AsSlice()}},
			{msg: echoReply(t, c, 1), peer: &net.IPAddr{IP: ip.AsSlice()}},
			{msg: echoReply(t, c, -1), peer: &net.IPAddr{IP: ip.AsSlice()}},
		}
	}}

	var networks []string
	pinger := &DualModeICMPPinger{
		listenPacketFunc: func(network, _ string) (PacketConn, error) {
			networks = append(networks, network)
			if network == "udp4" {
				return nil, errors.New("socket: permission denied")
			}
			return conn, nil
		},
	}

	rtt, err := pinger.pingNative(context.Background(), ip)
	if err != nil {
		t.Fatalf("unexpected pingNative error: %v", err)
	}
	if rtt <= 0 {
		t.Errorf("expected positive RTT, got %v", rtt)
	}
	if len(networks) != 2 || networks[1] != "ip4:icmp" {
		t.Errorf("expected udp4 then ip4:icmp, got %v", networks)
	}
	if _, ok := conn.dst.(*net.IPAddr); !ok {
		t.Errorf("expected an IP destination on the raw socket, got %T", conn.dst)
	}
	if len(conn.replies) != 0 {
		t.Errorf("expected the matching reply to be the last one read, %d left", len(conn.replies))
	}
}

func TestDualModeICMPPinger_Ping(t *testing.T) {
	ip := netip.MustParseAddr("192.0.2.10")
	noSocket := func(_, _ string) (PacketConn, error) {
		return nil, errors.New("socket: operation not permitted")
	}

	t.Run("no socket and no ping binary is ErrICMPUnavailable", func(t *testing.T) {
		pinger := &DualModeICMPPinger{listenPacketFunc: noSocket}
		_, err := pinger.Ping(context.Background(), ip)
		if !errors.Is(err, ErrICMPUnavailable) {
			t.Fatalf("expected ErrICMPUnavailable, got %v", err)
		}
	})

	t.Run("a host that does not answer is not an unavailable environment", func(t *testing.T) {
		falsePath, lookErr := exec.LookPath("false")
		if lookErr != nil {
			t.Skip("false binary not found")
		}
		pinger := &DualModeICMPPinger{
			pingPath: falsePath,
			listenPacketFunc: func(_, _ string) (PacketConn, error) {
				return &fakePacketConn{readErr: errors.New("i/o timeout")}, nil
			},
		}
		_, err := pinger.Ping(context.Background(), ip)
		if err == nil || errors.Is(err, ErrICMPUnavailable) || errors.Is(err, errNoICMPSocket) {
			t.Fatalf("expected a plain no-reply error, got %v", err)
		}
		if strings.Contains(err.Error(), "ping command failed") {
			t.Errorf("expected no CLI fallback once a socket opened, got %v", err)
		}
	})

	t.Run("no socket falls back to the ping binary", func(t *testing.T) {
		echoPath, lookErr := exec.LookPath("echo")
		if lookErr != nil {
			t.Skip("echo binary not found")
		}
		pinger := &DualModeICMPPinger{pingPath: echoPath, listenPacketFunc: noSocket}
		rtt, err := pinger.Ping(context.Background(), ip)
		if err != nil {
			t.Fatalf("unexpected error: %v", err)
		}
		if rtt != 1*time.Millisecond {
			t.Errorf("expected 1ms fallback RTT, got %v", rtt)
		}
	})

	t.Run("a failing ping binary is an unreachable host", func(t *testing.T) {
		falsePath, lookErr := exec.LookPath("false")
		if lookErr != nil {
			t.Skip("false binary not found")
		}
		pinger := &DualModeICMPPinger{pingPath: falsePath, listenPacketFunc: noSocket}
		_, err := pinger.Ping(context.Background(), ip)
		if err == nil || errors.Is(err, ErrICMPUnavailable) {
			t.Fatalf("expected a plain error, got %v", err)
		}
	})
}

func TestDualModeICMPPinger_PingNativeError(t *testing.T) {
	t.Run("ListenPacket fails", func(t *testing.T) {
		pinger := &DualModeICMPPinger{
			listenPacketFunc: func(_, _ string) (PacketConn, error) {
				return nil, errors.New("permission denied")
			},
		}
		_, err := pinger.pingNative(context.Background(), netip.MustParseAddr("127.0.0.1"))
		if !errors.Is(err, errNoICMPSocket) {
			t.Errorf("expected errNoICMPSocket when ListenPacket fails, got %v", err)
		}
	})

	t.Run("ReadFrom fails", func(t *testing.T) {
		pinger := &DualModeICMPPinger{
			listenPacketFunc: func(_, _ string) (PacketConn, error) {
				return &fakePacketConn{readErr: errors.New("read timeout")}, nil
			},
		}
		_, err := pinger.pingNative(context.Background(), netip.MustParseAddr("127.0.0.1"))
		if err == nil || errors.Is(err, errNoICMPSocket) {
			t.Errorf("expected a read error when ReadFrom fails, got %v", err)
		}
	})

	t.Run("pingNative with nil listenPacketFunc", func(t *testing.T) {
		pinger := &DualModeICMPPinger{listenPacketFunc: nil}
		_, err := pinger.pingNative(context.Background(), netip.MustParseAddr("127.0.0.1"))
		if err == nil {
			t.Error("expected error when listenPacketFunc is nil, got nil")
		}
	})
}
