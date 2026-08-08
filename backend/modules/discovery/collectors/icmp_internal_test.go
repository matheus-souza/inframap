package collectors

import (
	"context"
	"errors"
	"net"
	"net/netip"
	"os/exec"
	"testing"
	"time"

	"golang.org/x/net/icmp"
	"golang.org/x/net/ipv4"
)

type fakePacketConn struct {
	replyMsg []byte
	readErr  error
}

func (f *fakePacketConn) SetDeadline(_ time.Time) error            { return nil }
func (f *fakePacketConn) WriteTo(_ []byte, _ net.Addr) (int, error) { return 10, nil }
func (f *fakePacketConn) ReadFrom(b []byte) (int, net.Addr, error) {
	if f.readErr != nil {
		return 0, nil, f.readErr
	}
	n := copy(b, f.replyMsg)
	return n, &net.UDPAddr{}, nil
}
func (f *fakePacketConn) Close() error { return nil }

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
	echoReplyMsg := icmp.Message{
		Type: ipv4.ICMPTypeEchoReply, Code: 0,
		Body: &icmp.Echo{ID: 1, Seq: 1, Data: []byte("INFRAMAP_PING")},
	}
	b, err := echoReplyMsg.Marshal(nil)
	if err != nil {
		t.Fatalf("failed to marshal icmp reply: %v", err)
	}

	pinger := &DualModeICMPPinger{
		listenPacketFunc: func(_, _ string) (PacketConn, error) {
			return &fakePacketConn{replyMsg: b}, nil
		},
	}

	rtt, err := pinger.pingNative(context.Background(), netip.MustParseAddr("127.0.0.1"))
	if err != nil {
		t.Fatalf("unexpected pingNative error: %v", err)
	}
	if rtt <= 0 {
		t.Errorf("expected positive RTT, got %v", rtt)
	}
}

func TestDualModeICMPPinger_PingNativeError(t *testing.T) {
	t.Run("ListenPacket fails", func(t *testing.T) {
		pinger := &DualModeICMPPinger{
			listenPacketFunc: func(_, _ string) (PacketConn, error) {
				return nil, errors.New("permission denied")
			},
		}
		_, err := pinger.pingNative(context.Background(), netip.MustParseAddr("127.0.0.1"))
		if err == nil {
			t.Error("expected error when ListenPacket fails, got nil")
		}
	})

	t.Run("ReadFrom fails", func(t *testing.T) {
		pinger := &DualModeICMPPinger{
			listenPacketFunc: func(_, _ string) (PacketConn, error) {
				return &fakePacketConn{readErr: errors.New("read timeout")}, nil
			},
		}
		_, err := pinger.pingNative(context.Background(), netip.MustParseAddr("127.0.0.1"))
		if err == nil {
			t.Error("expected error when ReadFrom fails, got nil")
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
