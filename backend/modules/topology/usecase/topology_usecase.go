// Package usecase provides business logic and auto-inference algorithms for the Topology Engine.
package usecase

import (
	"context"
	"encoding/json"
	"errors"
	"fmt"
	"log/slog"
	"strings"
	"time"

	"github.com/google/uuid"
	"github.com/matheussouza/inframap/internal/platform/db"
	"github.com/matheussouza/inframap/internal/platform/eventbus"
	"github.com/matheussouza/inframap/internal/platform/sdk"
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

	return u.resolveContainment(ctx, device)
}

// resolveContainment anchors a workload to the host that runs it, in both directions.
//
// It replaces the previous inference, which paginated the whole inventory looking for a
// device whose metadata happened to carry is_host — a scan whose outcome depended on
// discovery ordering and which could not tell two hosts apart. Parentage is now taken from
// what the provider itself declared in parent_provider_ref (ADR-013 section 4).
//
// Resolution runs in two directions because discovery order is not guaranteed: the device
// may be a child looking for its host, or a host that has to adopt children discovered
// before it.
func (u *DefaultTopologyUseCase) resolveContainment(ctx context.Context, device *db.Device) error {
	if device.ParentProviderRef.Valid && device.ParentProviderRef.String != "" {
		u.anchorToParent(ctx, device)
	}

	if ref := deviceProviderRef(device); ref != "" {
		u.adoptPendingChildren(ctx, device, ref)
	}

	return nil
}

// anchorToParent resolves a workload's declared parent to a device and links the two.
func (u *DefaultTopologyUseCase) anchorToParent(ctx context.Context, child *db.Device) {
	parentRef := child.ParentProviderRef.String

	parent, err := u.invRepo.GetDeviceByProviderRef(ctx, parentRef)
	if err != nil || parent == nil {
		// The host has not been discovered yet. The child stays pending and will be adopted
		// when the host shows up, so this is expected rather than an error.
		return
	}

	u.linkContainment(ctx, parent, child)
}

// adoptPendingChildren links the workloads that declared this device as their host but were
// discovered before it existed in the inventory.
func (u *DefaultTopologyUseCase) adoptPendingChildren(ctx context.Context, parent *db.Device, parentRef string) {
	children, err := u.invRepo.ListDevicesPendingParentResolution(ctx, parentRef)
	if err != nil {
		u.logger.Warn("failed to list workloads pending parent resolution", "parent_ref", parentRef, "error", err)
		return
	}

	for i := range children {
		u.linkContainment(ctx, parent, &children[i])
	}
}

// linkContainment records the parentage on the child and materializes the containment edge.
//
// When the parent changes — a VM migrating between cluster nodes — the device record and
// the topology edge move together and topology.reparented is emitted, so consumers can tell
// a migration apart from a fresh discovery.
func (u *DefaultTopologyUseCase) linkContainment(ctx context.Context, parent, child *db.Device) {
	if parent.ID == child.ID {
		return
	}

	previousParent := child.ParentDeviceID
	migrated := previousParent.Valid && uuid.UUID(previousParent.Bytes) != parent.ID

	if err := u.repo.ReplaceContainmentLink(ctx, &topoRepo.ContainmentLinkRequest{
		ParentDeviceID: parent.ID,
		ChildDeviceID:  child.ID,
		LinkType:       dto.LinkTypeHostedOn,
		DiscoveredBy:   containmentDiscoveredBy(child),
		Metadata: map[string]interface{}{
			"parent_provider_ref": child.ParentProviderRef.String,
			"provider_ref":        deviceProviderRef(child),
		},
	}); err != nil {
		u.logger.Warn("failed to materialize containment link", "parent", parent.ID, "child", child.ID, "error", err)
		return
	}

	if _, err := u.invRepo.SetDeviceParent(ctx, child.ID, parent.ID, child.ParentProviderRef.String); err != nil {
		u.logger.Warn("failed to anchor workload to its host", "parent", parent.ID, "child", child.ID, "error", err)
		return
	}

	if migrated {
		u.publishReparented(ctx, child, uuid.UUID(previousParent.Bytes), parent.ID)
	}
	u.publishTopologyUpdated(ctx, parent.ID, "hosted_on", dto.LinkTypeHostedOn)
}

// publishReparented announces that a workload moved to a different host.
func (u *DefaultTopologyUseCase) publishReparented(ctx context.Context, child *db.Device, previousParent, newParent uuid.UUID) {
	if u.eventBus == nil {
		return
	}

	event := eventbus.NewBaseEvent("topology.reparented", map[string]interface{}{
		"device_id":        child.ID.String(),
		"provider_ref":     deviceProviderRef(child),
		"previous_host_id": previousParent.String(),
		"new_host_id":      newParent.String(),
		"timestamp":        time.Now().Format(time.RFC3339),
	})
	if err := u.eventBus.Publish(ctx, event); err != nil {
		u.logger.Warn("failed to publish topology.reparented event", "error", err)
	}
}

// containmentDiscoveredBy names the provider that declared the parentage, taken from the
// first segment of the workload's canonical reference.
func containmentDiscoveredBy(child *db.Device) string {
	ref, err := sdk.ParseProviderRef(deviceProviderRef(child))
	if err != nil {
		return "provider"
	}
	return ref.Provider
}

// deviceProviderRef reads the canonical provider identity stored on a device.
func deviceProviderRef(device *db.Device) string {
	if len(device.Metadata) == 0 {
		return ""
	}
	var metadata map[string]interface{}
	if err := json.Unmarshal(device.Metadata, &metadata); err != nil {
		return ""
	}
	ref, _ := metadata["provider_ref"].(string)
	return strings.TrimSpace(ref)
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
