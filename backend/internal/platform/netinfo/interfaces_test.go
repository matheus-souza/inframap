package netinfo_test

import (
	"testing"

	"github.com/matheussouza/inframap/internal/platform/netinfo"
)

func TestDetectInterfaces(t *testing.T) {
	ifaces, err := netinfo.DetectInterfaces()
	if err != nil {
		t.Fatalf("DetectInterfaces failed: %v", err)
	}

	for _, iface := range ifaces {
		if iface.Name == "" {
			t.Error("interface name should not be empty")
		}
		if iface.IP == "" {
			t.Error("interface IP should not be empty")
		}
		if iface.CIDR == "" {
			t.Error("interface CIDR should not be empty")
		}
		if iface.Name == "lo" || iface.Name == "lo0" {
			t.Errorf("loopback interface %s should be filtered", iface.Name)
		}
	}
}

func TestIsVirtualInterface(t *testing.T) {
	tests := []struct {
		name    string
		iface   string
		virtual bool
	}{
		{"docker bridge", "docker0", true},
		{"docker uppercase", "Docker0", true},
		{"br- prefix", "br-abc123", true},
		{"veth pair", "veth1234", true},
		{"virbr libvirt", "virbr0", true},
		{"lxc container", "lxc-br0", true},
		{"flannel overlay", "flannel.1", true},
		{"cni interface", "cni0", true},
		{"calico tunnel", "calicoabcdef", true},
		{"weave overlay", "weave", true},
		{"tun device", "tun0", true},
		{"tap device", "tap0", true},
		{"ethernet", "eth0", false},
		{"wifi", "wlan0", false},
		{"macos ethernet", "en0", false},
		{"bond interface", "bond0", false},
		{"empty name", "", false},
	}

	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			got := netinfo.IsVirtualInterface(tt.iface)
			if got != tt.virtual {
				t.Errorf("IsVirtualInterface(%q) = %v, want %v", tt.iface, got, tt.virtual)
			}
		})
	}
}
