package collectors

import (
	"errors"
	"fmt"
	"net/netip"
)

// ErrCIDRTooLarge is returned when the CIDR prefix is too broad (larger than /16).
var ErrCIDRTooLarge = errors.New("CIDR prefix too large: maximum allowed is /16")

// ParseTargetPrefix parses a CIDR string and enforces that the prefix is not larger than /16.
func ParseTargetPrefix(cidr string) (netip.Prefix, error) {
	prefix, err := netip.ParsePrefix(cidr)
	if err != nil {
		return netip.Prefix{}, fmt.Errorf("invalid CIDR %q: %w", cidr, err)
	}

	bits := prefix.Bits()
	if prefix.Addr().Is4() && bits < 16 {
		return netip.Prefix{}, fmt.Errorf("%w: /%d", ErrCIDRTooLarge, bits)
	}
	if prefix.Addr().Is6() && bits < 112 {
		return netip.Prefix{}, fmt.Errorf("%w: /%d (IPv6)", ErrCIDRTooLarge, bits)
	}

	return prefix, nil
}

// ExpandCIDR expands a CIDR string into all usable host IP addresses.
// For IPv4, network and broadcast addresses are excluded for prefixes <= /30.
// For /31 (RFC 3021 point-to-point), both addresses are returned.
// For /32, the single address is returned.
// Returns an error if the CIDR is invalid or the prefix is larger than /16.
func ExpandCIDR(cidr string) ([]netip.Addr, error) {
	prefix, err := ParseTargetPrefix(cidr)
	if err != nil {
		return nil, err
	}

	bits := prefix.Bits()
	prefix = netip.PrefixFrom(prefix.Masked().Addr(), bits)


	var addrs []netip.Addr
	addr := prefix.Addr()
	for prefix.Contains(addr) {
		addrs = append(addrs, addr)
		addr = addr.Next()
	}

	// For IPv4 prefixes <= /30 (more than 2 addresses), strip network and broadcast.
	if prefix.Addr().Is4() && bits <= 30 && len(addrs) > 2 {
		addrs = addrs[1 : len(addrs)-1]
	}

	return addrs, nil
}
