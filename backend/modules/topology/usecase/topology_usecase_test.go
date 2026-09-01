package usecase_test

import (
	"bytes"
	"context"
	"encoding/json"
	"errors"
	"log/slog"
	"strings"
	"testing"
	"time"

	"github.com/google/uuid"
	"github.com/jackc/pgx/v5/pgtype"
	"github.com/matheussouza/inframap/internal/platform/db"
	"github.com/matheussouza/inframap/internal/platform/eventbus"
	inventoryRepo "github.com/matheussouza/inframap/modules/inventory/repository"
	"github.com/matheussouza/inframap/modules/topology/dto"
	topoRepo "github.com/matheussouza/inframap/modules/topology/repository"
	"github.com/matheussouza/inframap/modules/topology/usecase"
)

type mockTopoRepo struct {
	links map[uuid.UUID]*dto.TopologyLinkResponse

	failCreate       bool
	failDelete       bool
	failContainment  bool
	containmentCalls []topoRepo.ContainmentLinkRequest
}

func (m *mockTopoRepo) ReplaceContainmentLink(_ context.Context, req *topoRepo.ContainmentLinkRequest) error {
	if m.failContainment {
		return errors.New("containment failure")
	}
	m.containmentCalls = append(m.containmentCalls, *req)
	return nil
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
	devicesByProviderRef map[string]db.Device
	pendingChildren      map[string][]db.Device
	parentAssignments    map[uuid.UUID]uuid.UUID

	devicesByProviderScope map[string][]db.Device
	absenceCalls           []uuid.UUID
	absenceCounts          map[uuid.UUID]int16

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

	t.Run("HandleDeviceEvent anchors a workload to its declared host", func(t *testing.T) {
		nodeID := uuid.New()
		vmID := uuid.New()

		nodeMeta, _ := json.Marshal(map[string]interface{}{"provider_ref": "proxmox:pve-cluster:node:pve-node1"})
		vmMeta, _ := json.Marshal(map[string]interface{}{"provider_ref": "proxmox:pve-cluster:qemu:101"})

		vm := db.Device{
			ID:                vmID,
			Hostname:          "vm-101-ubuntu",
			Metadata:          vmMeta,
			ParentProviderRef: pgtype.Text{String: "proxmox:pve-cluster:node:pve-node1", Valid: true},
		}
		invRepo.devices = []db.Device{{ID: nodeID, Hostname: "pve-node1", Metadata: nodeMeta}, vm}
		invRepo.devicesByProviderRef = map[string]db.Device{
			"proxmox:pve-cluster:node:pve-node1": {ID: nodeID, Hostname: "pve-node1", Metadata: nodeMeta},
		}
		repo.containmentCalls = nil

		event := eventbus.NewBaseEvent("device.created", map[string]interface{}{"id": vmID.String()})
		if err := uc.HandleDeviceEvent(ctx, event); err != nil {
			t.Fatalf("expected nil error on HandleDeviceEvent, got %v", err)
		}

		if len(repo.containmentCalls) != 1 {
			t.Fatalf("expected one containment link, got %d", len(repo.containmentCalls))
		}
		call := repo.containmentCalls[0]
		if call.ParentDeviceID != nodeID || call.ChildDeviceID != vmID {
			t.Errorf("expected a link from %s to %s, got %s to %s", nodeID, vmID, call.ParentDeviceID, call.ChildDeviceID)
		}
		if call.LinkType != dto.LinkTypeHostedOn {
			t.Errorf("link type = %q, want %q", call.LinkType, dto.LinkTypeHostedOn)
		}
		if call.DiscoveredBy != "proxmox" {
			t.Errorf("discovered_by = %q, want proxmox", call.DiscoveredBy)
		}
		if invRepo.parentAssignments[vmID] != nodeID {
			t.Errorf("expected parent_device_id to be anchored to %s", nodeID)
		}
	})

	t.Run("HandleDeviceEvent adopts children discovered before their host", func(t *testing.T) {
		engineID := uuid.New()
		containerID := uuid.New()

		engineMeta, _ := json.Marshal(map[string]interface{}{"provider_ref": "docker:lab:engine:daemon-1"})
		containerMeta, _ := json.Marshal(map[string]interface{}{"provider_ref": "docker:lab:container:abc"})

		engineDev := db.Device{ID: engineID, Hostname: "docker-host", Metadata: engineMeta}
		invRepo.devices = []db.Device{engineDev}
		invRepo.devicesByProviderRef = map[string]db.Device{"docker:lab:engine:daemon-1": engineDev}
		// Discovery order is not guaranteed, so the container was already waiting.
		invRepo.pendingChildren = map[string][]db.Device{
			"docker:lab:engine:daemon-1": {{
				ID:                containerID,
				Hostname:          "nginx",
				Metadata:          containerMeta,
				ParentProviderRef: pgtype.Text{String: "docker:lab:engine:daemon-1", Valid: true},
			}},
		}
		repo.containmentCalls = nil

		event := eventbus.NewBaseEvent("device.created", map[string]interface{}{"id": engineID.String()})
		if err := uc.HandleDeviceEvent(ctx, event); err != nil {
			t.Fatalf("expected nil error on HandleDeviceEvent, got %v", err)
		}

		if len(repo.containmentCalls) != 1 {
			t.Fatalf("expected the pending container to be adopted, got %d links", len(repo.containmentCalls))
		}
		if repo.containmentCalls[0].ChildDeviceID != containerID {
			t.Errorf("expected the pending container %s to be linked", containerID)
		}
		invRepo.pendingChildren = nil
	})

	t.Run("HandleDeviceEvent emits topology.reparented when a workload migrates", func(t *testing.T) {
		previousNodeID := uuid.New()
		newNodeID := uuid.New()
		vmID := uuid.New()

		newNodeMeta, _ := json.Marshal(map[string]interface{}{"provider_ref": "proxmox:pve-cluster:node:pve-node2"})
		vmMeta, _ := json.Marshal(map[string]interface{}{"provider_ref": "proxmox:pve-cluster:qemu:101"})

		// The VM is already anchored to another node: this run reports it on a new one.
		migratedVM := db.Device{
			ID:                vmID,
			Hostname:          "vm-101-ubuntu",
			Metadata:          vmMeta,
			ParentProviderRef: pgtype.Text{String: "proxmox:pve-cluster:node:pve-node2", Valid: true},
			ParentDeviceID:    pgtype.UUID{Bytes: previousNodeID, Valid: true},
		}
		newNode := db.Device{ID: newNodeID, Hostname: "pve-node2", Metadata: newNodeMeta}
		invRepo.devices = []db.Device{newNode, migratedVM}
		invRepo.devicesByProviderRef = map[string]db.Device{"proxmox:pve-cluster:node:pve-node2": newNode}
		repo.containmentCalls = nil

		received := make(chan eventbus.DomainEvent, 4)
		if err := bus.Subscribe("topology.reparented", func(_ context.Context, e eventbus.DomainEvent) error {
			received <- e
			return nil
		}); err != nil {
			t.Fatalf("failed to subscribe to topology.reparented: %v", err)
		}

		event := eventbus.NewBaseEvent("device.updated", map[string]interface{}{"id": vmID.String()})
		if err := uc.HandleDeviceEvent(ctx, event); err != nil {
			t.Fatalf("expected nil error on HandleDeviceEvent, got %v", err)
		}

		select {
		case got := <-received:
			payload, ok := got.Payload().(map[string]interface{})
			if !ok {
				t.Fatal("expected a map payload on topology.reparented")
			}
			if payload["previous_host_id"] != previousNodeID.String() {
				t.Errorf("previous_host_id = %v, want %s", payload["previous_host_id"], previousNodeID)
			}
			if payload["new_host_id"] != newNodeID.String() {
				t.Errorf("new_host_id = %v, want %s", payload["new_host_id"], newNodeID)
			}
		case <-time.After(2 * time.Second):
			t.Fatal("expected topology.reparented to be published on migration")
		}
	})

	t.Run("HandleDeviceEvent logs and gives up when the link cannot be written", func(t *testing.T) {
		nodeID := uuid.New()
		vmID := uuid.New()

		nodeMeta, _ := json.Marshal(map[string]interface{}{"provider_ref": "proxmox:pve-cluster:node:pve-fail"})
		vmMeta, _ := json.Marshal(map[string]interface{}{"provider_ref": "proxmox:pve-cluster:qemu:999"})

		node := db.Device{ID: nodeID, Hostname: "pve-fail-host", Metadata: nodeMeta}
		invRepo.devices = []db.Device{node, {
			ID:                vmID,
			Hostname:          "vm-fail-child",
			Metadata:          vmMeta,
			ParentProviderRef: pgtype.Text{String: "proxmox:pve-cluster:node:pve-fail", Valid: true},
		}}
		invRepo.devicesByProviderRef = map[string]db.Device{"proxmox:pve-cluster:node:pve-fail": node}
		delete(invRepo.parentAssignments, vmID)

		repo.failContainment = true
		event := eventbus.NewBaseEvent("device.created", map[string]interface{}{"id": vmID.String()})
		if err := uc.HandleDeviceEvent(ctx, event); err != nil {
			t.Fatalf("expected nil error, got %v", err)
		}
		repo.failContainment = false

		if !strings.Contains(buf.String(), "failed to materialize containment link") {
			t.Error("expected a warning when the containment link cannot be written")
		}
		// The device must not be anchored to a host whose edge does not exist.
		if _, anchored := invRepo.parentAssignments[vmID]; anchored {
			t.Error("expected no parent to be recorded when the link write failed")
		}
	})

	t.Run("HandleDeviceEvent leaves a workload pending until its host appears", func(t *testing.T) {
		orphanID := uuid.New()
		orphanMeta, _ := json.Marshal(map[string]interface{}{"provider_ref": "docker:lab:container:orphan"})

		invRepo.devices = []db.Device{{
			ID:                orphanID,
			Hostname:          "orphan",
			Metadata:          orphanMeta,
			ParentProviderRef: pgtype.Text{String: "docker:lab:engine:not-yet-seen", Valid: true},
		}}
		invRepo.devicesByProviderRef = map[string]db.Device{}
		repo.containmentCalls = nil

		event := eventbus.NewBaseEvent("device.created", map[string]interface{}{"id": orphanID.String()})
		if err := uc.HandleDeviceEvent(ctx, event); err != nil {
			t.Fatalf("expected an undiscovered host to be tolerated, got %v", err)
		}
		if len(repo.containmentCalls) != 0 {
			t.Errorf("expected no link before the host is discovered, got %d", len(repo.containmentCalls))
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
