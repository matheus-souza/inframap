package collectors_test

import (
	"net/netip"
	"testing"

	"github.com/matheussouza/inframap/modules/discovery/collectors"
)

func TestExpandCIDR(t *testing.T) {
	t.Run("Single host /32 returns one IP", func(t *testing.T) {
		addrs, err := collectors.ExpandCIDR("192.168.1.1/32")
		if err != nil {
			t.Fatalf("unexpected error: %v", err)
		}
		if len(addrs) != 1 {
			t.Fatalf("expected 1 address, got %d", len(addrs))
		}
		if addrs[0] != netip.MustParseAddr("192.168.1.1") {
			t.Errorf("expected 192.168.1.1, got %s", addrs[0])
		}
	})

	t.Run("/30 returns 2 usable hosts", func(t *testing.T) {
		addrs, err := collectors.ExpandCIDR("10.0.0.0/30")
		if err != nil {
			t.Fatalf("unexpected error: %v", err)
		}
		// /30 = 4 IPs total: .0 (network), .1, .2, .3 (broadcast)
		// Usable hosts: .1 and .2
		if len(addrs) != 2 {
			t.Fatalf("expected 2 usable hosts, got %d: %v", len(addrs), addrs)
		}
		expected := []netip.Addr{
			netip.MustParseAddr("10.0.0.1"),
			netip.MustParseAddr("10.0.0.2"),
		}
		for i, exp := range expected {
			if addrs[i] != exp {
				t.Errorf("addr[%d]: expected %s, got %s", i, exp, addrs[i])
			}
		}
	})

	t.Run("/24 returns 254 usable hosts", func(t *testing.T) {
		addrs, err := collectors.ExpandCIDR("192.168.1.0/24")
		if err != nil {
			t.Fatalf("unexpected error: %v", err)
		}
		if len(addrs) != 254 {
			t.Fatalf("expected 254 usable hosts, got %d", len(addrs))
		}
		if addrs[0] != netip.MustParseAddr("192.168.1.1") {
			t.Errorf("first host: expected 192.168.1.1, got %s", addrs[0])
		}
		if addrs[253] != netip.MustParseAddr("192.168.1.254") {
			t.Errorf("last host: expected 192.168.1.254, got %s", addrs[253])
		}
	})

	t.Run("IPv6 /128 returns single address", func(t *testing.T) {
		addrs, err := collectors.ExpandCIDR("::1/128")
		if err != nil {
			t.Fatalf("unexpected error: %v", err)
		}
		if len(addrs) != 1 {
			t.Fatalf("expected 1 address, got %d", len(addrs))
		}
		if addrs[0] != netip.MustParseAddr("::1") {
			t.Errorf("expected ::1, got %s", addrs[0])
		}
	})

	t.Run("Rejects CIDR larger than /16", func(t *testing.T) {
		_, err := collectors.ExpandCIDR("10.0.0.0/15")
		if err == nil {
			t.Fatal("expected error for oversized CIDR, got nil")
		}
	})

	t.Run("Rejects IPv6 CIDR larger than /112", func(t *testing.T) {
		_, err := collectors.ExpandCIDR("2001:db8::/64")
		if err == nil {
			t.Fatal("expected error for oversized IPv6 CIDR, got nil")
		}
	})

	t.Run("Rejects invalid CIDR string", func(t *testing.T) {
		_, err := collectors.ExpandCIDR("not-a-cidr")
		if err == nil {
			t.Fatal("expected error for invalid CIDR, got nil")
		}
	})


	t.Run("/31 point-to-point returns 2 IPs", func(t *testing.T) {
		addrs, err := collectors.ExpandCIDR("10.0.0.0/31")
		if err != nil {
			t.Fatalf("unexpected error: %v", err)
		}
		// RFC 3021: /31 point-to-point links use both addresses
		if len(addrs) != 2 {
			t.Fatalf("expected 2 addresses for /31, got %d", len(addrs))
		}
	})
}
