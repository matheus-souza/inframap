// Package collectors provides plug-and-play worker contracts and DTOs for discovery.
package collectors

import (
	"context"
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
	CIDR            string
	SubnetID        string
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
type Collector interface {
	ID() string
	Name() string
	Collect(ctx context.Context, target DiscoveryTarget) ([]RawObservation, error)
}
