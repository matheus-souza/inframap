// Package collectors provides plug-and-play worker contracts and DTOs for discovery.
package collectors

import (
	"context"
	"errors"
	"net/netip"
	"time"

	"github.com/google/uuid"

	"github.com/matheussouza/inframap/internal/platform/sdk"
)

// ProviderRef is the canonical workload identity emitted by providers. It is defined in the
// sdk package so that sdk.NormalizedDevice can carry it as a typed field without the
// discovery pipeline and the provider SDK importing each other in a cycle.
type ProviderRef = sdk.ProviderRef

// DiscoveryTarget defines the parameters for a collector run.
type DiscoveryTarget struct {
	// CIDR is the address block a per-target collector probes (RFC-026 §3).
	CIDR     string
	SubnetID string
	// Prefixes is the union of every target of a multi-target run, handed to union-scope
	// collectors (mDNS, ARP) so their single query is filtered by all targets at once. When
	// empty, those collectors fall back to CIDR.
	Prefixes        []netip.Prefix
	CredentialSetID *string
	SourceID        *uuid.UUID
}

// RawObservation represents a single un-reconciled fact gathered by a collector.
type RawObservation struct {
	IPAddress         string
	MACAddress        string
	Hostname          string
	Vendor            string
	DeviceType        string
	OS                string
	LatencyMs         int64
	ProtocolSource    string
	ConfidenceScore   int
	RawMetadata       map[string]interface{}
	ObservedAt        time.Time
	ProviderRef       *ProviderRef
	ParentProviderRef *ProviderRef
}

// Collector defines the contract for plug-and-play discovery workers.
//
// A collector that cannot run at all in the current environment (a missing privilege or
// system facility, not a target that did not answer) MUST return an error matching
// ErrCollectorUnavailable instead of an empty result, so the run records it as skipped
// rather than as a successful scan that found nothing.
type Collector interface {
	ID() string
	Name() string
	Collect(ctx context.Context, target DiscoveryTarget) ([]RawObservation, error)
}

// ErrCollectorUnavailable matches every error a collector returns when the environment does
// not let it scan at all. The engine records such a run as skipped, with the error's own
// message as the reason.
var ErrCollectorUnavailable = errors.New("collector unavailable in this environment")

// unavailableError is an ErrCollectorUnavailable that reads as its own reason.
type unavailableError string

func (e unavailableError) Error() string { return string(e) }

// Is makes every unavailableError match ErrCollectorUnavailable.
func (e unavailableError) Is(target error) bool { return target == ErrCollectorUnavailable }
