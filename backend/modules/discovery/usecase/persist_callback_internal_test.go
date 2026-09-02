package usecase

import (
	"bytes"
	"context"
	"errors"
	"log/slog"
	"testing"

	"github.com/google/uuid"
	"github.com/matheussouza/inframap/internal/platform/db"
	"github.com/matheussouza/inframap/internal/platform/eventbus"
	"github.com/matheussouza/inframap/modules/discovery/collectors"
	"github.com/matheussouza/inframap/modules/discovery/dto"
	inventoryRepo "github.com/matheussouza/inframap/modules/inventory/repository"
)

type stubInvRepo struct {
	capturedParams    []db.CreateStagingDeviceParams
	staged            []db.DeviceStaging
	failCreateStaging bool
}

func (s *stubInvRepo) CreateDevice(_ context.Context, _ db.CreateDeviceParams) (*db.Device, error) {
	return nil, nil
}
func (s *stubInvRepo) GetDeviceByID(_ context.Context, _ uuid.UUID, _ bool) (*db.Device, error) {
	return nil, inventoryRepo.ErrDeviceNotFound
}
func (s *stubInvRepo) ListDevices(_ context.Context, _, _ string, _, _ int32, _ bool) ([]db.Device, int64, error) {
	return nil, 0, nil
}
func (s *stubInvRepo) UpdateDevice(_ context.Context, _ db.UpdateDeviceParams) (*db.Device, error) {
	return nil, nil
}
func (s *stubInvRepo) SoftDeleteDevice(_ context.Context, _ uuid.UUID) error { return nil }
func (s *stubInvRepo) CreateStagingDevice(_ context.Context, params db.CreateStagingDeviceParams) (*db.DeviceStaging, error) {
	if s.failCreateStaging {
		return nil, errors.New("staging insert failed")
	}
	s.capturedParams = append(s.capturedParams, params)
	staged := db.DeviceStaging{
		ID:         params.ID,
		Hostname:   params.Hostname,
		IpAddress:  params.IpAddress,
		MacAddress: params.MacAddress,
		DeviceType: params.DeviceType,
		RawPayload: params.RawPayload,
		Status:     params.Status,
	}
	s.staged = append(s.staged, staged)
	return &staged, nil
}
func (s *stubInvRepo) GetStagingDeviceByID(_ context.Context, _ uuid.UUID) (*db.DeviceStaging, error) {
	return nil, nil
}
func (s *stubInvRepo) ListStagingDevices(_ context.Context, _ string, _, _ int32) ([]db.DeviceStaging, int64, error) {
	return nil, 0, nil
}
func (s *stubInvRepo) UpdateStagingDeviceStatus(_ context.Context, _ uuid.UUID, _ string) error {
	return nil
}
func (s *stubInvRepo) CreateSubnet(_ context.Context, _ db.CreateSubnetParams) (*db.Subnet, error) {
	return nil, nil
}
func (s *stubInvRepo) ListSubnets(_ context.Context) ([]db.Subnet, error) { return nil, nil }

func TestPersistDiscoveredDevice(t *testing.T) {
	t.Run("matched device is skipped", func(_ *testing.T) {
		uc := &DefaultDiscoveryUseCase{}
		uc.persistDiscoveredDevice(context.Background(), &dto.NormalizedDeviceDTO{}, "arp", true)
	})

	t.Run("nil invRepo logs error", func(t *testing.T) {
		buf := &bytes.Buffer{}
		logger := slog.New(slog.NewTextHandler(buf, nil))
		uc := &DefaultDiscoveryUseCase{logger: logger}

		uc.persistDiscoveredDevice(context.Background(), &dto.NormalizedDeviceDTO{Hostname: "h1"}, "arp", false)

		if !bytes.Contains(buf.Bytes(), []byte("inventory repository is not configured")) {
			t.Error("expected log about missing inventory repository")
		}
	})

	t.Run("stages new device with IP and MAC", func(t *testing.T) {
		inv := &stubInvRepo{}
		bus := eventbus.NewInMemoryEventBus(1, 16)
		defer func() { _ = bus.Close() }()

		uc := &DefaultDiscoveryUseCase{
			invRepo:  inv,
			eventBus: bus,
		}

		norm := &dto.NormalizedDeviceDTO{
			Hostname:   "discovered-host",
			IPAddress:  "10.0.0.5",
			MACAddress: "aa:bb:cc:dd:ee:ff",
			DeviceType: "host",
			RawPayload: map[string]interface{}{"source": "arp"},
		}

		uc.persistDiscoveredDevice(context.Background(), norm, "arp", false)

		if len(inv.capturedParams) != 1 {
			t.Fatalf("expected 1 staged device, got %d", len(inv.capturedParams))
		}
		p := inv.capturedParams[0]
		if p.Hostname != "discovered-host" {
			t.Errorf("hostname = %q, want %q", p.Hostname, "discovered-host")
		}
		if p.IpAddress == nil || p.IpAddress.String() != "10.0.0.5" {
			t.Errorf("ip_address = %v, want 10.0.0.5", p.IpAddress)
		}
		if p.MacAddress.String() != "aa:bb:cc:dd:ee:ff" {
			t.Errorf("mac_address = %q, want aa:bb:cc:dd:ee:ff", p.MacAddress.String())
		}
		if p.DeviceType != "host" {
			t.Errorf("device_type = %q, want %q", p.DeviceType, "host")
		}
		if p.Status != "discovered" {
			t.Errorf("status = %q, want %q", p.Status, "discovered")
		}
		if len(p.RawPayload) == 0 {
			t.Error("raw_payload should not be empty")
		}
	})

	t.Run("stages device without IP or MAC", func(t *testing.T) {
		inv := &stubInvRepo{}
		uc := &DefaultDiscoveryUseCase{invRepo: inv}

		norm := &dto.NormalizedDeviceDTO{
			Hostname:   "no-addr-host",
			DeviceType: "unknown",
		}

		uc.persistDiscoveredDevice(context.Background(), norm, "snmp", false)

		if len(inv.capturedParams) != 1 {
			t.Fatalf("expected 1 staged device, got %d", len(inv.capturedParams))
		}
		p := inv.capturedParams[0]
		if p.Hostname != "no-addr-host" {
			t.Errorf("hostname = %q, want %q", p.Hostname, "no-addr-host")
		}
		if p.IpAddress != nil {
			t.Errorf("ip_address should be nil, got %v", p.IpAddress)
		}
		if p.MacAddress != nil {
			t.Errorf("mac_address should be nil, got %v", p.MacAddress)
		}
		if p.DeviceType != "unknown" {
			t.Errorf("device_type = %q, want %q", p.DeviceType, "unknown")
		}
	})

	t.Run("CreateStagingDevice failure logs error", func(t *testing.T) {
		buf := &bytes.Buffer{}
		logger := slog.New(slog.NewTextHandler(buf, nil))
		inv := &stubInvRepo{failCreateStaging: true}

		uc := &DefaultDiscoveryUseCase{
			invRepo: inv,
			logger:  logger,
		}

		norm := &dto.NormalizedDeviceDTO{
			Hostname:  "fail-host",
			IPAddress: "10.0.0.1",
		}

		uc.persistDiscoveredDevice(context.Background(), norm, "icmp", false)

		if !bytes.Contains(buf.Bytes(), []byte("failed to persist discovered device")) {
			t.Error("expected error log about staging failure")
		}
	})

	t.Run("nil eventBus skips publish", func(t *testing.T) {
		inv := &stubInvRepo{}
		uc := &DefaultDiscoveryUseCase{invRepo: inv}

		norm := &dto.NormalizedDeviceDTO{
			Hostname:   "no-bus-host",
			IPAddress:  "10.0.0.2",
			MACAddress: "11:22:33:44:55:66",
			DeviceType: "host",
		}

		uc.persistDiscoveredDevice(context.Background(), norm, "arp", false)

		if len(inv.capturedParams) != 1 {
			t.Fatalf("expected 1 staged device, got %d", len(inv.capturedParams))
		}
		p := inv.capturedParams[0]
		if p.DeviceType != "host" {
			t.Errorf("device_type = %q, want %q", p.DeviceType, "host")
		}
		if p.IpAddress == nil || p.IpAddress.String() != "10.0.0.2" {
			t.Errorf("ip_address = %v, want 10.0.0.2", p.IpAddress)
		}
	})
}

func TestBuildDeviceMetadata(t *testing.T) {
	t.Run("stores the canonical identity alongside the collector payload", func(t *testing.T) {
		norm := &dto.NormalizedDeviceDTO{
			RawPayload: map[string]interface{}{
				"docker": map[string]interface{}{"image": "redis:7-alpine"},
			},
			ProviderRef:       &collectors.ProviderRef{Provider: "docker", Scope: "lab", Kind: "container", NativeID: "abc123"},
			ParentProviderRef: &collectors.ProviderRef{Provider: "docker", Scope: "lab", Kind: "engine", NativeID: "daemon-1"},
		}

		metadata := buildDeviceMetadata(norm)

		// The Matcher reads Tier 0 from this key and the partial unique index
		// uq_devices_provider_ref is built on metadata->>'provider_ref', so it must be the
		// canonical key as a plain string.
		if got := metadata["provider_ref"]; got != "docker:lab:container:abc123" {
			t.Errorf("provider_ref = %v, want docker:lab:container:abc123", got)
		}
		if got := metadata["parent_provider_ref"]; got != "docker:lab:engine:daemon-1" {
			t.Errorf("parent_provider_ref = %v, want docker:lab:engine:daemon-1", got)
		}
		if _, ok := metadata["docker"]; !ok {
			t.Error("expected the collector payload to be preserved")
		}
	})

	t.Run("leaves network sweep payloads untouched", func(t *testing.T) {
		norm := &dto.NormalizedDeviceDTO{RawPayload: map[string]interface{}{"arp": "seen"}}

		metadata := buildDeviceMetadata(norm)

		if _, ok := metadata["provider_ref"]; ok {
			t.Error("expected no provider_ref for an observation without provider identity")
		}
		if metadata["arp"] != "seen" {
			t.Errorf("arp = %v, want seen", metadata["arp"])
		}
	})

	t.Run("does not mutate the original payload", func(t *testing.T) {
		payload := map[string]interface{}{"docker": "x"}
		norm := &dto.NormalizedDeviceDTO{
			RawPayload:  payload,
			ProviderRef: &collectors.ProviderRef{Provider: "docker", Scope: "lab", Kind: "container", NativeID: "abc"},
		}

		buildDeviceMetadata(norm)

		if _, ok := payload["provider_ref"]; ok {
			t.Error("buildDeviceMetadata must not write back into the collector payload")
		}
	})

	t.Run("ignores a partial reference", func(t *testing.T) {
		norm := &dto.NormalizedDeviceDTO{
			RawPayload:  map[string]interface{}{},
			ProviderRef: &collectors.ProviderRef{Scope: "lab", Kind: "container"},
		}

		if _, ok := buildDeviceMetadata(norm)["provider_ref"]; ok {
			t.Error("expected a reference without provider and native id to be dropped")
		}
	})
}
