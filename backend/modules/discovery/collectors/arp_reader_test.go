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

	reader := collectors.NewProcNetARPReader(func(_ string) ([]byte, error) {
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

func TestParseProcNetARP_RejectsMulticastAndBroadcast(t *testing.T) {
	data := []byte(`IP address       HW type     Flags       HW address            Mask     Device
192.168.18.1     0x1         0x2         aa:bb:cc:dd:ee:01     *        eth0
192.168.18.2     0x1         0x2         01:00:5e:00:00:01     *        eth0
192.168.18.3     0x1         0x2         ff:ff:ff:ff:ff:ff     *        eth0
192.168.18.4     0x1         0x2         INVALIDMAC            *        eth0
192.168.18.5     0x1         0x2         33:33:00:00:00:01     *        eth0
`)

	reader := collectors.NewProcNetARPReader(func(_ string) ([]byte, error) {
		return data, nil
	})

	entries, err := reader.ReadEntries(context.Background())
	if err != nil {
		t.Fatalf("unexpected error: %v", err)
	}

	if len(entries) != 1 {
		t.Fatalf("expected 1 unicast entry, got %d", len(entries))
	}
	if entries[0].IPAddress != "192.168.18.1" {
		t.Errorf("expected IP 192.168.18.1, got %s", entries[0].IPAddress)
	}
}

func TestARPCollector_WithProcReader(t *testing.T) {
	data := []byte(`IP address       HW type     Flags       HW address            Mask     Device
192.168.18.1     0x1         0x2         aa:bb:cc:dd:ee:01     *        eth0
10.0.0.5         0x1         0x2         11:22:33:44:55:66     *        eth1
`)
	reader := collectors.NewProcNetARPReader(func(_ string) ([]byte, error) {
		return data, nil
	})
	col := collectors.NewARPCollector(reader)

	obs, err := col.Collect(context.Background(), collectors.DiscoveryTarget{CIDR: "192.168.18.0/24"})
	if err != nil {
		t.Fatalf("unexpected error: %v", err)
	}
	if len(obs) != 1 {
		t.Fatalf("expected 1 observation within CIDR, got %d", len(obs))
	}
	if obs[0].IPAddress != "192.168.18.1" {
		t.Errorf("expected IP 192.168.18.1, got %s", obs[0].IPAddress)
	}
	if obs[0].MACAddress != "aa:bb:cc:dd:ee:01" {
		t.Errorf("expected MAC aa:bb:cc:dd:ee:01, got %s", obs[0].MACAddress)
	}
}
