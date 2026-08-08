package usecase_test

import (
	"bytes"
	"context"
	"errors"
	"log/slog"
	"net/netip"
	"testing"

	"github.com/google/uuid"
	"github.com/matheussouza/inframap/internal/platform/db"
	"github.com/matheussouza/inframap/internal/platform/eventbus"
	"github.com/matheussouza/inframap/modules/discovery/dto"
	"github.com/matheussouza/inframap/modules/discovery/repository"
	"github.com/matheussouza/inframap/modules/discovery/usecase"
	inventoryRepo "github.com/matheussouza/inframap/modules/inventory/repository"
)

type mockDiscRepo struct {
	sources map[uuid.UUID]*dto.DiscoverySourceResponse
	records []*dto.DiscoveryRecordResponse

	failGetSource          bool
	failUpdateSourceStatus bool
}

func newMockDiscRepo() *mockDiscRepo {
	return &mockDiscRepo{
		sources: make(map[uuid.UUID]*dto.DiscoverySourceResponse),
	}
}

func (m *mockDiscRepo) CreateSource(_ context.Context, req *dto.CreateDiscoverySourceRequest) (*dto.DiscoverySourceResponse, error) {
	id := uuid.New()
	resp := &dto.DiscoverySourceResponse{
		ID:         id,
		Name:       req.Name,
		Type:       req.Type,
		Enabled:    *req.Enabled,
		LastStatus: "idle",
	}
	m.sources[id] = resp
	return resp, nil
}

func (m *mockDiscRepo) GetSourceByID(_ context.Context, id uuid.UUID) (*dto.DiscoverySourceResponse, error) {
	if m.failGetSource {
		return nil, errors.New("db error")
	}
	src, exists := m.sources[id]
	if !exists {
		return nil, repository.ErrSourceNotFound
	}
	return src, nil
}

func (m *mockDiscRepo) ListSources(_ context.Context) ([]*dto.DiscoverySourceResponse, error) {
	res := make([]*dto.DiscoverySourceResponse, 0, len(m.sources))
	for _, s := range m.sources {
		res = append(res, s)
	}
	return res, nil
}

func (m *mockDiscRepo) UpdateSourceStatus(_ context.Context, id uuid.UUID, status string) (*dto.DiscoverySourceResponse, error) {
	if m.failUpdateSourceStatus {
		return nil, errors.New("db status update error")
	}
	src, exists := m.sources[id]
	if !exists {
		return nil, repository.ErrSourceNotFound
	}
	src.LastStatus = status
	return src, nil
}

func (m *mockDiscRepo) DeleteSource(_ context.Context, id uuid.UUID) error {
	delete(m.sources, id)
	return nil
}

func (m *mockDiscRepo) UpsertRecord(_ context.Context, deviceID, sourceID uuid.UUID, matchedBy string, rawPayload map[string]interface{}) (*dto.DiscoveryRecordResponse, error) {
	rec := &dto.DiscoveryRecordResponse{
		ID:                uuid.New(),
		DeviceID:          deviceID,
		DiscoverySourceID: sourceID,
		MatchedBy:         matchedBy,
		RawPayload:        rawPayload,
	}
	m.records = append(m.records, rec)
	return rec, nil
}

func (m *mockDiscRepo) ListRecordsByDevice(_ context.Context, deviceID uuid.UUID) ([]*dto.DiscoveryRecordResponse, error) {
	var res []*dto.DiscoveryRecordResponse
	for _, r := range m.records {
		if r.DeviceID == deviceID {
			res = append(res, r)
		}
	}
	return res, nil
}

type mockInvRepo struct {
	devices []db.Device
	staged  []db.DeviceStaging

	failListDevices         bool
	failGetDeviceByID       bool
	failUpdateDevice        bool
	failCreateDevice        bool
	failCreateStagingDevice bool
}

func newMockInvRepo() *mockInvRepo {
	return &mockInvRepo{}
}

func (m *mockInvRepo) CreateDevice(_ context.Context, params db.CreateDeviceParams) (*db.Device, error) {
	if m.failCreateDevice {
		return nil, errors.New("failed to create device")
	}
	dev := db.Device{
		ID:         params.ID,
		Hostname:   params.Hostname,
		IpAddress:  params.IpAddress,
		MacAddress: params.MacAddress,
		DeviceType: params.DeviceType,
		Status:     params.Status,
		Metadata:   params.Metadata,
	}
	m.devices = append(m.devices, dev)
	return &dev, nil
}

func (m *mockInvRepo) GetDeviceByID(_ context.Context, id uuid.UUID, _ bool) (*db.Device, error) {
	if m.failGetDeviceByID {
		return nil, errors.New("db error")
	}
	for i := range m.devices {
		if m.devices[i].ID == id {
			return &m.devices[i], nil
		}
	}
	return nil, inventoryRepo.ErrDeviceNotFound
}

func (m *mockInvRepo) ListDevices(_ context.Context, _, _ string, _, _ int32, _ bool) ([]db.Device, int64, error) {
	if m.failListDevices {
		return nil, 0, errors.New("db list error")
	}
	return m.devices, int64(len(m.devices)), nil
}

func (m *mockInvRepo) UpdateDevice(_ context.Context, params db.UpdateDeviceParams) (*db.Device, error) {
	if m.failUpdateDevice {
		return nil, errors.New("db update error")
	}
	d, err := m.GetDeviceByID(context.Background(), params.ID, true)
	if err != nil {
		return nil, err
	}
	d.Hostname = params.Hostname
	d.IpAddress = params.IpAddress
	d.MacAddress = params.MacAddress
	return d, nil
}

func (m *mockInvRepo) SoftDeleteDevice(_ context.Context, _ uuid.UUID) error {
	return nil
}

func (m *mockInvRepo) CreateStagingDevice(_ context.Context, params db.CreateStagingDeviceParams) (*db.DeviceStaging, error) {
	if m.failCreateStagingDevice {
		return nil, errors.New("failed to create staging device")
	}
	staged := db.DeviceStaging{
		ID:         params.ID,
		Hostname:   params.Hostname,
		Status:     params.Status,
		RawPayload: params.RawPayload,
	}
	m.staged = append(m.staged, staged)
	return &staged, nil
}

func (m *mockInvRepo) GetStagingDeviceByID(_ context.Context, id uuid.UUID) (*db.DeviceStaging, error) {
	for i := range m.staged {
		if m.staged[i].ID == id {
			return &m.staged[i], nil
		}
	}
	return nil, inventoryRepo.ErrStagingDeviceNotFound
}

func (m *mockInvRepo) ListStagingDevices(_ context.Context, _ string, _, _ int32) ([]db.DeviceStaging, int64, error) {
	return m.staged, int64(len(m.staged)), nil
}

func (m *mockInvRepo) UpdateStagingDeviceStatus(_ context.Context, _ uuid.UUID, _ string) error {
	return nil
}

func (m *mockInvRepo) CreateSubnet(_ context.Context, _ db.CreateSubnetParams) (*db.Subnet, error) {
	return nil, nil
}

func (m *mockInvRepo) ListSubnets(_ context.Context) ([]db.Subnet, error) {
	return nil, nil
}

func TestDiscoveryUseCase_Unit(t *testing.T) {
	discRepo := newMockDiscRepo()
	invRepo := newMockInvRepo()
	bus := eventbus.NewInMemoryEventBus(1, 10)
	defer func() { _ = bus.Close() }()
	buf := &bytes.Buffer{}
	logger := slog.New(slog.NewTextHandler(buf, nil))

	uc := usecase.NewDefaultDiscoveryUseCase(discRepo, invRepo, bus, logger)

	ctx := context.Background()

	t.Run("CreateSource Validation Failure", func(t *testing.T) {
		req := &dto.CreateDiscoverySourceRequest{Name: ""}
		_, err := uc.CreateSource(ctx, req)
		if !errors.Is(err, usecase.ErrInvalidInput) {
			t.Errorf("expected ErrInvalidInput, got %v", err)
		}
	})

	t.Run("CreateSource Success", func(t *testing.T) {
		req := &dto.CreateDiscoverySourceRequest{
			Name: "Local Proxmox VE",
			Type: "proxmox",
		}
		src, err := uc.CreateSource(ctx, req)
		if err != nil {
			t.Fatalf("expected nil error, got %v", err)
		}
		if src.Name != "Local Proxmox VE" {
			t.Errorf("expected name Local Proxmox VE, got %s", src.Name)
		}
	})

	t.Run("GetSourceByID Invalid UUID", func(t *testing.T) {
		_, err := uc.GetSourceByID(ctx, "invalid-uuid")
		if !errors.Is(err, usecase.ErrInvalidUUID) {
			t.Errorf("expected ErrInvalidUUID, got %v", err)
		}
	})

	t.Run("GetSourceByID NotFound", func(t *testing.T) {
		_, err := uc.GetSourceByID(ctx, uuid.New().String())
		if !errors.Is(err, repository.ErrSourceNotFound) {
			t.Errorf("expected ErrSourceNotFound, got %v", err)
		}
	})

	t.Run("TriggerRun Invalid UUID", func(t *testing.T) {
		_, err := uc.TriggerRun(ctx, "not-a-valid-uuid")
		if !errors.Is(err, usecase.ErrInvalidUUID) {
			t.Errorf("expected ErrInvalidUUID, got %v", err)
		}
	})

	t.Run("TriggerRun status update failure", func(t *testing.T) {
		sources, _ := uc.ListSources(ctx)
		discRepo.failUpdateSourceStatus = true
		_, err := uc.TriggerRun(ctx, sources[0].ID.String())
		if err == nil {
			t.Fatal("expected error when UpdateSourceStatus fails")
		}
		discRepo.failUpdateSourceStatus = false
	})

	t.Run("TriggerRun updates status to running then resets to idle", func(t *testing.T) {
		sources, _ := uc.ListSources(ctx)
		if len(sources) == 0 {
			t.Fatal("expected at least 1 source")
		}
		srcID := sources[0].ID.String()

		res, err := uc.TriggerRun(ctx, srcID)
		if err != nil {
			t.Fatalf("expected nil error on TriggerRun, got %v", err)
		}
		if res.LastStatus != "idle" {
			t.Errorf("expected status idle upon completion, got %s", res.LastStatus)
		}
	})

	t.Run("IngestNormalizedDevice Matches Existing Device & Reconciles", func(t *testing.T) {
		sources, _ := uc.ListSources(ctx)
		srcID := sources[0].ID

		// Pre-create existing device in inventory
		ip, _ := netip.ParseAddr("192.168.1.50")
		dev, _ := invRepo.CreateDevice(ctx, db.CreateDeviceParams{
			ID:         uuid.New(),
			Hostname:   "existing-pve-host",
			IpAddress:  &ip,
			DeviceType: "unknown",
			Status:     "active",
		})

		norm := &dto.NormalizedDeviceDTO{
			Hostname:     "existing-pve-host",
			IPAddress:    "192.168.1.50",
			DeviceType:   "hypervisor",
			Manufacturer: "Dell Inc.",
			Model:        "PowerEdge R740",
			SerialNumber: "SN-DELL-1234",
		}

		rec, err := uc.IngestNormalizedDevice(ctx, srcID, norm)
		if err != nil {
			t.Fatalf("expected nil error on IngestNormalizedDevice, got %v", err)
		}
		if rec.MatchedBy != "hostname_ip" {
			t.Errorf("expected matchedBy hostname_ip, got %s", rec.MatchedBy)
		}
		if rec.DeviceID != dev.ID {
			t.Errorf("expected targetDeviceID %s, got %s", dev.ID, rec.DeviceID)
		}
	})

	t.Run("IngestNormalizedDevice Auto-Approves Trusted Provider", func(t *testing.T) {
		sources, _ := uc.ListSources(ctx)
		srcID := sources[0].ID

		norm := &dto.NormalizedDeviceDTO{
			Hostname:     "pve-node-01",
			IPAddress:    "192.168.1.100",
			MACAddress:   "aa:bb:cc:dd:ee:ff",
			Manufacturer: "HP",
			Model:        "ProLiant DL360",
			SerialNumber: "SN-9988",
			DeviceType:   "hypervisor",
			RawPayload:   map[string]interface{}{"cpus": 16},
		}

		rec, err := uc.IngestNormalizedDevice(ctx, srcID, norm)
		if err != nil {
			t.Fatalf("expected nil error on IngestNormalizedDevice, got %v", err)
		}
		if rec.MatchedBy != "new_discovery" {
			t.Errorf("expected matchedBy new_discovery, got %s", rec.MatchedBy)
		}
	})

	t.Run("IngestNormalizedDevice Invalid IP and MAC Warning Logs", func(t *testing.T) {
		sources, _ := uc.ListSources(ctx)
		srcID := sources[0].ID

		norm := &dto.NormalizedDeviceDTO{
			Hostname:   "invalid-ip-host",
			IPAddress:  "invalid-ip-addr",
			MACAddress: "invalid-mac-addr",
			DeviceType: "server",
		}

		rec, err := uc.IngestNormalizedDevice(ctx, srcID, norm)
		if err != nil {
			t.Fatalf("expected nil error on IngestNormalizedDevice, got %v", err)
		}
		if rec == nil {
			t.Fatal("expected non-nil discovery record")
		}
	})

	t.Run("IngestNormalizedDevice Generic Sweep Routes to Staging", func(t *testing.T) {
		sweepSource, _ := uc.CreateSource(ctx, &dto.CreateDiscoverySourceRequest{
			Name: "ARP Local Sweep",
			Type: "arp_sweep",
		})

		norm := &dto.NormalizedDeviceDTO{
			Hostname:   "unverified-host",
			IPAddress:  "192.168.1.200",
			MACAddress: "11:22:33:44:55:66",
		}

		rec, err := uc.IngestNormalizedDevice(ctx, sweepSource.ID, norm)
		if err != nil {
			t.Fatalf("expected nil error on IngestNormalizedDevice, got %v", err)
		}
		if rec.MatchedBy != "new_discovery" {
			t.Errorf("expected matchedBy new_discovery, got %s", rec.MatchedBy)
		}
	})

	t.Run("IngestNormalizedDevice Error Failures", func(t *testing.T) {
		sources, _ := uc.ListSources(ctx)
		srcID := sources[0].ID

		// Fail fetch source
		discRepo.failGetSource = true
		_, err := uc.IngestNormalizedDevice(ctx, srcID, &dto.NormalizedDeviceDTO{Hostname: "host1"})
		if err == nil {
			t.Error("expected error when GetSourceByID fails")
		}
		discRepo.failGetSource = false

		// Fail list devices
		invRepo.failListDevices = true
		_, err = uc.IngestNormalizedDevice(ctx, srcID, &dto.NormalizedDeviceDTO{Hostname: "host2"})
		if err == nil {
			t.Error("expected error when ListDevices fails")
		}
		invRepo.failListDevices = false

		// Fail create device
		trustedSource, _ := uc.CreateSource(ctx, &dto.CreateDiscoverySourceRequest{Name: "ProxmoxTest", Type: "proxmox"})
		invRepo.failCreateDevice = true
		normNew := &dto.NormalizedDeviceDTO{
			Hostname:   "unique-unmatched-host-12345",
			IPAddress:  "10.254.254.254",
			DeviceType: "server",
		}
		_, err = uc.IngestNormalizedDevice(ctx, trustedSource.ID, normNew)
		if err == nil {
			t.Error("expected error when CreateDevice fails")
		}
		invRepo.failCreateDevice = false

		// Fail create staging device
		sweepSource, _ := uc.CreateSource(ctx, &dto.CreateDiscoverySourceRequest{Name: "Sweep2", Type: "icmp_sweep"})
		invRepo.failCreateStagingDevice = true
		_, err = uc.IngestNormalizedDevice(ctx, sweepSource.ID, &dto.NormalizedDeviceDTO{Hostname: "staged-host"})
		if err == nil {
			t.Error("expected error when CreateStagingDevice fails")
		}
		invRepo.failCreateStagingDevice = false
	})

	t.Run("ListRecordsByDevice Success and Invalid UUID", func(t *testing.T) {
		_, err := uc.ListRecordsByDevice(ctx, "invalid-uuid")
		if !errors.Is(err, usecase.ErrInvalidUUID) {
			t.Errorf("expected ErrInvalidUUID, got %v", err)
		}

		recs, err := uc.ListRecordsByDevice(ctx, uuid.New().String())
		if err != nil {
			t.Fatalf("expected nil error on ListRecordsByDevice, got %v", err)
		}
		if len(recs) != 0 {
			t.Errorf("expected 0 records, got %d", len(recs))
		}
	})

	t.Run("TriggerScan Validations and Execution", func(t *testing.T) {
		_, err := uc.TriggerScan(ctx, nil)
		if !errors.Is(err, usecase.ErrInvalidInput) {
			t.Errorf("expected ErrInvalidInput for nil request, got %v", err)
		}

		_, err = uc.TriggerScan(ctx, &dto.TriggerScanRequest{CIDR: ""})
		if err == nil {
			t.Fatal("expected error for empty CIDR, got nil")
		}

		_, err = uc.TriggerScan(ctx, &dto.TriggerScanRequest{CIDR: "10.0.0.0/8"})
		if err == nil {
			t.Fatal("expected error for oversized CIDR /8, got nil")
		}

		res, err := uc.TriggerScan(ctx, &dto.TriggerScanRequest{CIDR: "192.168.1.0/30"})
		if err != nil {
			t.Fatalf("expected nil error on TriggerScan, got %v", err)
		}
		if res.CIDR != "192.168.1.0/30" {
			t.Errorf("expected CIDR '192.168.1.0/30', got %q", res.CIDR)
		}
	})
}

