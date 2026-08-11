// Package netinfo provides network interface detection for auto-configuration.
package netinfo

import (
	"net"
	"net/netip"
	"strings"
)

// InterfaceInfo describes a network interface with its primary IPv4 address and CIDR.
type InterfaceInfo struct {
	Name    string `json:"name"`
	IP      string `json:"ip"`
	CIDR    string `json:"cidr"`
	MAC     string `json:"mac"`
	Gateway string `json:"gateway,omitempty"`
}

// DetectInterfaces returns a list of active, non-virtual network interfaces with IPv4 addresses.
func DetectInterfaces() ([]InterfaceInfo, error) {
	ifaces, err := net.Interfaces()
	if err != nil {
		return nil, err
	}

	var results []InterfaceInfo
	for _, iface := range ifaces {
		if iface.Flags&net.FlagUp == 0 || iface.Flags&net.FlagLoopback != 0 {
			continue
		}
		if IsVirtualInterface(iface.Name) {
			continue
		}

		addrs, err := iface.Addrs()
		if err != nil {
			continue
		}

		for _, addr := range addrs {
			ipNet, ok := addr.(*net.IPNet)
			if !ok {
				continue
			}
			ip4 := ipNet.IP.To4()
			if ip4 == nil {
				continue
			}

			prefix, parseErr := netip.ParsePrefix(ipNet.String())
			if parseErr != nil {
				continue
			}
			networkPrefix := netip.PrefixFrom(prefix.Masked().Addr(), prefix.Bits())

			results = append(results, InterfaceInfo{
				Name: iface.Name,
				IP:   ip4.String(),
				CIDR: networkPrefix.String(),
				MAC:  iface.HardwareAddr.String(),
			})
		}
	}
	return results, nil
}

// IsVirtualInterface returns true if the interface name matches a known virtual/container prefix.
func IsVirtualInterface(name string) bool {
	virtualPrefixes := []string{
		"docker", "br-", "veth", "virbr", "lxc", "flannel",
		"cni", "calico", "weave", "tun", "tap",
	}
	lower := strings.ToLower(name)
	for _, prefix := range virtualPrefixes {
		if strings.HasPrefix(lower, prefix) {
			return true
		}
	}
	return false
}
