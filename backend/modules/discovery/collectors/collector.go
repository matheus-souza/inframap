// Package collectors provides plug-and-play worker contracts and DTOs for discovery.
package collectors

import (
	"context"
	"fmt"
	"time"
)

// ProviderRef represents the unique external identity of an entity managed by a provider.
type ProviderRef struct {
	Provider string `json:"provider"`
	Scope    string `json:"scope"`
	Kind     string `json:"kind"`
	NativeID string `json:"native_id"`
}

// IsZero returns true if all ProviderRef fields are empty.
func (r ProviderRef) IsZero() bool {
	return r.Provider == "" && r.Scope == "" && r.Kind == "" && r.NativeID == ""
}

// Key returns the canonical string representation of the provider identity.
func (r ProviderRef) Key() string {
	return fmt.Sprintf("%s:%s:%s:%s", r.Provider, r.Scope, r.Kind, r.NativeID)
}

// DiscoveryTarget defines the parameters for a collector run.
type DiscoveryTarget struct {
	CIDR            string
	SubnetID        string
	CredentialSetID *string
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
	ProviderRef       *ProviderRef
	ParentProviderRef *ProviderRef
}

// Collector defines the contract for plug-and-play discovery workers.
type Collector interface {
	ID() string
	Name() string
	Collect(ctx context.Context, target DiscoveryTarget) ([]RawObservation, error)
}
