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
	osStr := strings.TrimSpace(obs.OS)
	source := strings.ToLower(strings.TrimSpace(obs.ProtocolSource))

	observedAt := obs.ObservedAt
	if observedAt.IsZero() {
		observedAt = time.Now()
	}

	return collectors.RawObservation{
		IPAddress:       ip,
		MACAddress:      mac,
		Hostname:        hostname,
		Vendor:          vendor,
		OS:              osStr,
		LatencyMs:       obs.LatencyMs,
		ProtocolSource:  source,
		ConfidenceScore: obs.ConfidenceScore,
		RawMetadata:     obs.RawMetadata,
		ObservedAt:      observedAt,
	}
}
