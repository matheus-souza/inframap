package usecase_test

import (
	"bytes"
	"context"
	"encoding/json"
	"errors"
	"fmt"
	"log/slog"
	"net"
	"net/netip"
	"strings"
	"testing"
	"time"

	"github.com/google/uuid"
	"github.com/jackc/pgx/v5/pgtype"
	"github.com/matheussouza/inframap/internal/platform/db"
	"github.com/matheussouza/inframap/internal/platform/eventbus"
	"github.com/matheussouza/inframap/internal/platform/sdk"
	"github.com/matheussouza/inframap/modules/discovery/dto"
	"github.com/matheussouza/inframap/modules/discovery/repository"
	"github.com/matheussouza/inframap/modules/discovery/usecase"
	inventoryRepo "github.com/matheussouza/inframap/modules/inventory/repository"
)

type mockDiscRepo struct {
	sources             map[uuid.UUID]*dto.DiscoverySourceResponse
	records             []*dto.DiscoveryRecordResponse
	runs                []*db.CreateCollectorRunParams
	rawCollectorConfigs map[uuid.UUID]map[string]map[string]interface{}

	failGetSource          bool
	failUpdateSourceStatus bool
	failCreateCollectorRun bool
	failPurge              bool
	failListRunsPaged      bool
	purgedCount            int64
	lastCutoff             time.Time
	lastBatchSize          int
	lastLimit              int
	lastOffset             int
	resolveConfigFn        func(ctx context.Context, id uuid.UUID, collectorType string) (sdk.ProviderConfig, error)
}

func newMockDiscRepo() *mockDiscRepo {
	return &mockDiscRepo{
		sources:             make(map[uuid.UUID]*dto.DiscoverySourceResponse),
		runs:                make([]*db.CreateCollectorRunParams, 0),
		rawCollectorConfigs: make(map[uuid.UUID]map[string]map[string]interface{}),
	}
}

func (m *mockDiscRepo) CreateSource(_ context.Context, req *dto.CreateDiscoverySourceRequest) (*dto.DiscoverySourceResponse, error) {
	id := uuid.New()
	cols := make([]dto.CollectorResponse, len(req.Collectors))
	if m.rawCollectorConfigs[id] == nil {
		m.rawCollectorConfigs[id] = make(map[string]map[string]interface{})
	}
	for i, c := range req.Collectors {
		cols[i] = dto.CollectorResponse{
			ID:            uuid.New(),
			CollectorType: c.Type,
			Enabled:       true,
		}
		if c.Config != nil {
			m.rawCollectorConfigs[id][c.Type] = c.Config
		}
	}
	resp := &dto.DiscoverySourceResponse{
		ID:         id,
		Name:       req.Name,
		Type:       req.Type,
		Enabled:    *req.Enabled,
		Collectors: cols,
		LastStatus: "idle",
	}
	if req.ScheduleCron != "" {
		cron := req.ScheduleCron
		resp.ScheduleCron = &cron
	}
	m.sources[id] = resp
	return resp, nil
}

func (m *mockDiscRepo) GetRawCollectorConfigs(_ context.Context, sourceID uuid.UUID) (map[string]map[string]interface{}, error) {
	cfgs, exists := m.rawCollectorConfigs[sourceID]
	if !exists {
		return make(map[string]map[string]interface{}), nil
	}
	copied := make(map[string]map[string]interface{})
	for k, v := range cfgs {
		sub := make(map[string]interface{})
		for sk, sv := range v {
			sub[sk] = sv
		}
		copied[k] = sub
	}
	return copied, nil
}

func (m *mockDiscRepo) UpdateSource(_ context.Context, id uuid.UUID, req *dto.UpdateDiscoverySourceRequest) (*dto.DiscoverySourceResponse, error) {
	src, exists := m.sources[id]
	if !exists {
		return nil, repository.ErrSourceNotFound
	}
	src.Name = req.Name
	if req.Enabled != nil {
		src.Enabled = *req.Enabled
	}
	if req.ScheduleCron != "" {
		cron := req.ScheduleCron
		src.ScheduleCron = &cron
	} else {
		src.ScheduleCron = nil
	}
	cols := make([]dto.CollectorResponse, len(req.Collectors))
	if m.rawCollectorConfigs[id] == nil {
		m.rawCollectorConfigs[id] = make(map[string]map[string]interface{})
	}
	for i, c := range req.Collectors {
		cols[i] = dto.CollectorResponse{
			ID:            uuid.New(),
			CollectorType: c.Type,
			Enabled:       true,
		}
		if c.Config != nil {
			m.rawCollectorConfigs[id][c.Type] = c.Config
		}
	}
	src.Collectors = cols
	src.UpdatedAt = time.Now()
	return src, nil
}

func (m *mockDiscRepo) GetSourceByID(_ context.Context, id uuid.UUID) (*dto.DiscoverySourceResponse, error) {
	if m.failGetSource {
		return nil, errors.New("db error")
	}
	src, exists := m.sources[id]
	if !exists {
		return nil, repository.ErrSourceNotFound
	}
	if src.Collectors == nil {
		src.Collectors = make([]dto.CollectorResponse, 0)
	}
	return src, nil
}

func (m *mockDiscRepo) ListSources(_ context.Context) ([]*dto.DiscoverySourceResponse, error) {
	res := make([]*dto.DiscoverySourceResponse, 0, len(m.sources))
	for _, s := range m.sources {
		if s.Collectors == nil {
			s.Collectors = make([]dto.CollectorResponse, 0)
		}
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

func (m *mockDiscRepo) GetDeletionImpact(_ context.Context, id uuid.UUID) (*dto.DiscoverySourceDeletionImpact, error) {
	src, exists := m.sources[id]
	if !exists {
		return nil, repository.ErrSourceNotFound
	}
	return &dto.DiscoverySourceDeletionImpact{
		CollectorsHalted: len(src.Collectors),
		DevicesUnlinked:  2,
	}, nil
}

func (m *mockDiscRepo) SoftDeleteSource(_ context.Context, id uuid.UUID) (*dto.DiscoverySourceDeletionImpact, error) {
	src, exists := m.sources[id]
	if !exists {
		return nil, repository.ErrSourceNotFound
	}
	impact := &dto.DiscoverySourceDeletionImpact{
		CollectorsHalted: len(src.Collectors),
		DevicesUnlinked:  2,
	}
	delete(m.sources, id)
	return impact, nil
}

func (m *mockDiscRepo) DeleteSource(ctx context.Context, id uuid.UUID) error {
	_, err := m.SoftDeleteSource(ctx, id)
	return err
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

func (m *mockDiscRepo) CreateCollectorRun(_ context.Context, run *db.CreateCollectorRunParams) error {
	if m.failCreateCollectorRun {
		return errors.New("db collector run error")
	}
	m.runs = append(m.runs, run)
	return nil
}

func (m *mockDiscRepo) ListRunsBySourceID(_ context.Context, sourceID uuid.UUID, limit int) ([]*dto.CollectorRunResponse, error) {
	var res []*dto.CollectorRunResponse
	for _, r := range m.runs {
		if r.SourceID == sourceID {
			var errMsg string
			if r.ErrorMessage.Valid {
				errMsg = r.ErrorMessage.String
			}
			res = append(res, &dto.CollectorRunResponse{
				ID:            r.ID,
				SourceID:      r.SourceID,
				CollectorType: r.CollectorType,
				Status:        r.Status,
				DevicesFound:  int(r.DevicesFound),
				DurationMs:    int64(r.DurationMs),
				ErrorMessage:  errMsg,
				StartedAt:     r.StartedAt.Time,
				FinishedAt:    r.FinishedAt.Time,
			})
			if limit > 0 && len(res) >= limit {
				break
			}
		}
	}
	return res, nil
}

func (m *mockDiscRepo) ListRunsBySourceIDPaged(_ context.Context, sourceID uuid.UUID, limit, offset int) ([]*dto.CollectorRunResponse, int64, error) {
	m.lastLimit = limit
	m.lastOffset = offset
	if m.failListRunsPaged {
		return nil, 0, errors.New("db error listing runs paged")
	}
	res := make([]*dto.CollectorRunResponse, 0)
	var matched []*db.CreateCollectorRunParams
	for _, r := range m.runs {
		if r.SourceID == sourceID {
			matched = append(matched, r)
		}
	}
	total := int64(len(matched))
	if offset < len(matched) {
		matched = matched[offset:]
	} else {
		matched = nil
	}
	for _, r := range matched {
		var errMsg string
		if r.ErrorMessage.Valid {
			errMsg = r.ErrorMessage.String
		}
		res = append(res, &dto.CollectorRunResponse{
			ID:            r.ID,
			SourceID:      r.SourceID,
			CollectorType: r.CollectorType,
			Status:        r.Status,
			DevicesFound:  int(r.DevicesFound),
			DurationMs:    int64(r.DurationMs),
			ErrorMessage:  errMsg,
			StartedAt:     r.StartedAt.Time,
			FinishedAt:    r.FinishedAt.Time,
		})
		if limit > 0 && len(res) >= limit {
			break
		}
	}
	return res, total, nil
}

func (m *mockDiscRepo) PurgeOldCollectorRuns(_ context.Context, cutoff time.Time, batchSize int) (int64, error) {
	m.lastCutoff = cutoff
	m.lastBatchSize = batchSize
	if m.failPurge {
		return 0, errors.New("db purge failure")
	}
	return m.purgedCount, nil
}

func (m *mockDiscRepo) ResolveCollectorConfig(ctx context.Context, id uuid.UUID, collectorType string) (sdk.ProviderConfig, error) {
	if m.resolveConfigFn != nil {
		return m.resolveConfigFn(ctx, id, collectorType)
	}
	if collectorType == "docker" {
		// Point the Docker collector at a closed port. Left unconfigured it falls back to
		// the local daemon socket, so these tests would otherwise pass or fail depending on
		// whether the machine running them happens to have Docker installed — which is the
		// difference between a developer laptop and a CI runner (guideline #185).
		return sdk.ProviderConfig{"tcp_url": "tcp://127.0.0.1:1"}, nil
	}
	return sdk.ProviderConfig{}, nil
}

type mockInvRepo struct {
	devicesByProviderRef map[string]db.Device
	pendingChildren      map[string][]db.Device
	parentAssignments    map[uuid.UUID]uuid.UUID

	devicesByProviderScope map[string][]db.Device
	absenceCalls           []uuid.UUID
	absenceCounts          map[uuid.UUID]int16

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

func (m *mockInvRepo) RestoreDevice(_ context.Context, params db.RestoreDeviceParams) (*db.Device, error) {
	d, err := m.GetDeviceByID(context.Background(), params.ID, true)
	if err != nil {
		return nil, err
	}
	d.DeletedAt = pgtype.Timestamptz{Valid: false}
	d.Status = "active"
	return d, nil
}

func (m *mockInvRepo) FindPendingStagingDevice(_ context.Context, params db.FindPendingStagingDeviceParams) (*db.DeviceStaging, error) {
	for i := range m.staged {
		st := &m.staged[i]
		if st.Status != "pending" && st.Status != "discovered" {
			continue
		}
		if params.IpAddress != nil && st.IpAddress != nil && *params.IpAddress == *st.IpAddress {
			return st, nil
		}
		if len(params.MacAddress) > 0 && len(st.MacAddress) > 0 && params.MacAddress.String() == st.MacAddress.String() {
			return st, nil
		}
		if params.MatchedDeviceID.Valid && len(st.RawPayload) > 0 {
			var rawMap map[string]interface{}
			if err := json.Unmarshal(st.RawPayload, &rawMap); err == nil {
				if id, ok := rawMap["matched_device_id"].(string); ok && id == params.MatchedDeviceID.String {
					return st, nil
				}
				if meta, ok := rawMap["metadata"].(map[string]interface{}); ok {
					if id, ok := meta["matched_device_id"].(string); ok && id == params.MatchedDeviceID.String {
						return st, nil
					}
				}
			}
		}
	}
	return nil, nil
}

func (m *mockInvRepo) UpdateStagingDevice(_ context.Context, params db.UpdateStagingDeviceParams) (*db.DeviceStaging, error) {
	for i := range m.staged {
		if m.staged[i].ID == params.ID {
			m.staged[i].Hostname = params.Hostname
			m.staged[i].IpAddress = params.IpAddress
			m.staged[i].MacAddress = params.MacAddress
			m.staged[i].DeviceType = params.DeviceType
			m.staged[i].RawPayload = params.RawPayload
			return &m.staged[i], nil
		}
	}
	return nil, inventoryRepo.ErrStagingDeviceNotFound
}

func (m *mockInvRepo) CreateStagingDevice(_ context.Context, params db.CreateStagingDeviceParams) (*db.DeviceStaging, error) {
	if m.failCreateStagingDevice {
		return nil, errors.New("failed to create staging device")
	}
	staged := db.DeviceStaging{
		ID:         params.ID,
		Hostname:   params.Hostname,
		IpAddress:  params.IpAddress,
		MacAddress: params.MacAddress,
		DeviceType: params.DeviceType,
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

func (m *mockInvRepo) GetSubnetByID(_ context.Context, _ uuid.UUID) (*db.Subnet, error) {
	return nil, nil
}

func (m *mockInvRepo) UpdateSubnet(_ context.Context, _ db.UpdateSubnetParams) (*db.Subnet, error) {
	return nil, nil
}

func (m *mockInvRepo) SoftDeleteSubnet(_ context.Context, _ uuid.UUID) error {
	return nil
}

func (m *mockInvRepo) GetInactiveDiscoverySourceDeviceIDs(_ context.Context, _ []uuid.UUID) ([]uuid.UUID, error) {
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

	t.Run("CreateSource Legacy Format Round-Trip", func(t *testing.T) {
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
		if src.Type != "proxmox" {
			t.Errorf("expected legacy type proxmox, got %s", src.Type)
		}
		if len(src.Collectors) != 1 {
			t.Fatalf("expected 1 populated collector, got %d", len(src.Collectors))
		}
		if src.Collectors[0].CollectorType != "proxmox" {
			t.Errorf("expected collector type proxmox, got %s", src.Collectors[0].CollectorType)
		}
	})

	t.Run("CreateSource Multi-Collector Plan Round-Trip", func(t *testing.T) {
		req := &dto.CreateDiscoverySourceRequest{
			Name: "Homelab Subnet Plan",
			Collectors: []dto.CollectorConfig{
				{Type: "icmp_sweep"},
				{Type: "arp_sweep"},
				{Type: "reverse_dns"},
			},
		}
		src, err := uc.CreateSource(ctx, req)
		if err != nil {
			t.Fatalf("expected nil error, got %v", err)
		}
		if src.Name != "Homelab Subnet Plan" {
			t.Errorf("expected name Homelab Subnet Plan, got %s", src.Name)
		}
		if src.Type != "icmp_sweep" {
			t.Errorf("expected primary type icmp_sweep, got %s", src.Type)
		}
		if len(src.Collectors) != 3 {
			t.Fatalf("expected 3 populated collectors, got %d", len(src.Collectors))
		}
		if src.Collectors[0].CollectorType != "icmp_sweep" ||
			src.Collectors[1].CollectorType != "arp_sweep" ||
			src.Collectors[2].CollectorType != "reverse_dns" {
			t.Errorf("unexpected collectors: %+v", src.Collectors)
		}
	})

	t.Run("CreateSource Emits discovery_source.created Event", func(t *testing.T) {
		eventBus := eventbus.NewInMemoryEventBus(1, 10)
		defer func() { _ = eventBus.Close() }()

		received := make(chan eventbus.DomainEvent, 1)
		_ = eventBus.Subscribe("discovery_source.created", func(_ context.Context, e eventbus.DomainEvent) error {
			received <- e
			return nil
		})

		localUC := usecase.NewDefaultDiscoveryUseCase(newMockDiscRepo(), newMockInvRepo(), eventBus, slog.Default())

		cronExpr := "*/5 * * * *"
		req := &dto.CreateDiscoverySourceRequest{
			Name:         "Cron Source",
			Type:         "icmp_sweep",
			ScheduleCron: cronExpr,
		}
		src, err := localUC.CreateSource(ctx, req)
		if err != nil {
			t.Fatalf("CreateSource error: %v", err)
		}

		select {
		case evt := <-received:
			payload, ok := evt.Payload().(map[string]interface{})
			if !ok {
				t.Fatal("event payload is not map[string]interface{}")
			}
			if payload["source_id"] != src.ID.String() {
				t.Errorf("expected source_id %s, got %v", src.ID, payload["source_id"])
			}
			if payload["schedule_cron"] != cronExpr {
				t.Errorf("expected schedule_cron %s, got %v", cronExpr, payload["schedule_cron"])
			}
			if payload["enabled"] != true {
				t.Errorf("expected enabled true, got %v", payload["enabled"])
			}
		case <-time.After(2 * time.Second):
			t.Fatal("discovery_source.created event was not received within 2s")
		}
	})

	t.Run("CreateSource Logs Publish Error Without Failing", func(t *testing.T) {
		closedBus := eventbus.NewInMemoryEventBus(1, 10)
		_ = closedBus.Close()

		logBuf := &bytes.Buffer{}
		logLogger := slog.New(slog.NewTextHandler(logBuf, nil))
		localUC := usecase.NewDefaultDiscoveryUseCase(newMockDiscRepo(), newMockInvRepo(), closedBus, logLogger)

		req := &dto.CreateDiscoverySourceRequest{
			Name: "Publish Fail Source",
			Type: "icmp_sweep",
		}
		src, err := localUC.CreateSource(ctx, req)
		if err != nil {
			t.Fatalf("CreateSource should succeed even when Publish fails, got %v", err)
		}
		if src.Name != "Publish Fail Source" {
			t.Errorf("expected name 'Publish Fail Source', got %s", src.Name)
		}
		if !bytes.Contains(logBuf.Bytes(), []byte("failed to publish discovery_source.created event")) {
			t.Error("expected log entry for publish error")
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

	t.Run("TriggerRun returns error when source has no CIDR", func(t *testing.T) {
		sources, _ := uc.ListSources(ctx)
		if len(sources) == 0 {
			t.Fatal("expected at least 1 source")
		}
		srcID := sources[0].ID.String()

		_, err := uc.TriggerRun(ctx, srcID)
		if err == nil {
			t.Fatal("expected error for source without CIDR, got nil")
		}
		if sources[0].LastStatus != "error" {
			t.Errorf("expected source status 'error' after missing CIDR, got %q", sources[0].LastStatus)
		}
	})

	t.Run("TriggerRun sets error status when scan fails", func(t *testing.T) {
		sources, _ := uc.ListSources(ctx)
		if len(sources) == 0 {
			t.Fatal("expected at least 1 source")
		}
		sources[0].ConfigCIDR = "not-a-valid-cidr"

		_, err := uc.TriggerRun(ctx, sources[0].ID.String())
		if err == nil {
			t.Fatal("expected error for invalid CIDR scan, got nil")
		}
		if sources[0].LastStatus != "error" {
			t.Errorf("expected status 'error' after scan failure, got %q", sources[0].LastStatus)
		}
	})

	t.Run("TriggerRun with All Success resets to idle and records runs", func(t *testing.T) {
		src, err := uc.CreateSource(ctx, &dto.CreateDiscoverySourceRequest{
			Name: "All Success Source",
			Collectors: []dto.CollectorConfig{
				{Type: "icmp_sweep"},
				{Type: "arp_sweep"},
			},
		})
		if err != nil {
			t.Fatalf("CreateSource error: %v", err)
		}
		src.ConfigCIDR = "192.168.1.0/30"

		res, err := uc.TriggerRun(ctx, src.ID.String())
		if err != nil {
			t.Fatalf("expected nil error, got %v", err)
		}
		if res.LastStatus != "idle" {
			t.Errorf("expected status 'idle', got %s", res.LastStatus)
		}

		runs, err := discRepo.ListRunsBySourceID(ctx, src.ID, 10)
		if err != nil {
			t.Fatalf("ListRunsBySourceID error: %v", err)
		}
		if len(runs) != 2 {
			t.Fatalf("expected 2 collector runs recorded, got %d", len(runs))
		}
		for _, r := range runs {
			if r.Status != "success" {
				t.Errorf("expected run status 'success', got %q for %s", r.Status, r.CollectorType)
			}
		}
	})

	t.Run("TriggerRun with Mixed Success/Error sets partial status and records runs", func(t *testing.T) {
		src, err := uc.CreateSource(ctx, &dto.CreateDiscoverySourceRequest{
			Name: "Partial Success Source",
			Collectors: []dto.CollectorConfig{
				{Type: "arp_sweep"},
				{Type: "proxmox"},
			},
		})
		if err != nil {
			t.Fatalf("CreateSource error: %v", err)
		}
		src.ConfigCIDR = "192.168.1.0/30"

		res, err := uc.TriggerRun(ctx, src.ID.String())
		if err != nil {
			t.Fatalf("expected nil error on partial status run, got %v", err)
		}
		if res.LastStatus != "partial" {
			t.Errorf("expected status 'partial', got %q", res.LastStatus)
		}

		runs, err := discRepo.ListRunsBySourceID(ctx, src.ID, 10)
		if err != nil {
			t.Fatalf("ListRunsBySourceID error: %v", err)
		}
		if len(runs) != 2 {
			t.Fatalf("expected 2 collector runs recorded, got %d", len(runs))
		}
		successCount := 0
		errorCount := 0
		for _, r := range runs {
			if r.Status == "success" {
				successCount++
			} else if r.Status == "error" {
				errorCount++
			}
		}
		if successCount != 1 || errorCount != 1 {
			t.Errorf("expected 1 success and 1 error run, got %d success, %d error", successCount, errorCount)
		}
	})

	t.Run("TriggerRun with All Error sets error status and records runs", func(t *testing.T) {
		src, err := uc.CreateSource(ctx, &dto.CreateDiscoverySourceRequest{
			Name: "All Error Source",
			Collectors: []dto.CollectorConfig{
				{Type: "proxmox"},
				{Type: "docker"},
			},
		})
		if err != nil {
			t.Fatalf("CreateSource error: %v", err)
		}
		src.ConfigCIDR = "192.168.1.0/30"

		res, err := uc.TriggerRun(ctx, src.ID.String())
		if err == nil {
			t.Fatal("expected error on all-error scan, got nil")
		}
		if res.LastStatus != "error" {
			t.Errorf("expected status 'error', got %q", res.LastStatus)
		}

		runs, err := discRepo.ListRunsBySourceID(ctx, src.ID, 10)
		if err != nil {
			t.Fatalf("ListRunsBySourceID error: %v", err)
		}
		if len(runs) != 2 {
			t.Fatalf("expected 2 collector runs recorded, got %d", len(runs))
		}
		for _, r := range runs {
			if r.Status != "error" {
				t.Errorf("expected run status 'error', got %q for %s", r.Status, r.CollectorType)
			}
		}
	})

	t.Run("TriggerRun with Context Cancellation sets cancelled status", func(t *testing.T) {
		src, err := uc.CreateSource(ctx, &dto.CreateDiscoverySourceRequest{
			Name: "Cancelled Source",
			Type: "icmp_sweep",
		})
		if err != nil {
			t.Fatalf("CreateSource error: %v", err)
		}
		src.ConfigCIDR = "192.168.1.0/30"

		cancelCtx, cancel := context.WithCancel(ctx)
		cancel()

		res, err := uc.TriggerRun(cancelCtx, src.ID.String())
		if !errors.Is(err, context.Canceled) {
			t.Errorf("expected context.Canceled error, got %v", err)
		}
		if res.LastStatus != "cancelled" {
			t.Errorf("expected status 'cancelled', got %q", res.LastStatus)
		}
	})

	t.Run("TriggerRun handles CreateCollectorRun failure gracefully (Guideline #85)", func(t *testing.T) {
		src, err := uc.CreateSource(ctx, &dto.CreateDiscoverySourceRequest{
			Name: "Repo Error Source",
			Type: "arp_sweep",
		})
		if err != nil {
			t.Fatalf("CreateSource error: %v", err)
		}
		src.ConfigCIDR = "192.168.1.0/30"

		discRepo.failCreateCollectorRun = true
		res, err := uc.TriggerRun(ctx, src.ID.String())
		if err != nil {
			t.Fatalf("expected nil error even when CreateCollectorRun fails, got %v", err)
		}
		if res.LastStatus != "idle" {
			t.Errorf("expected status 'idle', got %q", res.LastStatus)
		}
		discRepo.failCreateCollectorRun = false
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

	t.Run("IngestNormalizedDevice Auto-Approves Trusted Provider when Collector Configured", func(t *testing.T) {
		sweepSrc, err := uc.CreateSource(ctx, &dto.CreateDiscoverySourceRequest{
			Name: "Multi-Collector Plan with Proxmox",
			Collectors: []dto.CollectorConfig{
				{Type: "icmp_sweep"},
				{Type: "proxmox"},
			},
		})
		if err != nil {
			t.Fatalf("CreateSource error: %v", err)
		}

		norm := &dto.NormalizedDeviceDTO{
			Hostname:       "proxmox-pve-02",
			IPAddress:      "192.168.1.150",
			DeviceType:     "hypervisor",
			ProtocolSource: "proxmox",
		}

		rec, err := uc.IngestNormalizedDevice(ctx, sweepSrc.ID, norm)
		if err != nil {
			t.Fatalf("IngestNormalizedDevice error: %v", err)
		}
		if rec.MatchedBy != "new_discovery" {
			t.Errorf("expected matchedBy new_discovery, got %s", rec.MatchedBy)
		}

		dev, err := invRepo.GetDeviceByID(ctx, rec.DeviceID, false)
		if err != nil {
			t.Fatalf("expected device in active inventory, got error: %v", err)
		}
		if dev.Status != "active" {
			t.Errorf("expected device status 'active', got %q", dev.Status)
		}
	})

	t.Run("IngestNormalizedDevice Stages Device When ProtocolSource Not Configured on Source", func(t *testing.T) {
		icmpSrc, err := uc.CreateSource(ctx, &dto.CreateDiscoverySourceRequest{
			Name: "ICMP Only Source",
			Type: "icmp_sweep",
		})
		if err != nil {
			t.Fatalf("CreateSource error: %v", err)
		}

		norm := &dto.NormalizedDeviceDTO{
			Hostname:       "spoofed-proxmox-claim",
			IPAddress:      "192.168.1.151",
			DeviceType:     "server",
			ProtocolSource: "proxmox", // Claiming proxmox on an ICMP-only source
		}

		rec, err := uc.IngestNormalizedDevice(ctx, icmpSrc.ID, norm)
		if err != nil {
			t.Fatalf("IngestNormalizedDevice error: %v", err)
		}

		// Because source is only icmp_sweep, device must be sent to staging (staged_devices), not active inventory
		dev, err := invRepo.GetDeviceByID(ctx, rec.DeviceID, false)
		if err == nil && dev != nil {
			t.Fatalf("expected device NOT to be auto-approved into active inventory, got: %v", dev)
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

	t.Run("GetDeletionImpact Invalid UUID", func(t *testing.T) {
		_, err := uc.GetDeletionImpact(ctx, "not-a-uuid")
		if !errors.Is(err, usecase.ErrInvalidUUID) {
			t.Errorf("expected ErrInvalidUUID, got %v", err)
		}
	})

	t.Run("GetDeletionImpact NotFound", func(t *testing.T) {
		_, err := uc.GetDeletionImpact(ctx, uuid.New().String())
		if !errors.Is(err, repository.ErrSourceNotFound) {
			t.Errorf("expected ErrSourceNotFound, got %v", err)
		}
	})

	t.Run("GetDeletionImpact Success", func(t *testing.T) {
		impactID := uuid.New()
		discRepo.sources[impactID] = &dto.DiscoverySourceResponse{
			ID:   impactID,
			Name: "impact-src",
			Collectors: []dto.CollectorResponse{
				{ID: uuid.New(), CollectorType: "proxmox", Enabled: true},
			},
		}

		resp, err := uc.GetDeletionImpact(ctx, impactID.String())
		if err != nil {
			t.Fatalf("expected nil error, got %v", err)
		}
		if resp.SourceID != impactID.String() {
			t.Errorf("expected source ID %s, got %s", impactID.String(), resp.SourceID)
		}
		if resp.Impact.CollectorsHalted != 1 {
			t.Errorf("expected 1 collector halted, got %d", resp.Impact.CollectorsHalted)
		}
		if resp.Impact.DevicesUnlinked != 2 {
			t.Errorf("expected 2 devices unlinked, got %d", resp.Impact.DevicesUnlinked)
		}
	})

	t.Run("DeleteSource Invalid UUID", func(t *testing.T) {
		err := uc.DeleteSource(ctx, "not-a-uuid")
		if !errors.Is(err, usecase.ErrInvalidUUID) {
			t.Errorf("expected ErrInvalidUUID, got %v", err)
		}
	})

	t.Run("DeleteSource NotFound", func(t *testing.T) {
		err := uc.DeleteSource(ctx, uuid.New().String())
		if !errors.Is(err, repository.ErrSourceNotFound) {
			t.Errorf("expected ErrSourceNotFound, got %v", err)
		}
	})

	t.Run("DeleteSource Success", func(t *testing.T) {
		deleteID := uuid.New()
		discRepo.sources[deleteID] = &dto.DiscoverySourceResponse{ID: deleteID, Name: "to-delete"}

		err := uc.DeleteSource(ctx, deleteID.String())
		if err != nil {
			t.Fatalf("expected nil error, got %v", err)
		}
		if _, exists := discRepo.sources[deleteID]; exists {
			t.Error("expected source to be removed from repo")
		}
	})

	t.Run("SoftDeleteSource Preserves Devices and Allows Same Name Re-registration", func(t *testing.T) {
		deleteID := uuid.New()
		sourceName := "reusable-source-name"
		discRepo.sources[deleteID] = &dto.DiscoverySourceResponse{
			ID:   deleteID,
			Name: sourceName,
			Collectors: []dto.CollectorResponse{
				{ID: uuid.New(), CollectorType: "docker", Enabled: true},
			},
		}

		// Seed a device record linked to this source
		devID := uuid.New()
		rec, _ := discRepo.UpsertRecord(ctx, devID, deleteID, "mac", map[string]interface{}{"ip": "10.0.0.1"})

		resp, err := uc.SoftDeleteSource(ctx, deleteID.String())
		if err != nil {
			t.Fatalf("unexpected error on SoftDeleteSource: %v", err)
		}
		if resp.DeletedID != deleteID.String() {
			t.Errorf("expected deleted ID %s, got %s", deleteID.String(), resp.DeletedID)
		}
		if resp.Impact.CollectorsHalted != 1 {
			t.Errorf("expected 1 collector halted, got %d", resp.Impact.CollectorsHalted)
		}

		// Discovery record is preserved for history
		records, err := discRepo.ListRecordsByDevice(ctx, devID)
		if err != nil || len(records) == 0 || records[0].ID != rec.ID {
			t.Errorf("expected discovery record to be preserved in repo")
		}

		// Re-registering with the same name is permitted after soft-delete
		newSource, err := uc.CreateSource(ctx, &dto.CreateDiscoverySourceRequest{
			Name: sourceName,
			Type: "docker",
		})
		if err != nil {
			t.Fatalf("expected creating source with same name after deletion to succeed, got %v", err)
		}
		if newSource.Name != sourceName {
			t.Errorf("expected new source name %s, got %s", sourceName, newSource.Name)
		}
	})

	t.Run("DeleteSource Publishes Event", func(t *testing.T) {
		localBus := eventbus.NewInMemoryEventBus(1, 10)
		defer func() { _ = localBus.Close() }()

		received := make(chan eventbus.DomainEvent, 1)
		_ = localBus.Subscribe("discovery_source.deleted", func(_ context.Context, e eventbus.DomainEvent) error {
			received <- e
			return nil
		})

		deleteID := uuid.New()
		localRepo := newMockDiscRepo()
		localRepo.sources[deleteID] = &dto.DiscoverySourceResponse{
			ID:   deleteID,
			Name: "evt-src",
			Collectors: []dto.CollectorResponse{
				{ID: uuid.New(), CollectorType: "proxmox", Enabled: true},
			},
		}
		localUC := usecase.NewDefaultDiscoveryUseCase(localRepo, newMockInvRepo(), localBus, slog.Default())

		err := localUC.DeleteSource(ctx, deleteID.String())
		if err != nil {
			t.Fatalf("expected nil error, got %v", err)
		}

		select {
		case evt := <-received:
			payload, ok := evt.Payload().(map[string]interface{})
			if !ok {
				t.Fatal("event payload is not map[string]interface{}")
			}
			if payload["source_id"] != deleteID.String() {
				t.Errorf("expected source_id %s, got %v", deleteID, payload["source_id"])
			}
			if payload["collectors_halted"] != 1 {
				t.Errorf("expected collectors_halted 1, got %v", payload["collectors_halted"])
			}
			if payload["devices_unlinked"] != 2 {
				t.Errorf("expected devices_unlinked 2, got %v", payload["devices_unlinked"])
			}
		case <-time.After(2 * time.Second):
			t.Fatal("discovery_source.deleted event was not received within 2s")
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

		// Selective scan with valid collectors
		selRes, err := uc.TriggerScan(ctx, &dto.TriggerScanRequest{
			CIDR:       "192.168.1.0/30",
			Collectors: []string{"icmp_sweep", "arp_sweep"},
		})
		if err != nil {
			t.Fatalf("expected nil error on selective TriggerScan, got %v", err)
		}
		if selRes == nil || len(selRes.Collectors) != 2 {
			t.Errorf("expected 2 collector details in response, got %v", selRes)
		}

		// Selective scan with invalid collector
		_, err = uc.TriggerScan(ctx, &dto.TriggerScanRequest{
			CIDR:       "192.168.1.0/30",
			Collectors: []string{"unknown_bad_collector"},
		})
		if !errors.Is(err, usecase.ErrInvalidInput) {
			t.Errorf("expected ErrInvalidInput for invalid collector, got %v", err)
		}
	})

	t.Run("PurgeCollectorRuns Default and Configurable Retention", func(t *testing.T) {
		discRepo.purgedCount = 42
		discRepo.failPurge = false

		// 1. Default retention days (7)
		t.Setenv(usecase.RunRetentionDaysEnvVar, "")
		purged, err := uc.PurgeCollectorRuns(ctx, 0)
		if err != nil {
			t.Fatalf("expected nil error on purge, got %v", err)
		}
		if purged != 42 {
			t.Errorf("expected 42 purged, got %d", purged)
		}
		expectedCutoff := time.Now().Add(-7 * 24 * time.Hour)
		diff := discRepo.lastCutoff.Sub(expectedCutoff)
		if diff < 0 {
			diff = -diff
		}
		if diff > 5*time.Second {
			t.Errorf("expected cutoff around %v, got %v", expectedCutoff, discRepo.lastCutoff)
		}

		// 2. Override via environment variable
		t.Setenv(usecase.RunRetentionDaysEnvVar, "14")
		purged, err = uc.PurgeCollectorRuns(ctx, 0)
		if err != nil {
			t.Fatalf("expected nil error on purge with env override, got %v", err)
		}
		if purged != 42 {
			t.Errorf("expected 42 purged, got %d", purged)
		}
		expectedCutoff14 := time.Now().Add(-14 * 24 * time.Hour)
		diff14 := discRepo.lastCutoff.Sub(expectedCutoff14)
		if diff14 < 0 {
			diff14 = -diff14
		}
		if diff14 > 5*time.Second {
			t.Errorf("expected cutoff around %v, got %v", expectedCutoff14, discRepo.lastCutoff)
		}

		// 3. Explicit parameter overrides environment variable
		purged, err = uc.PurgeCollectorRuns(ctx, 30)
		if err != nil {
			t.Fatalf("expected nil error on purge with explicit param, got %v", err)
		}
		if purged != 42 {
			t.Errorf("expected 42 purged, got %d", purged)
		}
		expectedCutoff30 := time.Now().Add(-30 * 24 * time.Hour)
		diff30 := discRepo.lastCutoff.Sub(expectedCutoff30)
		if diff30 < 0 {
			diff30 = -diff30
		}
		if diff30 > 5*time.Second {
			t.Errorf("expected cutoff around %v, got %v", expectedCutoff30, discRepo.lastCutoff)
		}

		// 4. Repository failure
		discRepo.failPurge = true
		_, err = uc.PurgeCollectorRuns(ctx, 7)
		if err == nil {
			t.Fatal("expected error on repo purge failure, got nil")
		}
		discRepo.failPurge = false

		// 5. Overflow clamp for massive retention days
		purged, err = uc.PurgeCollectorRuns(ctx, 1_000_000)
		if err != nil {
			t.Fatalf("expected nil error on massive retention days purge, got %v", err)
		}
		if purged != 42 {
			t.Errorf("expected 42 purged, got %d", purged)
		}
		expectedCutoffMax := time.Now().Add(-time.Duration(usecase.MaxRetentionDays) * 24 * time.Hour)
		diffMax := discRepo.lastCutoff.Sub(expectedCutoffMax)
		if diffMax < 0 {
			diffMax = -diffMax
		}
		if diffMax > 5*time.Second {
			t.Errorf("expected cutoff clamped to MaxRetentionDays around %v, got %v", expectedCutoffMax, discRepo.lastCutoff)
		}
	})

	t.Run("ListRunsBySource Validations and Pagination", func(t *testing.T) {
		// 1. Invalid UUID
		_, _, err := uc.ListRunsBySource(ctx, "invalid-uuid", 20, 0)
		if !errors.Is(err, usecase.ErrInvalidUUID) {
			t.Errorf("expected ErrInvalidUUID, got %v", err)
		}

		// 2. Source not found
		_, _, err = uc.ListRunsBySource(ctx, uuid.New().String(), 20, 0)
		if !errors.Is(err, repository.ErrSourceNotFound) {
			t.Errorf("expected ErrSourceNotFound, got %v", err)
		}

		// 3. Existing source with runs
		src, createErr := uc.CreateSource(ctx, &dto.CreateDiscoverySourceRequest{
			Name: "History Source",
			Type: "icmp_sweep",
		})
		if createErr != nil {
			t.Fatalf("failed to create source: %v", createErr)
		}

		for i := int32(0); i < 25; i++ {
			_ = discRepo.CreateCollectorRun(ctx, &db.CreateCollectorRunParams{
				ID:            uuid.New(),
				SourceID:      src.ID,
				CollectorType: "icmp_sweep",
				Status:        "success",
				DevicesFound:  i + 1,
			})
		}

		// Query with default limit / offset
		runs, total, err := uc.ListRunsBySource(ctx, src.ID.String(), 0, 0)
		if err != nil {
			t.Fatalf("expected nil error, got %v", err)
		}
		if total != 25 {
			t.Errorf("expected total 25, got %d", total)
		}
		if len(runs) != 20 {
			t.Errorf("expected 20 runs (clamped default), got %d", len(runs))
		}
		if discRepo.lastLimit != 20 || discRepo.lastOffset != 0 {
			t.Errorf("expected repo limit 20, offset 0; got %d, %d", discRepo.lastLimit, discRepo.lastOffset)
		}

		// Query with limit 5, offset 20
		runsPaged, totalPaged, err := uc.ListRunsBySource(ctx, src.ID.String(), 5, 20)
		if err != nil {
			t.Fatalf("expected nil error on paged runs, got %v", err)
		}
		if totalPaged != 25 {
			t.Errorf("expected total 25, got %d", totalPaged)
		}
		if len(runsPaged) != 5 {
			t.Errorf("expected 5 runs on page 2, got %d", len(runsPaged))
		}
		if runsPaged[0].DevicesFound != 21 {
			t.Errorf("expected DevicesFound 21 on page 2 first item, got %d", runsPaged[0].DevicesFound)
		}
		if discRepo.lastLimit != 5 || discRepo.lastOffset != 20 {
			t.Errorf("expected repo limit 5, offset 20; got %d, %d", discRepo.lastLimit, discRepo.lastOffset)
		}

		// Query with limit > 100 clamped to 100
		runsMax, totalMax, err := uc.ListRunsBySource(ctx, src.ID.String(), 200, 0)
		if err != nil {
			t.Fatalf("expected nil error on max limit query, got %v", err)
		}
		if totalMax != 25 {
			t.Errorf("expected total 25, got %d", totalMax)
		}
		if len(runsMax) != 25 {
			t.Errorf("expected 25 runs total, got %d", len(runsMax))
		}
		if discRepo.lastLimit != 100 || discRepo.lastOffset != 0 {
			t.Errorf("expected repo limit 100, offset 0; got %d, %d", discRepo.lastLimit, discRepo.lastOffset)
		}
	})
}

func (m *mockInvRepo) ListDevicesByProviderScope(_ context.Context, providerScope string) ([]db.Device, error) {
	if m.devicesByProviderScope == nil {
		return nil, nil
	}
	return m.devicesByProviderScope[providerScope], nil
}

func (m *mockInvRepo) MarkDeviceAbsent(_ context.Context, id uuid.UUID, archiveThreshold int32) (*db.Device, error) {
	m.absenceCalls = append(m.absenceCalls, id)
	if m.absenceCounts == nil {
		m.absenceCounts = map[uuid.UUID]int16{}
	}
	m.absenceCounts[id]++
	count := m.absenceCounts[id]

	status := "offline"
	if int32(count) >= archiveThreshold {
		status = "archived"
	}
	return &db.Device{ID: id, Status: status, AbsenceCount: count}, nil
}

func (m *mockInvRepo) GetDeviceByProviderRef(_ context.Context, providerRef string) (*db.Device, error) {
	device, ok := m.devicesByProviderRef[providerRef]
	if !ok {
		return nil, inventoryRepo.ErrDeviceNotFound
	}
	return &device, nil
}

func (m *mockInvRepo) ListDevicesPendingParentResolution(_ context.Context, parentProviderRef string) ([]db.Device, error) {
	return m.pendingChildren[parentProviderRef], nil
}

func (m *mockInvRepo) SetDeviceParent(_ context.Context, id, parentDeviceID uuid.UUID, parentProviderRef string) (*db.Device, error) {
	if m.parentAssignments == nil {
		m.parentAssignments = map[uuid.UUID]uuid.UUID{}
	}
	m.parentAssignments[id] = parentDeviceID
	return &db.Device{
		ID:                id,
		ParentDeviceID:    pgtype.UUID{Bytes: parentDeviceID, Valid: true},
		ParentProviderRef: pgtype.Text{String: parentProviderRef, Valid: parentProviderRef != ""},
	}, nil
}

func TestUpdateDiscoverySource_SecretPreservationAndSSRFGuard(t *testing.T) {
	discRepo := newMockDiscRepo()
	invRepo := newMockInvRepo()
	bus := eventbus.NewInMemoryEventBus(1, 10)
	uc := usecase.NewDefaultDiscoveryUseCase(discRepo, invRepo, bus, slog.Default())
	ctx := context.Background()

	createReq := &dto.CreateDiscoverySourceRequest{
		Name: "Lab Proxmox",
		Type: "proxmox",
		Collectors: []dto.CollectorConfig{
			{
				Type: "proxmox",
				Config: map[string]interface{}{
					"api_url":      "https://pve1.lab:8006",
					"token_id":     "root@pam!inframap",
					"token_secret": "super-secret-12345",
				},
			},
		},
	}
	src, err := uc.CreateSource(ctx, createReq)
	if err != nil {
		t.Fatalf("failed to create source: %v", err)
	}

	t.Run("same target and blank secret preserves secret", func(t *testing.T) {
		updateReq := &dto.UpdateDiscoverySourceRequest{
			Name: "Lab Proxmox Updated",
			Type: "proxmox",
			Collectors: []dto.CollectorConfig{
				{
					Type: "proxmox",
					Config: map[string]interface{}{
						"api_url":      "https://pve1.lab:8006",
						"token_id":     "root@pam!inframap",
						"token_secret": "",
					},
				},
			},
		}

		updated, err := uc.UpdateSource(ctx, src.ID.String(), updateReq)
		if err != nil {
			t.Fatalf("expected update to succeed, got %v", err)
		}
		if updated.Name != "Lab Proxmox Updated" {
			t.Errorf("expected name to be updated, got %s", updated.Name)
		}

		rawConfigs, err := discRepo.GetRawCollectorConfigs(ctx, src.ID)
		if err != nil {
			t.Fatalf("failed to get raw configs: %v", err)
		}
		if rawConfigs["proxmox"]["token_secret"] != "super-secret-12345" {
			t.Errorf("expected secret to be preserved, got %v", rawConfigs["proxmox"]["token_secret"])
		}
	})

	t.Run("target changed and blank secret rejects with ErrSecretRequiredOnTargetChange", func(t *testing.T) {
		updateReq := &dto.UpdateDiscoverySourceRequest{
			Name: "Lab Proxmox SSRF Attempt",
			Type: "proxmox",
			Collectors: []dto.CollectorConfig{
				{
					Type: "proxmox",
					Config: map[string]interface{}{
						"api_url":      "https://evil-attacker.com:8006",
						"token_id":     "root@pam!inframap",
						"token_secret": "",
					},
				},
			},
		}

		_, err := uc.UpdateSource(ctx, src.ID.String(), updateReq)
		if err == nil {
			t.Fatal("expected error, got nil")
		}
		var ssrfErr *dto.ErrSecretRequiredOnTargetChange
		if !errors.As(err, &ssrfErr) {
			t.Fatalf("expected ErrSecretRequiredOnTargetChange, got %v", err)
		}
		if len(ssrfErr.Fields) == 0 || ssrfErr.Fields[0] != "api_url" {
			t.Errorf("expected api_url in changed fields, got %v", ssrfErr.Fields)
		}
	})

	t.Run("target changed and new secret provided succeeds", func(t *testing.T) {
		updateReq := &dto.UpdateDiscoverySourceRequest{
			Name: "Lab Proxmox Legit Move",
			Type: "proxmox",
			Collectors: []dto.CollectorConfig{
				{
					Type: "proxmox",
					Config: map[string]interface{}{
						"api_url":      "https://pve2.lab:8006",
						"token_id":     "root@pam!inframap",
						"token_secret": "brand-new-secret-67890",
					},
				},
			},
		}

		updated, err := uc.UpdateSource(ctx, src.ID.String(), updateReq)
		if err != nil {
			t.Fatalf("expected update to succeed, got %v", err)
		}
		if updated.Name != "Lab Proxmox Legit Move" {
			t.Errorf("expected name to be updated, got %s", updated.Name)
		}

		rawConfigs, err := discRepo.GetRawCollectorConfigs(ctx, src.ID)
		if err != nil {
			t.Fatalf("failed to get raw configs: %v", err)
		}
		if rawConfigs["proxmox"]["token_secret"] != "brand-new-secret-67890" {
			t.Errorf("expected secret to be updated, got %v", rawConfigs["proxmox"]["token_secret"])
		}
		if rawConfigs["proxmox"]["api_url"] != "https://pve2.lab:8006" {
			t.Errorf("expected api_url to be updated, got %v", rawConfigs["proxmox"]["api_url"])
		}
	})
}

type dummyHealthProvider struct {
	id         string
	healthErr  error
	lastConfig sdk.ProviderConfig
}

func (d *dummyHealthProvider) ID() string { return d.id }
func (d *dummyHealthProvider) Metadata() sdk.ProviderMetadata {
	return sdk.ProviderMetadata{Name: d.id}
}
func (d *dummyHealthProvider) ConfigSchema() sdk.ConfigSchema {
	return sdk.ConfigSchema{}
}
func (d *dummyHealthProvider) ValidateConfig(_ sdk.ProviderConfig) error { return nil }
func (d *dummyHealthProvider) HealthCheck(_ context.Context, config sdk.ProviderConfig) error {
	d.lastConfig = config
	return d.healthErr
}
func (d *dummyHealthProvider) Discover(_ context.Context, _ sdk.ProviderConfig) ([]sdk.NormalizedDevice, error) {
	return nil, nil
}

func TestDiscoveryUseCase_TestHealth(t *testing.T) {
	ctx := context.Background()

	t.Run("success using stored secret", func(t *testing.T) {
		discRepo := newMockDiscRepo()
		invRepo := newMockInvRepo()
		bus := eventbus.NewInMemoryEventBus(1, 10)
		uc := usecase.NewDefaultDiscoveryUseCase(discRepo, invRepo, bus, slog.Default())

		provider := &dummyHealthProvider{id: "proxmox"}
		uc.RegisterProvider(provider)

		srcID := uuid.New()
		discRepo.sources[srcID] = &dto.DiscoverySourceResponse{
			ID:   srcID,
			Name: "Proxmox Cluster",
			Type: "proxmox",
			Collectors: []dto.CollectorResponse{
				{
					ID:            uuid.New(),
					CollectorType: "proxmox",
					Enabled:       true,
				},
			},
		}

		discRepo.resolveConfigFn = func(_ context.Context, _ uuid.UUID, _ string) (sdk.ProviderConfig, error) {
			return sdk.ProviderConfig{
				"api_url":      "https://pve.example.com:8006",
				"token_id":     "root@pam!token",
				"token_secret": "super-secret-stored-value",
			}, nil
		}

		resp, err := uc.TestHealth(ctx, srcID.String(), "")
		if err != nil {
			t.Fatalf("expected nil error, got %v", err)
		}
		if resp.Status != "ok" {
			t.Errorf("expected status ok, got %s", resp.Status)
		}
		if resp.ProviderID != "proxmox" {
			t.Errorf("expected provider proxmox, got %s", resp.ProviderID)
		}
		if provider.lastConfig["token_secret"] != "super-secret-stored-value" {
			t.Errorf("expected stored secret to be passed to provider, got %v", provider.lastConfig["token_secret"])
		}
	})

	t.Run("error sanitizes secret from message", func(t *testing.T) {
		discRepo := newMockDiscRepo()
		invRepo := newMockInvRepo()
		bus := eventbus.NewInMemoryEventBus(1, 10)
		uc := usecase.NewDefaultDiscoveryUseCase(discRepo, invRepo, bus, slog.Default())

		secretVal := "very-sensitive-token-secret-12345"
		provider := &dummyHealthProvider{
			id:        "proxmox",
			healthErr: fmt.Errorf("connection failed using token secret %s: timeout", secretVal),
		}
		uc.RegisterProvider(provider)

		srcID := uuid.New()
		discRepo.sources[srcID] = &dto.DiscoverySourceResponse{
			ID:   srcID,
			Name: "Proxmox Cluster",
			Type: "proxmox",
			Collectors: []dto.CollectorResponse{
				{
					ID:            uuid.New(),
					CollectorType: "proxmox",
					Enabled:       true,
				},
			},
		}

		discRepo.resolveConfigFn = func(_ context.Context, _ uuid.UUID, _ string) (sdk.ProviderConfig, error) {
			return sdk.ProviderConfig{
				"api_url":      "https://pve.example.com:8006",
				"token_id":     "root@pam!token",
				"token_secret": secretVal,
			}, nil
		}

		resp, err := uc.TestHealth(ctx, srcID.String(), "proxmox")
		if err != nil {
			t.Fatalf("expected nil error (health reporting error via payload), got %v", err)
		}
		if resp.Status != "error" {
			t.Errorf("expected status error, got %s", resp.Status)
		}
		if strings.Contains(resp.Message, secretVal) {
			t.Fatalf("CRITICAL: secret leaked in health error message: %s", resp.Message)
		}
		if !strings.Contains(resp.Message, "[REDACTED]") {
			t.Errorf("expected redacted indicator in message, got: %s", resp.Message)
		}
	})

	t.Run("not found source", func(t *testing.T) {
		discRepo := newMockDiscRepo()
		invRepo := newMockInvRepo()
		bus := eventbus.NewInMemoryEventBus(1, 10)
		uc := usecase.NewDefaultDiscoveryUseCase(discRepo, invRepo, bus, slog.Default())

		_, err := uc.TestHealth(ctx, uuid.New().String(), "")
		if err == nil {
			t.Fatal("expected error for nonexistent source")
		}
	})

	t.Run("invalid uuid", func(t *testing.T) {
		discRepo := newMockDiscRepo()
		invRepo := newMockInvRepo()
		bus := eventbus.NewInMemoryEventBus(1, 10)
		uc := usecase.NewDefaultDiscoveryUseCase(discRepo, invRepo, bus, slog.Default())

		_, err := uc.TestHealth(ctx, "invalid-uuid", "")
		if !errors.Is(err, usecase.ErrInvalidUUID) {
			t.Errorf("expected ErrInvalidUUID, got %v", err)
		}
	})

	t.Run("source without provider", func(t *testing.T) {
		discRepo := newMockDiscRepo()
		invRepo := newMockInvRepo()
		bus := eventbus.NewInMemoryEventBus(1, 10)
		uc := usecase.NewDefaultDiscoveryUseCase(discRepo, invRepo, bus, slog.Default())

		srcID := uuid.New()
		discRepo.sources[srcID] = &dto.DiscoverySourceResponse{
			ID:   srcID,
			Name: "Network Scan",
			Type: "network",
			Collectors: []dto.CollectorResponse{
				{
					ID:            uuid.New(),
					CollectorType: "icmp_sweep",
					Enabled:       true,
				},
			},
		}

		_, err := uc.TestHealth(ctx, srcID.String(), "")
		if !errors.Is(err, usecase.ErrNoProviderForSource) {
			t.Errorf("expected ErrNoProviderForSource, got %v", err)
		}
	})
}

func TestIngestNormalizedDevice_RediscoverSoftDeletedDevice(t *testing.T) {
	discRepo := newMockDiscRepo()
	invRepo := newMockInvRepo()
	bus := eventbus.NewInMemoryEventBus(1, 10)
	uc := usecase.NewDefaultDiscoveryUseCase(discRepo, invRepo, bus, slog.Default())

	srcID := uuid.New()
	discRepo.sources[srcID] = &dto.DiscoverySourceResponse{
		ID:   srcID,
		Name: "Test Sweeper",
		Type: "network",
	}

	delDeviceID := uuid.New()
	addr, _ := netip.ParseAddr("10.0.0.99")
	hw := net.HardwareAddr{0x00, 0x11, 0x22, 0x33, 0x44, 0x55}
	invRepo.devices = append(invRepo.devices, db.Device{
		ID:         delDeviceID,
		Hostname:   "deleted-device",
		IpAddress:  &addr,
		MacAddress: hw,
		DeviceType: "server",
		Status:     "deleted",
		DeletedAt:  pgtype.Timestamptz{Time: time.Now(), Valid: true},
	})

	norm := &dto.NormalizedDeviceDTO{
		Hostname:       "deleted-device-new-name",
		IPAddress:      "10.0.0.99",
		MACAddress:     "00:11:22:33:44:55",
		DeviceType:     "server",
		ProtocolSource: "icmp_sweep",
		RawPayload:     map[string]interface{}{"ping": "ok"},
	}

	// First sweep: should match soft-deleted device and route to staging marked previously_deleted
	rec, err := uc.IngestNormalizedDevice(context.Background(), srcID, norm)
	if err != nil {
		t.Fatalf("unexpected error ingesting device: %v", err)
	}
	if rec.MatchedBy != "previously_deleted" {
		t.Errorf("expected matched_by previously_deleted, got %s", rec.MatchedBy)
	}

	// Verify device in inventory was NOT restored automatically
	for _, d := range invRepo.devices {
		if d.ID == delDeviceID {
			if !d.DeletedAt.Valid {
				t.Error("soft-deleted device must NOT have deleted_at cleared automatically")
			}
		}
	}

	// Verify staging record
	if len(invRepo.staged) != 1 {
		t.Fatalf("expected 1 staging record, got %d", len(invRepo.staged))
	}
	staged := invRepo.staged[0]
	var payload map[string]interface{}
	if err := json.Unmarshal(staged.RawPayload, &payload); err != nil {
		t.Fatalf("failed to parse staged raw payload: %v", err)
	}
	if prevDel, ok := payload["previously_deleted"].(bool); !ok || !prevDel {
		t.Error("expected previously_deleted=true in raw_payload")
	}
	if matchedID, ok := payload["matched_device_id"].(string); !ok || matchedID != delDeviceID.String() {
		t.Errorf("expected matched_device_id=%s, got %v", delDeviceID, payload["matched_device_id"])
	}

	// Second sweep: idempotency guarantee - repeated sweep must NOT create duplicate staging record
	rec2, err2 := uc.IngestNormalizedDevice(context.Background(), srcID, norm)
	if err2 != nil {
		t.Fatalf("unexpected error in second sweep: %v", err2)
	}
	if rec2 == nil {
		t.Fatal("expected record from second sweep")
	}
	if len(invRepo.staged) != 1 {
		t.Errorf("idempotency violated: expected 1 staging record after repeated sweep, got %d", len(invRepo.staged))
	}
}

