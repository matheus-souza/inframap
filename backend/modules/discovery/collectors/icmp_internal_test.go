package collectors

import (
	"context"
	"net/netip"
	"os/exec"
	"testing"
	"time"
)

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
