// Package usecase provides business logic and auto-inference algorithms for the Topology Engine.
package usecase

import (
	"context"
	"encoding/json"
	"errors"
	"fmt"
	"log/slog"
	"time"

	"github.com/google/uuid"
	"github.com/matheussouza/inframap/internal/platform/db"
	"github.com/matheussouza/inframap/internal/platform/eventbus"
	"github.com/matheussouza/inframap/modules/inventory/repository"
	"github.com/matheussouza/inframap/modules/topology/dto"
	topoRepo "github.com/matheussouza/inframap/modules/topology/repository"
)

var (
	// ErrInvalidUUID indicates that a provided UUID string is malformed.
	ErrInvalidUUID = errors.New("invalid UUID format")
	// ErrInvalidInput indicates that the input payload failed validation.
	ErrInvalidInput = errors.New("invalid input")
)

// TopologyUseCase defines the core business operations for network topology.
type TopologyUseCase interface {
	CreateLink(ctx context.Context, req *dto.CreateTopologyLinkRequest) (*dto.TopologyLinkResponse, error)
	GetLinkByID(ctx context.Context, idStr string) (*dto.TopologyLinkResponse, error)
	ListLinks(ctx context.Context, linkType, sourceIDStr, targetIDStr string, page, limit int32) ([]*dto.TopologyLinkResponse, error)
	DeleteLink(ctx context.Context, idStr string) error
	GetGraph(ctx context.Context) (*dto.TopologyGraphResponse, error)
	HandleDeviceEvent(ctx context.Context, event eventbus.DomainEvent) error
}

// DefaultTopologyUseCase implements TopologyUseCase.
type DefaultTopologyUseCase struct {
	repo     topoRepo.TopologyRepository
	invRepo  repository.InventoryRepository
	eventBus eventbus.EventBus
	logger   *slog.Logger
}

// NewDefaultTopologyUseCase constructs a DefaultTopologyUseCase.
func NewDefaultTopologyUseCase(
	repo topoRepo.TopologyRepository,
	invRepo repository.InventoryRepository,
	eventBus eventbus.EventBus,
	logger *slog.Logger,
) *DefaultTopologyUseCase {
	if logger == nil {
		logger = slog.Default()
	}
	return &DefaultTopologyUseCase{
		repo:     repo,
		invRepo:  invRepo,
		eventBus: eventBus,
		logger:   logger,
	}
}

// CreateLink normalizes, validates, and inserts a topology link.
func (u *DefaultTopologyUseCase) CreateLink(ctx context.Context, req *dto.CreateTopologyLinkRequest) (*dto.TopologyLinkResponse, error) {
	if req == nil {
		return nil, ErrInvalidInput
	}
	req.Normalize()
	if err := req.Validate(); err != nil {
		return nil, fmt.Errorf("%w: %v", ErrInvalidInput, err)
	}

	link, err := u.repo.CreateLink(ctx, req)
	if err != nil {
		u.logger.Error("failed to create topology link", "error", err)
		return nil, err
	}

	u.publishTopologyUpdated(ctx, link.ID, "created", link.LinkType)
	return link, nil
}

// GetLinkByID retrieves a link by string UUID.
func (u *DefaultTopologyUseCase) GetLinkByID(ctx context.Context, idStr string) (*dto.TopologyLinkResponse, error) {
	id, err := uuid.Parse(idStr)
	if err != nil {
		return nil, ErrInvalidUUID
	}
	return u.repo.GetLinkByID(ctx, id)
}

// ListLinks filters links by type or device UUIDs with pagination.
func (u *DefaultTopologyUseCase) ListLinks(ctx context.Context, linkType, sourceIDStr, targetIDStr string, page, limit int32) ([]*dto.TopologyLinkResponse, error) {
	var srcID, tgtID *uuid.UUID
	if sourceIDStr != "" {
		parsedSrc, err := uuid.Parse(sourceIDStr)
		if err != nil {
			return nil, ErrInvalidUUID
		}
		srcID = &parsedSrc
	}
	if targetIDStr != "" {
		parsedTgt, err := uuid.Parse(targetIDStr)
		if err != nil {
			return nil, ErrInvalidUUID
		}
		tgtID = &parsedTgt
	}

	return u.repo.ListLinks(ctx, linkType, srcID, tgtID, page, limit)
}

// DeleteLink removes a topology link by string UUID.
func (u *DefaultTopologyUseCase) DeleteLink(ctx context.Context, idStr string) error {
	id, err := uuid.Parse(idStr)
	if err != nil {
		return ErrInvalidUUID
	}
	if err := u.repo.DeleteLink(ctx, id); err != nil {
		u.logger.Error("failed to delete topology link", "id", idStr, "error", err)
		return err
	}
	u.publishTopologyUpdated(ctx, id, "deleted", "manual")
	return nil
}

// GetGraph builds the topology graph representation.
func (u *DefaultTopologyUseCase) GetGraph(ctx context.Context) (*dto.TopologyGraphResponse, error) {
	return u.repo.GetGraphData(ctx)
}

// HandleDeviceEvent listens to device.created and device.updated events to infer virtual links.
func (u *DefaultTopologyUseCase) HandleDeviceEvent(ctx context.Context, event eventbus.DomainEvent) error {
	if event == nil {
		return nil
	}
	payload, ok := event.Payload().(map[string]interface{})
	if !ok {
		return nil
	}

	idVal, exists := payload["id"]
	if !exists {
		return nil
	}
	devIDStr := fmt.Sprintf("%v", idVal)
	devID, err := uuid.Parse(devIDStr)
	if err != nil {
		return nil
	}

	// Fetch device details
	device, err := u.invRepo.GetDeviceByID(ctx, devID, false)
	if err != nil || device == nil || len(device.Metadata) == 0 {
		return nil
	}

	var meta map[string]interface{}
	if err := json.Unmarshal(device.Metadata, &meta); err != nil {
		return nil
	}

	// Infer Proxmox Virtual Links
	if proxmox, ok := meta["proxmox"].(map[string]interface{}); ok {
		if vmIDVal, exists := proxmox["vm_id"]; exists && vmIDVal != nil {
			u.inferVirtualLink(ctx, device, fmt.Sprintf("%v", vmIDVal), dto.LinkTypeVirtualHypervisor, "proxmox")
		}
	}

	// Infer Docker Virtual Links
	if docker, ok := meta["docker"].(map[string]interface{}); ok {
		if containerIDVal, exists := docker["container_id"]; exists && containerIDVal != nil {
			u.inferVirtualLink(ctx, device, fmt.Sprintf("%v", containerIDVal), dto.LinkTypeContainerVeth, "docker")
		}
	}

	return nil
}

func (u *DefaultTopologyUseCase) inferVirtualLink(ctx context.Context, childDevice *db.Device, providerUUID, linkType, provider string) {
	// Paginate through active devices to find matching parent host
	var page int32 = 1
	var limit int32 = 1000

	for {
		devices, _, err := u.invRepo.ListDevices(ctx, "", "", page, limit, false)
		if err != nil || len(devices) == 0 {
			break
		}

		for i := range devices {
			parent := &devices[i]
			if parent.ID == childDevice.ID || len(parent.Metadata) == 0 {
				continue
			}

			var parentMeta map[string]interface{}
			if err := json.Unmarshal(parent.Metadata, &parentMeta); err != nil {
				continue
			}

			// Parent device is a hypervisor/docker host
			if parentProv, ok := parentMeta[provider].(map[string]interface{}); ok {
				if isHost, ok := parentProv["is_host"].(bool); ok && isHost {
					// Create virtual link from parent to child
					req := &dto.CreateTopologyLinkRequest{
						SourceDeviceID: parent.ID,
						TargetDeviceID: childDevice.ID,
						LinkType:       linkType,
						DiscoveredBy:   provider,
						Metadata: map[string]interface{}{
							"auto_inferred": true,
							"provider_uuid": providerUUID,
						},
					}
					req.Normalize()
					if _, err := u.repo.CreateLink(ctx, req); err != nil {
						u.logger.Warn("failed to auto-infer virtual link", "parent", parent.ID, "child", childDevice.ID, "error", err)
					} else {
						u.publishTopologyUpdated(ctx, parent.ID, "inferred", linkType)
					}
					return
				}
			}
		}

		if len(devices) < int(limit) {
			break
		}
		page++
	}
}

func (u *DefaultTopologyUseCase) publishTopologyUpdated(ctx context.Context, linkID uuid.UUID, action, linkType string) {
	if u.eventBus == nil {
		return
	}
	event := eventbus.NewBaseEvent("topology.updated", map[string]interface{}{
		"link_id":   linkID.String(),
		"action":    action,
		"link_type": linkType,
		"timestamp": time.Now().Format(time.RFC3339),
	})
	if err := u.eventBus.Publish(ctx, event); err != nil {
		u.logger.Warn("failed to publish topology.updated event", "error", err)
	}
}
