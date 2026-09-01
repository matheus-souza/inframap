package engine

import (
	"net"
	"strings"
	"time"

	"github.com/matheussouza/inframap/modules/discovery/collectors"
)

// NormalizeObservation cleanses and standardizes raw observation values.
func NormalizeObservation(obs collectors.RawObservation) collectors.RawObservation {
	ip := strings.TrimSpace(obs.IPAddress)

	mac := strings.TrimSpace(obs.MACAddress)
	if parsed, err := net.ParseMAC(mac); err == nil {
		mac = strings.ToLower(parsed.String())
	} else {
		mac = strings.ToLower(mac)
	}

	hostname := strings.ToLower(strings.TrimSpace(obs.Hostname))
	vendor := strings.TrimSpace(obs.Vendor)
	deviceType := strings.ToLower(strings.TrimSpace(obs.DeviceType))
	osStr := strings.TrimSpace(obs.OS)
	source := strings.ToLower(strings.TrimSpace(obs.ProtocolSource))

	observedAt := obs.ObservedAt
	if observedAt.IsZero() {
		observedAt = time.Now()
	}
	var providerRef *collectors.ProviderRef
	if obs.ProviderRef != nil {
		providerRef = &collectors.ProviderRef{
			Provider: strings.TrimSpace(obs.ProviderRef.Provider),
			Scope:    strings.TrimSpace(obs.ProviderRef.Scope),
			Kind:     strings.TrimSpace(obs.ProviderRef.Kind),
			NativeID: strings.TrimSpace(obs.ProviderRef.NativeID),
		}
	}
	var parentProviderRef *collectors.ProviderRef
	if obs.ParentProviderRef != nil {
		parentProviderRef = &collectors.ProviderRef{
			Provider: strings.TrimSpace(obs.ParentProviderRef.Provider),
			Scope:    strings.TrimSpace(obs.ParentProviderRef.Scope),
			Kind:     strings.TrimSpace(obs.ParentProviderRef.Kind),
			NativeID: strings.TrimSpace(obs.ParentProviderRef.NativeID),
		}
	}

	return collectors.RawObservation{
		IPAddress:         ip,
		MACAddress:        mac,
		Hostname:          hostname,
		Vendor:            vendor,
		DeviceType:        deviceType,
		OS:                osStr,
		LatencyMs:         obs.LatencyMs,
		ProtocolSource:    source,
		ConfidenceScore:   obs.ConfidenceScore,
		RawMetadata:       obs.RawMetadata,
		ObservedAt:        observedAt,
		ProviderRef:       providerRef,
		ParentProviderRef: parentProviderRef,
	}
}
