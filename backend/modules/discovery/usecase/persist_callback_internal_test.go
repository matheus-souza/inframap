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
	"github.com/matheussouza/inframap/modules/discovery/dto"
	inventoryRepo "github.com/matheussouza/inframap/modules/inventory/repository"
)

type stubInvRepo struct {
	staged              []db.DeviceStaging
	failCreateStaging   bool
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
	staged := db.DeviceStaging{ID: params.ID, Hostname: params.Hostname, Status: params.Status}
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

		if len(inv.staged) != 1 {
			t.Fatalf("expected 1 staged device, got %d", len(inv.staged))
		}
		if inv.staged[0].Hostname != "discovered-host" {
			t.Errorf("expected hostname discovered-host, got %s", inv.staged[0].Hostname)
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

		if len(inv.staged) != 1 {
			t.Fatalf("expected 1 staged device, got %d", len(inv.staged))
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

		if len(inv.staged) != 1 {
			t.Fatalf("expected 1 staged device, got %d", len(inv.staged))
		}
	})
}
