package collectors_test

import (
	"context"
	"errors"
	"testing"
	"time"

	"github.com/google/uuid"
	"github.com/matheussouza/inframap/internal/platform/sdk"
	"github.com/matheussouza/inframap/modules/discovery/collectors"
)

type mockProvider struct {
	id          string
	name        string
	discoverErr error
	devices     []sdk.NormalizedDevice
	lastConfig  sdk.ProviderConfig
}

func (m *mockProvider) ID() string {
	return m.id
}

func (m *mockProvider) Metadata() sdk.ProviderMetadata {
	return sdk.ProviderMetadata{
		ID:   m.id,
		Name: m.name,
	}
}

func (m *mockProvider) ConfigSchema() sdk.ConfigSchema {
	return sdk.ConfigSchema{}
}

func (m *mockProvider) HealthCheck(_ context.Context, _ sdk.ProviderConfig) error {
	return nil
}

func (m *mockProvider) Discover(_ context.Context, config sdk.ProviderConfig) ([]sdk.NormalizedDevice, error) {
	m.lastConfig = config
	if m.discoverErr != nil {
		return nil, m.discoverErr
	}
	return m.devices, nil
}

type mockConfigResolver struct {
	resolveFunc func(ctx context.Context, sourceID uuid.UUID, collectorType string) (sdk.ProviderConfig, error)
}

func (r *mockConfigResolver) ResolveCollectorConfig(ctx context.Context, sourceID uuid.UUID, collectorType string) (sdk.ProviderConfig, error) {
	if r.resolveFunc != nil {
		return r.resolveFunc(ctx, sourceID, collectorType)
	}
	return sdk.ProviderConfig{}, nil
}

func TestProviderCollector_IDAndName(t *testing.T) {
	p := &mockProvider{id: "proxmox", name: "Proxmox VE"}
	col := collectors.NewProviderCollector(p, nil)

	if got := col.ID(); got != "proxmox" {
		t.Errorf("ID() = %q, want %q", got, "proxmox")
	}
	if got := col.Name(); got != "Proxmox VE" {
		t.Errorf("Name() = %q, want %q", got, "Proxmox VE")
	}

	nilCol := collectors.NewProviderCollector(nil, nil)
	if got := nilCol.ID(); got != "" {
		t.Errorf("nil provider ID() = %q, want empty", got)
	}
	if got := nilCol.Name(); got != "" {
		t.Errorf("nil provider Name() = %q, want empty", got)
	}
}

func TestProviderCollector_Collect_NilSourceID(t *testing.T) {
	p := &mockProvider{id: "proxmox", name: "Proxmox VE"}
	resolverCalled := false
	resolver := &mockConfigResolver{
		resolveFunc: func(_ context.Context, _ uuid.UUID, _ string) (sdk.ProviderConfig, error) {
			resolverCalled = true
			return sdk.ProviderConfig{}, nil
		},
	}

	col := collectors.NewProviderCollector(p, resolver)
	target := collectors.DiscoveryTarget{
		CIDR:     "192.168.1.0/24",
		SourceID: nil,
	}

	obs, err := col.Collect(context.Background(), target)
	if err != nil {
		t.Fatalf("unexpected error on nil SourceID: %v", err)
	}
	if len(obs) != 0 {
		t.Errorf("expected 0 observations for nil SourceID, got %d", len(obs))
	}
	if resolverCalled {
		t.Error("resolver should not be called when SourceID is nil")
	}
}

func TestProviderCollector_Collect_Success(t *testing.T) {
	sourceID := uuid.New()
	p := &mockProvider{
		id:   "proxmox",
		name: "Proxmox VE",
		devices: []sdk.NormalizedDevice{
			{
				Hostname:   "pve-node1",
				IPAddress:  "192.168.1.100",
				MACAddress: "bc:24:11:22:33:44",
				DeviceType: "server",
				Vendor:     "Proxmox",
				OSName:     "Linux",
				Metadata: map[string]interface{}{
					"proxmox": map[string]interface{}{
						"is_host": true,
						"node":    "pve-node1",
					},
				},
			},
			{
				Hostname:   "vm-web",
				IPAddress:  "192.168.1.105",
				MACAddress: "bc:24:11:22:33:55",
				DeviceType: "virtual_machine",
				Vendor:     "QEMU",
				OSName:     "Debian",
				Metadata: map[string]interface{}{
					"proxmox": map[string]interface{}{
						"vm_id":     101,
						"node_host": "pve-node1",
						"status":    "running",
					},
				},
			},
		},
	}

	expectedCfg := sdk.ProviderConfig{
		"api_url":  "https://192.168.1.100:8006",
		"token_id": "root@pam!token",
	}

	resolver := &mockConfigResolver{
		resolveFunc: func(_ context.Context, sID uuid.UUID, cType string) (sdk.ProviderConfig, error) {
			if sID != sourceID {
				t.Errorf("resolver received sourceID %v, want %v", sID, sourceID)
			}
			if cType != "proxmox" {
				t.Errorf("resolver received collectorType %q, want %q", cType, "proxmox")
			}
			return expectedCfg, nil
		},
	}

	col := collectors.NewProviderCollector(p, resolver)
	target := collectors.DiscoveryTarget{
		CIDR:     "192.168.1.0/24",
		SourceID: &sourceID,
	}

	startTime := time.Now()
	obs, err := col.Collect(context.Background(), target)
	if err != nil {
		t.Fatalf("unexpected error from Collect: %v", err)
	}

	if len(obs) != 2 {
		t.Fatalf("expected 2 observations, got %d", len(obs))
	}

	// Verify host device observation
	if obs[0].Hostname != "pve-node1" {
		t.Errorf("obs[0].Hostname = %q, want %q", obs[0].Hostname, "pve-node1")
	}
	if obs[0].IPAddress != "192.168.1.100" {
		t.Errorf("obs[0].IPAddress = %q, want %q", obs[0].IPAddress, "192.168.1.100")
	}
	if obs[0].MACAddress != "bc:24:11:22:33:44" {
		t.Errorf("obs[0].MACAddress = %q, want %q", obs[0].MACAddress, "bc:24:11:22:33:44")
	}
	if obs[0].Vendor != "Proxmox" {
		t.Errorf("obs[0].Vendor = %q, want %q", obs[0].Vendor, "Proxmox")
	}
	if obs[0].OS != "Linux" {
		t.Errorf("obs[0].OS = %q, want %q", obs[0].OS, "Linux")
	}
	if obs[0].ProtocolSource != "proxmox" {
		t.Errorf("obs[0].ProtocolSource = %q, want %q", obs[0].ProtocolSource, "proxmox")
	}
	if obs[0].ConfidenceScore != 85 {
		t.Errorf("obs[0].ConfidenceScore = %d, want 85", obs[0].ConfidenceScore)
	}
	if obs[0].ObservedAt.Before(startTime) {
		t.Errorf("obs[0].ObservedAt is before test start time")
	}

	// Verify VM observation with ProviderRef and ParentProviderRef
	if obs[1].Hostname != "vm-web" {
		t.Errorf("obs[1].Hostname = %q, want %q", obs[1].Hostname, "vm-web")
	}
	if obs[1].ProviderRef != "101" {
		t.Errorf("obs[1].ProviderRef = %q, want %q", obs[1].ProviderRef, "101")
	}
	if obs[1].ParentProviderRef != "pve-node1" {
		t.Errorf("obs[1].ParentProviderRef = %q, want %q", obs[1].ParentProviderRef, "pve-node1")
	}

	// Verify provider received the resolved configuration
	if p.lastConfig["api_url"] != "https://192.168.1.100:8006" {
		t.Errorf("provider received unexpected config: %v", p.lastConfig)
	}
}

func TestProviderCollector_Collect_DockerProviderRef(t *testing.T) {
	sourceID := uuid.New()
	p := &mockProvider{
		id:   "docker",
		name: "Docker Engine",
		devices: []sdk.NormalizedDevice{
			{
				Hostname:   "redis-cache",
				IPAddress:  "172.17.0.2",
				DeviceType: "container",
				Vendor:     "Docker",
				Metadata: map[string]interface{}{
					"docker": map[string]interface{}{
						"container_id": "c1a2b3c4d5e6",
						"image":        "redis:7-alpine",
					},
				},
			},
		},
	}

	col := collectors.NewProviderCollector(p, nil)
	target := collectors.DiscoveryTarget{
		SourceID: &sourceID,
	}

	obs, err := col.Collect(context.Background(), target)
	if err != nil {
		t.Fatalf("unexpected error: %v", err)
	}
	if len(obs) != 1 {
		t.Fatalf("expected 1 observation, got %d", len(obs))
	}
	if obs[0].ProviderRef != "c1a2b3c4d5e6" {
		t.Errorf("obs[0].ProviderRef = %q, want %q", obs[0].ProviderRef, "c1a2b3c4d5e6")
	}
	if obs[0].ProtocolSource != "docker" {
		t.Errorf("obs[0].ProtocolSource = %q, want %q", obs[0].ProtocolSource, "docker")
	}
	if obs[0].ConfidenceScore != 85 {
		t.Errorf("obs[0].ConfidenceScore = %d, want 85", obs[0].ConfidenceScore)
	}
}

func TestProviderCollector_Collect_ResolverError(t *testing.T) {
	sourceID := uuid.New()
	p := &mockProvider{id: "proxmox", name: "Proxmox VE"}
	resolver := &mockConfigResolver{
		resolveFunc: func(_ context.Context, _ uuid.UUID, _ string) (sdk.ProviderConfig, error) {
			return nil, errors.New("database connection failed")
		},
	}

	col := collectors.NewProviderCollector(p, resolver)
	target := collectors.DiscoveryTarget{
		SourceID: &sourceID,
	}

	_, err := col.Collect(context.Background(), target)
	if err == nil {
		t.Fatal("expected error when resolver fails, got nil")
	}
}

func TestProviderCollector_Collect_DiscoverError(t *testing.T) {
	sourceID := uuid.New()
	p := &mockProvider{
		id:          "proxmox",
		name:        "Proxmox VE",
		discoverErr: errors.New("authentication failed: HTTP 401"),
	}

	col := collectors.NewProviderCollector(p, nil)
	target := collectors.DiscoveryTarget{
		SourceID: &sourceID,
	}

	_, err := col.Collect(context.Background(), target)
	if err == nil {
		t.Fatal("expected error when Discover fails, got nil")
	}
}

func TestProviderCollector_Collect_NilProvider(t *testing.T) {
	col := collectors.NewProviderCollector(nil, nil)
	sourceID := uuid.New()
	target := collectors.DiscoveryTarget{
		SourceID: &sourceID,
	}

	obs, err := col.Collect(context.Background(), target)
	if err != nil {
		t.Fatalf("unexpected error with nil provider: %v", err)
	}
	if len(obs) != 0 {
		t.Errorf("expected 0 observations with nil provider, got %d", len(obs))
	}
}
