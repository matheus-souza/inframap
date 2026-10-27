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

	if err := checkPrefixSize(prefix); err != nil {
		return netip.Prefix{}, err
	}

	return prefix, nil
}

// checkPrefixSize enforces the /16 (IPv4) and /112 (IPv6) upper bound on a target block.
func checkPrefixSize(prefix netip.Prefix) error {
	bits := prefix.Bits()
	if prefix.Addr().Is4() && bits < 16 {
		return fmt.Errorf("%w: /%d", ErrCIDRTooLarge, bits)
	}
	if prefix.Addr().Is6() && bits < 112 {
		return fmt.Errorf("%w: /%d (IPv6)", ErrCIDRTooLarge, bits)
	}
	return nil
}

// TargetPrefixes returns the address blocks a union-scope collector filters its responses
// by: target.Prefixes when set, otherwise the single target.CIDR. Every block is held to
// the same size limit as ParseTargetPrefix.
func TargetPrefixes(target DiscoveryTarget) ([]netip.Prefix, error) {
	if len(target.Prefixes) == 0 {
		prefix, err := ParseTargetPrefix(target.CIDR)
		if err != nil {
			return nil, err
		}
		return []netip.Prefix{prefix}, nil
	}

	prefixes := make([]netip.Prefix, 0, len(target.Prefixes))
	for _, prefix := range target.Prefixes {
		if !prefix.IsValid() {
			return nil, fmt.Errorf("invalid target prefix %q", prefix)
		}
		if err := checkPrefixSize(prefix); err != nil {
			return nil, err
		}
		prefixes = append(prefixes, prefix)
	}
	return prefixes, nil
}

// PrefixesContain reports whether addr falls inside any of the prefixes. IPv4-mapped IPv6
// addresses are matched as IPv4.
func PrefixesContain(prefixes []netip.Prefix, addr netip.Addr) bool {
	addr = addr.Unmap()
	for _, prefix := range prefixes {
		if prefix.Contains(addr) {
			return true
		}
	}
	return false
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
