// Package usecase implements application business logic for the Discovery Engine.
package usecase

import (
	"context"
	"encoding/json"
	"errors"
	"fmt"
	"net"
	"net/netip"
	"strings"

	"github.com/google/uuid"
	"github.com/jackc/pgx/v5/pgtype"
	"github.com/matheussouza/inframap/internal/platform/db"
	"github.com/matheussouza/inframap/internal/platform/eventbus"
	"github.com/matheussouza/inframap/modules/discovery/dto"
	"github.com/matheussouza/inframap/modules/discovery/engine"
	"github.com/matheussouza/inframap/modules/discovery/repository"
	inventoryRepo "github.com/matheussouza/inframap/modules/inventory/repository"
	inventoryUC "github.com/matheussouza/inframap/modules/inventory/usecase"
)

var (
	// ErrInvalidPayload indicates that the raw discovery payload is malformed.
	ErrInvalidPayload = errors.New("invalid discovery payload format")
)

// DiscoveryUseCase contract defines application orchestration for discovery sources and scan ingestion.
type DiscoveryUseCase interface {
	CreateSource(ctx context.Context, req *dto.CreateDiscoverySourceRequest) (*dto.DiscoverySourceResponse, error)
	GetSourceByID(ctx context.Context, id string) (*dto.DiscoverySourceResponse, error)
	ListSources(ctx context.Context) ([]*dto.DiscoverySourceResponse, error)
	TriggerRun(ctx context.Context, sourceID string) (*dto.DiscoverySourceResponse, error)
	IngestNormalizedDevice(ctx context.Context, sourceID uuid.UUID, norm *dto.NormalizedDeviceDTO) (*dto.DiscoveryRecordResponse, error)
	ListRecordsByDevice(ctx context.Context, deviceID string) ([]*dto.DiscoveryRecordResponse, error)
}

// DefaultDiscoveryUseCase implements DiscoveryUseCase interface.
type DefaultDiscoveryUseCase struct {
	discRepo   repository.DiscoveryRepository
	invRepo    inventoryRepo.InventoryRepository
	eventBus   eventbus.EventBus
	matcher    engine.IdentityMatcher
	reconciler engine.FieldReconciler
}

// NewDefaultDiscoveryUseCase constructs a DefaultDiscoveryUseCase instance.
func NewDefaultDiscoveryUseCase(
	discRepo repository.DiscoveryRepository,
	invRepo inventoryRepo.InventoryRepository,
	eventBus eventbus.EventBus,
) *DefaultDiscoveryUseCase {
	return &DefaultDiscoveryUseCase{
		discRepo:   discRepo,
		invRepo:    invRepo,
		eventBus:   eventBus,
		matcher:    engine.NewDefaultIdentityMatcher(),
		reconciler: engine.NewDefaultFieldReconciler(),
	}
}

// CreateSource registers a new discovery source after normalization and validation.
func (u *DefaultDiscoveryUseCase) CreateSource(ctx context.Context, req *dto.CreateDiscoverySourceRequest) (*dto.DiscoverySourceResponse, error) {
	req.Normalize()
	if err := req.Validate(); err != nil {
		return nil, fmt.Errorf("%w: %v", inventoryUC.ErrInvalidInput, err)
	}

	return u.discRepo.CreateSource(ctx, req)
}

// GetSourceByID parses UUID and fetches a discovery source.
func (u *DefaultDiscoveryUseCase) GetSourceByID(ctx context.Context, idStr string) (*dto.DiscoverySourceResponse, error) {
	id, err := uuid.Parse(idStr)
	if err != nil {
		return nil, inventoryUC.ErrInvalidUUID
	}
	return u.discRepo.GetSourceByID(ctx, id)
}

// ListSources returns all discovery sources.
func (u *DefaultDiscoveryUseCase) ListSources(ctx context.Context) ([]*dto.DiscoverySourceResponse, error) {
	return u.discRepo.ListSources(ctx)
}

// TriggerRun triggers a manual scan sweep for a discovery source.
func (u *DefaultDiscoveryUseCase) TriggerRun(ctx context.Context, idStr string) (*dto.DiscoverySourceResponse, error) {
	id, err := uuid.Parse(idStr)
	if err != nil {
		return nil, inventoryUC.ErrInvalidUUID
	}

	source, err := u.discRepo.GetSourceByID(ctx, id)
	if err != nil {
		return nil, err
	}

	_ = sanitizeLogInput(source.Name)
	return u.discRepo.UpdateSourceStatus(ctx, source.ID, "running")
}

// IngestNormalizedDevice processes a normalized scan observation:
// 1. Matches identity against active inventory using Matcher.
// 2. If matched: reconciles fields using Reconciler, updates device in inventory, creates record.
// 3. If no match: auto-approves direct provider sources or routes generic sweeps to Staging.
func (u *DefaultDiscoveryUseCase) IngestNormalizedDevice(ctx context.Context, sourceID uuid.UUID, norm *dto.NormalizedDeviceDTO) (*dto.DiscoveryRecordResponse, error) {
	source, err := u.discRepo.GetSourceByID(ctx, sourceID)
	if err != nil {
		return nil, fmt.Errorf("failed to fetch discovery source: %w", err)
	}

	activeDevices, _, err := u.invRepo.ListDevices(ctx, "", "", 1000, 0, false)
	if err != nil {
		return nil, fmt.Errorf("failed to list active devices: %w", err)
	}

	match := u.matcher.MatchDevice(norm, activeDevices)

	var targetDeviceID uuid.UUID
	var matchedBy string

	rawBytes, _ := json.Marshal(norm.RawPayload)

	if match.DeviceID != nil {
		targetDeviceID = *match.DeviceID
		matchedBy = match.MatchedBy

		existingDB, fetchErr := u.invRepo.GetDeviceByID(ctx, targetDeviceID, false)
		if fetchErr == nil {
			reconciledDB, changed := u.reconciler.Reconcile(existingDB, norm, source.Type)
			if changed {
				updateParams := db.UpdateDeviceParams{
					ID:           reconciledDB.ID,
					Hostname:     reconciledDB.Hostname,
					IpAddress:    reconciledDB.IpAddress,
					MacAddress:   reconciledDB.MacAddress,
					Manufacturer: reconciledDB.Manufacturer,
					Model:        reconciledDB.Model,
					SerialNumber: reconciledDB.SerialNumber,
					DeviceType:   reconciledDB.DeviceType,
					Status:       reconciledDB.Status,
					Metadata:     reconciledDB.Metadata,
				}
				_, updateErr := u.invRepo.UpdateDevice(ctx, updateParams)
				if updateErr == nil && u.eventBus != nil {
					_ = u.eventBus.Publish(ctx, eventbus.NewBaseEvent("device.updated", map[string]interface{}{
						"device_id":   targetDeviceID.String(),
						"matched_by":  matchedBy,
						"source_type": source.Type,
					}))
				}
			}
		}
	} else {
		matchedBy = "new_discovery"
		if isTrustedProvider(source.Type) {
			createParams := db.CreateDeviceParams{
				ID:         uuid.New(),
				Hostname:   norm.Hostname,
				DeviceType: norm.DeviceType,
				Status:     "active",
				Metadata:   rawBytes,
			}
			if norm.IPAddress != "" {
				if addr, err := netip.ParseAddr(norm.IPAddress); err == nil {
					createParams.IpAddress = &addr
				}
			}
			if norm.MACAddress != "" {
				if hw, err := net.ParseMAC(norm.MACAddress); err == nil {
					createParams.MacAddress = hw
				}
			}
			if norm.Manufacturer != "" {
				createParams.Manufacturer = pgtype.Text{String: norm.Manufacturer, Valid: true}
			}
			if norm.Model != "" {
				createParams.Model = pgtype.Text{String: norm.Model, Valid: true}
			}
			if norm.SerialNumber != "" {
				createParams.SerialNumber = pgtype.Text{String: norm.SerialNumber, Valid: true}
			}

			newDev, createErr := u.invRepo.CreateDevice(ctx, createParams)
			if createErr != nil {
				return nil, fmt.Errorf("failed to auto-approve device: %w", createErr)
			}
			targetDeviceID = newDev.ID
			if u.eventBus != nil {
				_ = u.eventBus.Publish(ctx, eventbus.NewBaseEvent("device.created", map[string]interface{}{
					"device_id": targetDeviceID.String(),
					"source":    source.Type,
				}))
			}
		} else {
			stageParams := db.CreateStagingDeviceParams{
				ID:                uuid.New(),
				Hostname:          norm.Hostname,
				DeviceType:        norm.DeviceType,
				DiscoverySourceID: pgtype.UUID{Bytes: source.ID, Valid: true},
				RawPayload:        rawBytes,
				Status:            "discovered",
			}
			if norm.IPAddress != "" {
				if addr, err := netip.ParseAddr(norm.IPAddress); err == nil {
					stageParams.IpAddress = &addr
				}
			}
			if norm.MACAddress != "" {
				if hw, err := net.ParseMAC(norm.MACAddress); err == nil {
					stageParams.MacAddress = hw
				}
			}
			staged, stageErr := u.invRepo.CreateStagingDevice(ctx, stageParams)
			if stageErr != nil {
				return nil, fmt.Errorf("failed to create staging device: %w", stageErr)
			}
			targetDeviceID = staged.ID
			if u.eventBus != nil {
				_ = u.eventBus.Publish(ctx, eventbus.NewBaseEvent("device.staged", map[string]interface{}{
					"staging_id": staged.ID.String(),
					"source":     source.Type,
				}))
			}
		}
	}

	return u.discRepo.UpsertRecord(ctx, targetDeviceID, source.ID, matchedBy, norm.RawPayload)
}

// ListRecordsByDevice fetches discovery records for a given device ID.
func (u *DefaultDiscoveryUseCase) ListRecordsByDevice(ctx context.Context, deviceIDStr string) ([]*dto.DiscoveryRecordResponse, error) {
	deviceID, err := uuid.Parse(deviceIDStr)
	if err != nil {
		return nil, inventoryUC.ErrInvalidUUID
	}
	return u.discRepo.ListRecordsByDevice(ctx, deviceID)
}

func isTrustedProvider(sourceType string) bool {
	switch sourceType {
	case "proxmox", "docker", "unifi":
		return true
	default:
		return false
	}
}

func sanitizeLogInput(input string) string {
	return strings.Map(func(r rune) rune {
		if r < 0x20 || r == 0x7F {
			return -1
		}
		return r
	}, input)
}
