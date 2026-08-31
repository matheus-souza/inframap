// Package collectors provides plug-and-play worker contracts and adapters for discovery.
package collectors

import (
	"context"
	"fmt"
	"time"

	"github.com/google/uuid"
	"github.com/matheussouza/inframap/internal/platform/sdk"
)

// ProviderConfigResolver resolves decrypted provider configurations lazily per source.
type ProviderConfigResolver interface {
	ResolveCollectorConfig(ctx context.Context, sourceID uuid.UUID, collectorType string) (sdk.ProviderConfig, error)
}

// ProviderCollector adapts an sdk.Provider into a collectors.Collector.
type ProviderCollector struct {
	provider sdk.Provider
	resolver ProviderConfigResolver
}

// NewProviderCollector creates a new ProviderCollector adapter.
func NewProviderCollector(provider sdk.Provider, resolver ProviderConfigResolver) *ProviderCollector {
	return &ProviderCollector{
		provider: provider,
		resolver: resolver,
	}
}

// ID returns the provider ID (e.g. "proxmox", "docker").
func (p *ProviderCollector) ID() string {
	if p.provider == nil {
		return ""
	}
	return p.provider.ID()
}

// Name returns the provider Name from its metadata.
func (p *ProviderCollector) Name() string {
	if p.provider == nil {
		return ""
	}
	return p.provider.Metadata().Name
}

// Collect executes discovery using the underlying provider after resolving configuration.
// Returns an empty slice without error if target.SourceID is nil.
func (p *ProviderCollector) Collect(ctx context.Context, target DiscoveryTarget) ([]RawObservation, error) {
	if p.provider == nil {
		return []RawObservation{}, nil
	}
	if target.SourceID == nil {
		return []RawObservation{}, nil
	}

	var config sdk.ProviderConfig
	if p.resolver != nil {
		resolved, err := p.resolver.ResolveCollectorConfig(ctx, *target.SourceID, p.ID())
		if err != nil {
			return nil, fmt.Errorf("failed to resolve collector config for %s: %w", p.ID(), err)
		}
		config = resolved
	} else {
		config = sdk.ProviderConfig{}
	}

	devices, err := p.provider.Discover(ctx, config)
	if err != nil {
		return nil, err
	}

	observations := make([]RawObservation, 0, len(devices))
	now := time.Now()

	for _, dev := range devices {
		obs := RawObservation{
			IPAddress:       dev.IPAddress,
			MACAddress:      dev.MACAddress,
			Hostname:        dev.Hostname,
			Vendor:          dev.Vendor,
			OS:              dev.OSName,
			ProtocolSource:  p.ID(),
			ConfidenceScore: 85,
			RawMetadata:     dev.Metadata,
			ObservedAt:      now,
		}

		if dev.Metadata != nil {
			if proxmox, ok := dev.Metadata["proxmox"].(map[string]interface{}); ok {
				if vmID, exists := proxmox["vm_id"]; exists && vmID != nil {
					obs.ProviderRef = &ProviderRef{
						Provider: "proxmox",
						Scope:    "cluster",
						Kind:     "qemu",
						NativeID: fmt.Sprintf("%v", vmID),
					}
				}
				if nodeHost, exists := proxmox["node_host"]; exists && nodeHost != nil {
					obs.ParentProviderRef = &ProviderRef{
						Provider: "proxmox",
						Scope:    "cluster",
						Kind:     "node",
						NativeID: fmt.Sprintf("%v", nodeHost),
					}
				}
			}
			if docker, ok := dev.Metadata["docker"].(map[string]interface{}); ok {
				if containerID, exists := docker["container_id"]; exists && containerID != nil {
					obs.ProviderRef = &ProviderRef{
						Provider: "docker",
						Scope:    "engine",
						Kind:     "container",
						NativeID: fmt.Sprintf("%v", containerID),
					}
				}
			}
		}

		observations = append(observations, obs)
	}

	return observations, nil
}
