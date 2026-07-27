package usecase_test

import (
	"bytes"
	"context"
	"encoding/json"
	"errors"
	"log/slog"
	"testing"
	"time"

	"github.com/google/uuid"
	"github.com/matheussouza/inframap/internal/platform/db"
	"github.com/matheussouza/inframap/internal/platform/eventbus"
	inventoryRepo "github.com/matheussouza/inframap/modules/inventory/repository"
	"github.com/matheussouza/inframap/modules/topology/dto"
	topoRepo "github.com/matheussouza/inframap/modules/topology/repository"
	"github.com/matheussouza/inframap/modules/topology/usecase"
)

type mockTopoRepo struct {
	links map[uuid.UUID]*dto.TopologyLinkResponse

	failCreate bool
	failDelete bool
}

func newMockTopoRepo() *mockTopoRepo {
	return &mockTopoRepo{links: make(map[uuid.UUID]*dto.TopologyLinkResponse)}
}

func (m *mockTopoRepo) CreateLink(_ context.Context, req *dto.CreateTopologyLinkRequest) (*dto.TopologyLinkResponse, error) {
	if m.failCreate {
		return nil, errors.New("db error")
	}
	id := uuid.New()
	score := 1.00
	if req.ConfidenceScore != nil {
		score = *req.ConfidenceScore
	}
	resp := &dto.TopologyLinkResponse{
		ID:              id,
		SourceDeviceID:  req.SourceDeviceID,
		TargetDeviceID:  req.TargetDeviceID,
		LinkType:        req.LinkType,
		ConfidenceScore: score,
		DiscoveredBy:    req.DiscoveredBy,
		Metadata:        req.Metadata,
		CreatedAt:       time.Now().Format(time.RFC3339),
		UpdatedAt:       time.Now().Format(time.RFC3339),
	}
	m.links[id] = resp
	return resp, nil
}

func (m *mockTopoRepo) GetLinkByID(_ context.Context, id uuid.UUID) (*dto.TopologyLinkResponse, error) {
	l, exists := m.links[id]
	if !exists {
		return nil, topoRepo.ErrLinkNotFound
	}
	return l, nil
}

func (m *mockTopoRepo) ListLinks(_ context.Context, linkType string, _, _ *uuid.UUID, _, _ int32) ([]*dto.TopologyLinkResponse, error) {
	res := make([]*dto.TopologyLinkResponse, 0, len(m.links))
	for _, l := range m.links {
		if linkType == "" || l.LinkType == linkType {
			res = append(res, l)
		}
	}
	return res, nil
}

func (m *mockTopoRepo) DeleteLink(_ context.Context, id uuid.UUID) error {
	if m.failDelete {
		return errors.New("db delete error")
	}
	delete(m.links, id)
	return nil
}

func (m *mockTopoRepo) GetGraphData(_ context.Context) (*dto.TopologyGraphResponse, error) {
	nodes := []dto.DeviceNode{
		{ID: uuid.New(), Hostname: "pve-host", DeviceType: "hypervisor", Status: "active"},
		{ID: uuid.New(), Hostname: "vm-101", DeviceType: "vm", Status: "active"},
	}
	edges := make([]dto.LinkEdge, 0, len(m.links))
	for _, l := range m.links {
		edges = append(edges, dto.LinkEdge{
			ID:              l.ID,
			SourceDeviceID:  l.SourceDeviceID,
			TargetDeviceID:  l.TargetDeviceID,
			LinkType:        l.LinkType,
			ConfidenceScore: l.ConfidenceScore,
			DiscoveredBy:    l.DiscoveredBy,
		})
	}
	return &dto.TopologyGraphResponse{
		Nodes:    nodes,
		Edges:    edges,
		Metadata: map[string]interface{}{"total_nodes": len(nodes), "total_edges": len(edges)},
	}, nil
}

type mockInvRepo struct {
	devices []db.Device
}

func (m *mockInvRepo) CreateDevice(_ context.Context, _ db.CreateDeviceParams) (*db.Device, error) {
	return nil, nil
}

func (m *mockInvRepo) GetDeviceByID(_ context.Context, id uuid.UUID, _ bool) (*db.Device, error) {
	for i := range m.devices {
		if m.devices[i].ID == id {
			return &m.devices[i], nil
		}
	}
	return nil, inventoryRepo.ErrDeviceNotFound
}

func (m *mockInvRepo) ListDevices(_ context.Context, _, _ string, _, _ int32, _ bool) ([]db.Device, int64, error) {
	return m.devices, int64(len(m.devices)), nil
}

func (m *mockInvRepo) UpdateDevice(_ context.Context, _ db.UpdateDeviceParams) (*db.Device, error) {
	return nil, nil
}

func (m *mockInvRepo) SoftDeleteDevice(_ context.Context, _ uuid.UUID) error {
	return nil
}

func (m *mockInvRepo) CreateStagingDevice(_ context.Context, _ db.CreateStagingDeviceParams) (*db.DeviceStaging, error) {
	return nil, nil
}

func (m *mockInvRepo) GetStagingDeviceByID(_ context.Context, _ uuid.UUID) (*db.DeviceStaging, error) {
	return nil, nil
}

func (m *mockInvRepo) ListStagingDevices(_ context.Context, _ string, _, _ int32) ([]db.DeviceStaging, int64, error) {
	return nil, 0, nil
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

func TestTopologyUseCase_Unit(t *testing.T) {
	repo := newMockTopoRepo()
	invRepo := &mockInvRepo{}
	bus := eventbus.NewInMemoryEventBus(1, 10)
	defer func() { _ = bus.Close() }()
	buf := &bytes.Buffer{}
	logger := slog.New(slog.NewTextHandler(buf, nil))

	uc := usecase.NewDefaultTopologyUseCase(repo, invRepo, bus, logger)
	ctx := context.Background()

	dev1 := uuid.New()
	dev2 := uuid.New()

	t.Run("CreateLink Nil Payload", func(t *testing.T) {
		_, err := uc.CreateLink(ctx, nil)
		if !errors.Is(err, usecase.ErrInvalidInput) {
			t.Errorf("expected ErrInvalidInput, got %v", err)
		}
	})

	t.Run("CreateLink Invalid Device IDs", func(t *testing.T) {
		req := &dto.CreateTopologyLinkRequest{SourceDeviceID: dev1, TargetDeviceID: dev1}
		_, err := uc.CreateLink(ctx, req)
		if !errors.Is(err, usecase.ErrInvalidInput) {
			t.Errorf("expected ErrInvalidInput, got %v", err)
		}
	})

	t.Run("CreateLink Success and DB Error", func(t *testing.T) {
		req := &dto.CreateTopologyLinkRequest{
			SourceDeviceID: dev1,
			TargetDeviceID: dev2,
			LinkType:       dto.LinkTypeManual,
		}
		link, err := uc.CreateLink(ctx, req)
		if err != nil {
			t.Fatalf("expected nil error, got %v", err)
		}
		if link.LinkType != dto.LinkTypeManual {
			t.Errorf("expected linkType manual, got %s", link.LinkType)
		}

		repo.failCreate = true
		_, err = uc.CreateLink(ctx, req)
		if err == nil {
			t.Error("expected error when repo.CreateLink fails")
		}
		repo.failCreate = false
	})

	t.Run("GetLinkByID Success & Invalid UUID & NotFound", func(t *testing.T) {
		links, _ := uc.ListLinks(ctx, "", "", "", 1, 100)
		if len(links) == 0 {
			t.Fatal("expected at least 1 link")
		}

		l, err := uc.GetLinkByID(ctx, links[0].ID.String())
		if err != nil {
			t.Fatalf("expected nil error, got %v", err)
		}
		if l.ID != links[0].ID {
			t.Errorf("expected link ID %s, got %s", links[0].ID, l.ID)
		}

		_, err = uc.GetLinkByID(ctx, "invalid-uuid")
		if !errors.Is(err, usecase.ErrInvalidUUID) {
			t.Errorf("expected ErrInvalidUUID, got %v", err)
		}

		_, err = uc.GetLinkByID(ctx, uuid.New().String())
		if !errors.Is(err, topoRepo.ErrLinkNotFound) {
			t.Errorf("expected ErrLinkNotFound, got %v", err)
		}
	})

	t.Run("ListLinks Filters", func(t *testing.T) {
		links, err := uc.ListLinks(ctx, dto.LinkTypeManual, dev1.String(), dev2.String(), 1, 100)
		if err != nil {
			t.Fatalf("expected nil error, got %v", err)
		}
		if len(links) == 0 {
			t.Errorf("expected matching links")
		}

		_, err = uc.ListLinks(ctx, "", "invalid-uuid", "", 1, 100)
		if !errors.Is(err, usecase.ErrInvalidUUID) {
			t.Errorf("expected ErrInvalidUUID for source_device_id, got %v", err)
		}

		_, err = uc.ListLinks(ctx, "", "", "invalid-uuid", 1, 100)
		if !errors.Is(err, usecase.ErrInvalidUUID) {
			t.Errorf("expected ErrInvalidUUID for target_device_id, got %v", err)
		}
	})

	t.Run("DeleteLink Success & Failures", func(t *testing.T) {
		links, _ := uc.ListLinks(ctx, "", "", "", 1, 100)
		err := uc.DeleteLink(ctx, links[0].ID.String())
		if err != nil {
			t.Fatalf("expected nil error on DeleteLink, got %v", err)
		}

		err = uc.DeleteLink(ctx, "invalid-uuid")
		if !errors.Is(err, usecase.ErrInvalidUUID) {
			t.Errorf("expected ErrInvalidUUID, got %v", err)
		}

		repo.failDelete = true
		err = uc.DeleteLink(ctx, uuid.New().String())
		if err == nil {
			t.Error("expected error when DeleteLink fails in repo")
		}
		repo.failDelete = false
	})

	t.Run("GetGraph Success", func(t *testing.T) {
		graph, err := uc.GetGraph(ctx)
		if err != nil {
			t.Fatalf("expected nil error on GetGraph, got %v", err)
		}
		if len(graph.Nodes) != 2 {
			t.Errorf("expected 2 nodes, got %d", len(graph.Nodes))
		}
	})

	t.Run("HandleDeviceEvent Auto-Infers Proxmox & Docker Links", func(t *testing.T) {
		parentPVEID := uuid.New()
		childVMID := uuid.New()

		parentMeta, _ := json.Marshal(map[string]interface{}{
			"proxmox": map[string]interface{}{"is_host": true},
		})
		childMeta, _ := json.Marshal(map[string]interface{}{
			"proxmox": map[string]interface{}{"vm_id": 101},
		})

		invRepo.devices = []db.Device{
			{ID: parentPVEID, Hostname: "pve-host-01", Metadata: parentMeta},
			{ID: childVMID, Hostname: "vm-101-ubuntu", Metadata: childMeta},
		}

		event := eventbus.NewBaseEvent("device.created", map[string]interface{}{"id": childVMID.String()})
		err := uc.HandleDeviceEvent(ctx, event)
		if err != nil {
			t.Fatalf("expected nil error on HandleDeviceEvent, got %v", err)
		}

		links, _ := uc.ListLinks(ctx, dto.LinkTypeVirtualHypervisor, "", "", 1, 100)
		if len(links) == 0 {
			t.Fatal("expected virtual_hypervisor link to be inferred")
		}
		if links[0].SourceDeviceID != parentPVEID || links[0].TargetDeviceID != childVMID {
			t.Errorf("expected link between %s and %s", parentPVEID, childVMID)
		}
	})

	t.Run("HandleDeviceEvent Docker Veth Auto-Inference", func(t *testing.T) {
		parentDocID := uuid.New()
		childContainerID := uuid.New()

		parentMeta, _ := json.Marshal(map[string]interface{}{
			"docker": map[string]interface{}{"is_host": true},
		})
		childMeta, _ := json.Marshal(map[string]interface{}{
			"docker": map[string]interface{}{"container_id": "c998877"},
		})

		invRepo.devices = []db.Device{
			{ID: parentDocID, Hostname: "docker-host-01", Metadata: parentMeta},
			{ID: childContainerID, Hostname: "nginx-container", Metadata: childMeta},
		}

		event := eventbus.NewBaseEvent("device.created", map[string]interface{}{"id": childContainerID.String()})
		err := uc.HandleDeviceEvent(ctx, event)
		if err != nil {
			t.Fatalf("expected nil error on HandleDeviceEvent, got %v", err)
		}

		links, _ := uc.ListLinks(ctx, dto.LinkTypeContainerVeth, "", "", 1, 100)
		if len(links) == 0 {
			t.Fatal("expected container_veth link to be inferred")
		}
	})

	t.Run("HandleDeviceEvent Ignored Event Cases", func(t *testing.T) {
		if err := uc.HandleDeviceEvent(ctx, nil); err != nil {
			t.Errorf("expected nil error for nil event, got %v", err)
		}

		invalidPayload := eventbus.NewBaseEvent("device.created", "invalid-payload-string")
		if err := uc.HandleDeviceEvent(ctx, invalidPayload); err != nil {
			t.Errorf("expected nil error for invalid payload, got %v", err)
		}

		noIDPayload := eventbus.NewBaseEvent("device.created", map[string]interface{}{"key": "val"})
		if err := uc.HandleDeviceEvent(ctx, noIDPayload); err != nil {
			t.Errorf("expected nil error for no ID, got %v", err)
		}

		invalidUUIDPayload := eventbus.NewBaseEvent("device.created", map[string]interface{}{"id": "not-a-uuid"})
		if err := uc.HandleDeviceEvent(ctx, invalidUUIDPayload); err != nil {
			t.Errorf("expected nil error for invalid UUID, got %v", err)
		}
	})
}
