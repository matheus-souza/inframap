package engine

import (
	"errors"
	"fmt"
	"net"
	"net/netip"
	"strings"

	"github.com/matheussouza/inframap/modules/discovery/collectors"
)

var (
	// ErrInvalidIP indicates an unparseable IPv4/IPv6 syntax.
	ErrInvalidIP = errors.New("invalid IP address syntax")
	// ErrInvalidMAC indicates an unparseable MAC address format.
	ErrInvalidMAC = errors.New("invalid MAC address syntax")
	// ErrZeroMAC indicates a discarded zero MAC (00:00:00:00:00:00).
	ErrZeroMAC = errors.New("zero MAC address rejected")
	// ErrBroadcastMAC indicates a discarded broadcast MAC (FF:FF:FF:FF:FF:FF).
	ErrBroadcastMAC = errors.New("broadcast MAC address rejected")
	// ErrInvalidHostname indicates a hostname violating RFC limits or control character restrictions.
	ErrInvalidHostname = errors.New("invalid hostname string")
	// ErrMissingIdentity indicates an observation lacking IP, MAC, and ProviderRef.
	ErrMissingIdentity = errors.New("observation missing address or provider identity")
	// ErrMissingAddress is an alias for backwards compatibility.
	ErrMissingAddress = ErrMissingIdentity
)

// ValidateObservation checks an observation for structural sanity and security constraints.
func ValidateObservation(obs collectors.RawObservation) error {
	hasAddress := obs.IPAddress != "" || obs.MACAddress != ""
	hasProviderRef := obs.ProviderRef != nil && !obs.ProviderRef.IsZero()
	if !hasAddress && !hasProviderRef {
		return ErrMissingIdentity
	}

	if obs.IPAddress != "" {
		if _, err := netip.ParseAddr(obs.IPAddress); err != nil {
			return fmt.Errorf("%w: %q", ErrInvalidIP, obs.IPAddress)
		}
	}

	if obs.MACAddress != "" {
		parsed, err := net.ParseMAC(obs.MACAddress)
		if err != nil {
			return fmt.Errorf("%w: %q", ErrInvalidMAC, obs.MACAddress)
		}

		macStr := strings.ToLower(parsed.String())
		if macStr == "00:00:00:00:00:00" {
			return ErrZeroMAC
		}
		if macStr == "ff:ff:ff:ff:ff:ff" {
			return ErrBroadcastMAC
		}
	}

	if obs.Hostname != "" {
		if len(obs.Hostname) > 253 {
			return fmt.Errorf("%w: max length exceeded", ErrInvalidHostname)
		}
		for _, r := range obs.Hostname {
			if r < 0x20 || r == 0x7f {
				return fmt.Errorf("%w: contains unprintable control character", ErrInvalidHostname)
			}
		}
	}

	return nil
}
