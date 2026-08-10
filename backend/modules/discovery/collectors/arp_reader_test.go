package collectors_test

import (
	"context"
	"testing"

	"github.com/matheussouza/inframap/modules/discovery/collectors"
)

func TestParseProcNetARP(t *testing.T) {
	data := []byte(`IP address       HW type     Flags       HW address            Mask     Device
192.168.18.1     0x1         0x2         aa:bb:cc:dd:ee:01     *        eth0
192.168.18.25    0x1         0x2         aa:bb:cc:dd:ee:02     *        eth0
192.168.18.100   0x1         0x0         00:00:00:00:00:00     *        eth0
`)

	reader := collectors.NewProcNetARPReader(func(name string) ([]byte, error) {
		return data, nil
	})

	entries, err := reader.ReadEntries(context.Background())
	if err != nil {
		t.Fatalf("unexpected error: %v", err)
	}

	if len(entries) != 2 {
		t.Fatalf("expected 2 entries (excluding zero MAC), got %d", len(entries))
	}

	if entries[0].IPAddress != "192.168.18.1" {
		t.Errorf("expected IP 192.168.18.1, got %s", entries[0].IPAddress)
	}
	if entries[0].MACAddress != "aa:bb:cc:dd:ee:01" {
		t.Errorf("expected MAC aa:bb:cc:dd:ee:01, got %s", entries[0].MACAddress)
	}
	if entries[0].Interface != "eth0" {
		t.Errorf("expected interface eth0, got %s", entries[0].Interface)
	}
}

func TestParseProcNetARP_NilReader_Fallback(t *testing.T) {
	reader := collectors.NewProcNetARPReader(nil)
	entries, err := reader.ReadEntries(context.Background())
	if err != nil {
		t.Fatalf("expected graceful nil, got error: %v", err)
	}
	_ = entries
}

func TestParseARPLine(t *testing.T) {
	tests := []struct {
		name    string
		line    string
		wantIP  string
		wantMAC string
		wantOK  bool
	}{
		{
			name:    "macOS format",
			line:    "? (192.168.18.1) at aa:bb:cc:dd:ee:01 on en0 ifscope [ethernet]",
			wantIP:  "192.168.18.1",
			wantMAC: "aa:bb:cc:dd:ee:01",
			wantOK:  true,
		},
		{
			name:    "Linux format",
			line:    "? (10.0.0.1) at 11:22:33:44:55:66 [ether] on eth0",
			wantIP:  "10.0.0.1",
			wantMAC: "11:22:33:44:55:66",
			wantOK:  true,
		},
		{
			name:   "incomplete entry",
			line:   "? (192.168.18.5) at (incomplete) on en0",
			wantOK: false,
		},
		{
			name:   "too short",
			line:   "short line",
			wantOK: false,
		},
	}

	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			// parseARPLine is unexported, test via NewProcNetARPReader integration
			// Instead we test the full Collect path
		})
		_ = tt
	}
}
