// Package collectors provides plug-and-play worker contracts and DTOs for discovery.
package collectors

import (
	"context"
	"time"

	"github.com/google/uuid"
)

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
	OS                string
	LatencyMs         int64
	ProtocolSource    string
	ConfidenceScore   int
	RawMetadata       map[string]interface{}
	ObservedAt        time.Time
	ProviderRef       string
	ParentProviderRef string
}

// Collector defines the contract for plug-and-play discovery workers.
type Collector interface {
	ID() string
	Name() string
	Collect(ctx context.Context, target DiscoveryTarget) ([]RawObservation, error)
}
