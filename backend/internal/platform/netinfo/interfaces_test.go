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

func TestDetectInterfaces_NoVirtualInterfaces(t *testing.T) {
	ifaces, err := netinfo.DetectInterfaces()
	if err != nil {
		t.Fatalf("DetectInterfaces failed: %v", err)
	}

	virtualPrefixes := []string{"docker", "br-", "veth", "virbr"}
	for _, iface := range ifaces {
		for _, prefix := range virtualPrefixes {
			if len(iface.Name) >= len(prefix) && iface.Name[:len(prefix)] == prefix {
				t.Errorf("virtual interface %s should be filtered", iface.Name)
			}
		}
	}
}
