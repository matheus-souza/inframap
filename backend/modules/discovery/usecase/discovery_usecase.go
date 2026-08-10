// Package usecase implements application business logic for the Discovery Engine.
package usecase

import (
	"context"
	"encoding/json"
	"errors"
	"fmt"
	"log/slog"
	"net"
	"net/netip"
	"os"

	"github.com/google/uuid"
	"github.com/jackc/pgx/v5/pgtype"
	"github.com/matheussouza/inframap/internal/platform/db"
	"github.com/matheussouza/inframap/internal/platform/eventbus"
	"github.com/matheussouza/inframap/modules/discovery/collectors"
	"github.com/matheussouza/inframap/modules/discovery/dto"

	"github.com/matheussouza/inframap/modules/discovery/engine"
	"github.com/matheussouza/inframap/modules/discovery/repository"
	inventoryRepo "github.com/matheussouza/inframap/modules/inventory/repository"
)

var (
	// ErrInvalidUUID indicates a malformed UUID string.
	ErrInvalidUUID = errors.New("invalid resource UUID format")

	// ErrInvalidInput indicates malformed user input.
	ErrInvalidInput = errors.New("invalid input")

	// ErrInvalidPayload indicates that the raw discovery payload is malformed.
	ErrInvalidPayload = errors.New("invalid discovery payload format")
)

// DiscoveryUseCase contract defines application orchestration for discovery sources and scan ingestion.
type DiscoveryUseCase interface {
	CreateSource(ctx context.Context, req *dto.CreateDiscoverySourceRequest) (*dto.DiscoverySourceResponse, error)
	GetSourceByID(ctx context.Context, id string) (*dto.DiscoverySourceResponse, error)
	ListSources(ctx context.Context) ([]*dto.DiscoverySourceResponse, error)
	TriggerRun(ctx context.Context, sourceID string) (*dto.DiscoverySourceResponse, error)
	TriggerScan(ctx context.Context, req *dto.TriggerScanRequest) (*dto.ScanResultResponse, error)
	IngestNormalizedDevice(ctx context.Context, sourceID uuid.UUID, norm *dto.NormalizedDeviceDTO) (*dto.DiscoveryRecordResponse, error)
	ListRecordsByDevice(ctx context.Context, deviceID string) ([]*dto.DiscoveryRecordResponse, error)
}

// DefaultDiscoveryUseCase implements DiscoveryUseCase interface.
type DefaultDiscoveryUseCase struct {
	discRepo     repository.DiscoveryRepository
	invRepo      inventoryRepo.InventoryRepository
	eventBus     eventbus.EventBus
	logger       *slog.Logger
	matcher      engine.IdentityMatcher
	reconciler   engine.FieldReconciler
	orchestrator engine.Orchestrator
}

// NewDefaultDiscoveryUseCase constructs a DefaultDiscoveryUseCase instance.
func NewDefaultDiscoveryUseCase(
	discRepo repository.DiscoveryRepository,
	invRepo inventoryRepo.InventoryRepository,
	eventBus eventbus.EventBus,
	logger *slog.Logger,
) *DefaultDiscoveryUseCase {
	arpReader := collectors.NewProcNetARPReader(os.ReadFile)
	dnsResolver := collectors.NewNetDNSResolver()

	orch := engine.NewDefaultOrchestrator(eventBus)
	orch.RegisterCollector(collectors.NewICMPCollector(nil))
	orch.RegisterCollector(collectors.NewARPCollector(arpReader))
	orch.RegisterCollector(collectors.NewReverseDNSCollector(dnsResolver))
	orch.RegisterCollector(collectors.NewSNMPCollector(nil, nil))

	uc := &DefaultDiscoveryUseCase{
		discRepo:     discRepo,
		invRepo:      invRepo,
		eventBus:     eventBus,
		logger:       logger,
		matcher:      engine.NewDefaultIdentityMatcher(),
		reconciler:   engine.NewDefaultFieldReconciler(),
		orchestrator: orch,
	}

	orch.SetDeviceCallback(uc.persistDiscoveredDevice)

	return uc
}


// CreateSource registers a new discovery source after normalization and validation.
func (u *DefaultDiscoveryUseCase) CreateSource(ctx context.Context, req *dto.CreateDiscoverySourceRequest) (*dto.DiscoverySourceResponse, error) {
	req.Normalize()
	if err := req.Validate(); err != nil {
		return nil, fmt.Errorf("%w: %v", ErrInvalidInput, err)
	}

	return u.discRepo.CreateSource(ctx, req)
}

// GetSourceByID parses UUID and fetches a discovery source.
func (u *DefaultDiscoveryUseCase) GetSourceByID(ctx context.Context, idStr string) (*dto.DiscoverySourceResponse, error) {
	id, err := uuid.Parse(idStr)
	if err != nil {
		return nil, ErrInvalidUUID
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
		return nil, ErrInvalidUUID
	}

	source, err := u.discRepo.GetSourceByID(ctx, id)
	if err != nil {
		return nil, err
	}

	if _, updateErr := u.discRepo.UpdateSourceStatus(ctx, source.ID, "running"); updateErr != nil {
		return nil, fmt.Errorf("failed to set discovery status to running: %w", updateErr)
	}

	cidr := source.ConfigCIDR
	if cidr == "" {
		if u.logger != nil {
			u.logger.Warn("discovery source has no CIDR configured, skipping scan", slog.String("source_id", source.ID.String()))
		}
	} else {
		scanReq := &dto.TriggerScanRequest{CIDR: cidr}
		if _, scanErr := u.TriggerScan(ctx, scanReq); scanErr != nil {
			if u.logger != nil {
				u.logger.Error("discovery source scan failed", slog.String("source_id", source.ID.String()), slog.Any("error", scanErr))
			}
		}
	}

	res, finishErr := u.discRepo.UpdateSourceStatus(ctx, source.ID, "idle")
	if finishErr != nil {
		return nil, fmt.Errorf("failed to complete discovery run: %w", finishErr)
	}

	return res, nil
}

// TriggerScan executes an active discovery scan across a target CIDR network range.
func (u *DefaultDiscoveryUseCase) TriggerScan(ctx context.Context, req *dto.TriggerScanRequest) (*dto.ScanResultResponse, error) {
	if req == nil {
		return nil, ErrInvalidInput
	}

	req.Normalize()
	if err := req.Validate(); err != nil {
		return nil, fmt.Errorf("%w: %s", ErrInvalidInput, err.Error())
	}

	if _, err := collectors.ParseTargetPrefix(req.CIDR); err != nil {
		return nil, fmt.Errorf("%w: %s", ErrInvalidInput, err.Error())
	}

	var activeDevices []db.Device
	if u.invRepo != nil {
		const pageSize int32 = 1000
		var offset int32
		for {
			devs, total, err := u.invRepo.ListDevices(ctx, "", "", pageSize, offset, false)
			if err != nil {
				return nil, fmt.Errorf("failed to load active inventory: %w", err)
			}
			activeDevices = append(activeDevices, devs...)
			offset += pageSize
			if int64(offset) >= total {
				break
			}
		}
	}



	target := collectors.DiscoveryTarget{
		CIDR:            req.CIDR,
		SubnetID:        req.SubnetID,
		CredentialSetID: req.CredentialSetID,
	}

	res, err := u.orchestrator.RunScan(ctx, target, activeDevices)
	if err != nil {
		return nil, err
	}

	return &dto.ScanResultResponse{
		CIDR:            res.Target.CIDR,
		TotalCollected:  res.TotalCollected,
		TotalValid:      res.TotalValid,
		TotalDiscovered: res.TotalDiscovered,
		TotalUpdated:    res.TotalUpdated,
		DurationMs:      res.Duration.Milliseconds(),
	}, nil
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

	var allActive []db.Device
	offset := int32(0)
	limit := int32(1000)
	for {
		devices, total, fetchErr := u.invRepo.ListDevices(ctx, "", "", limit, offset, false)
		if fetchErr != nil {
			return nil, fmt.Errorf("failed to list active devices: %w", fetchErr)
		}
		allActive = append(allActive, devices...)
		if int64(len(allActive)) >= total || len(devices) == 0 {
			break
		}
		offset += limit
	}

	match := u.matcher.MatchDevice(norm, allActive)

	var targetDeviceID uuid.UUID
	var matchedBy string

	rawBytes, _ := json.Marshal(norm.RawPayload)

	if match.DeviceID != nil {
		targetDeviceID = *match.DeviceID
		matchedBy = match.MatchedBy

		existingDB, fetchErr := u.invRepo.GetDeviceByID(ctx, targetDeviceID, false)
		if fetchErr != nil {
			if u.logger != nil {
				u.logger.Error("matched device not found in inventory", slog.String("device_id", targetDeviceID.String()), slog.Any("error", fetchErr))
			}
			return nil, fmt.Errorf("failed to fetch matched device %s: %w", targetDeviceID, fetchErr)
		}

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
			if updateErr != nil {
				if u.logger != nil {
					u.logger.Error("failed to update reconciled device", slog.String("device_id", targetDeviceID.String()), slog.Any("error", updateErr))
				}
				return nil, fmt.Errorf("failed to update reconciled device %s: %w", targetDeviceID, updateErr)
			}
			if u.eventBus != nil {
				_ = u.eventBus.Publish(ctx, eventbus.NewBaseEvent("device.updated", map[string]interface{}{
					"device_id":   targetDeviceID.String(),
					"matched_by":  matchedBy,
					"source_type": source.Type,
				}))
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
				} else if u.logger != nil {
					u.logger.Warn("invalid IP address in discovery payload", slog.String("value", norm.IPAddress), slog.Any("error", err))
				}
			}
			if norm.MACAddress != "" {
				if hw, err := net.ParseMAC(norm.MACAddress); err == nil {
					createParams.MacAddress = hw
				} else if u.logger != nil {
					u.logger.Warn("invalid MAC address in discovery payload", slog.String("value", norm.MACAddress), slog.Any("error", err))
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
				} else if u.logger != nil {
					u.logger.Warn("invalid IP address in staging payload", slog.String("value", norm.IPAddress), slog.Any("error", err))
				}
			}
			if norm.MACAddress != "" {
				if hw, err := net.ParseMAC(norm.MACAddress); err == nil {
					stageParams.MacAddress = hw
				} else if u.logger != nil {
					u.logger.Warn("invalid MAC address in staging payload", slog.String("value", norm.MACAddress), slog.Any("error", err))
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
		return nil, ErrInvalidUUID
	}
	return u.discRepo.ListRecordsByDevice(ctx, deviceID)
}

// persistDiscoveredDevice is called by the orchestrator for each valid observation.
// New devices go to staging; matched devices are already reconciled in-memory by the orchestrator.
func (u *DefaultDiscoveryUseCase) persistDiscoveredDevice(ctx context.Context, norm *dto.NormalizedDeviceDTO, sourceType string, matched bool) {
	if matched {
		return
	}

	rawBytes, _ := json.Marshal(norm.RawPayload)

	stageParams := db.CreateStagingDeviceParams{
		ID:         uuid.New(),
		Hostname:   norm.Hostname,
		DeviceType: norm.DeviceType,
		RawPayload: rawBytes,
		Status:     "discovered",
	}
	if norm.IPAddress != "" {
		if addr, parseErr := netip.ParseAddr(norm.IPAddress); parseErr == nil {
			stageParams.IpAddress = &addr
		}
	}
	if norm.MACAddress != "" {
		if hw, parseErr := net.ParseMAC(norm.MACAddress); parseErr == nil {
			stageParams.MacAddress = hw
		}
	}

	staged, err := u.invRepo.CreateStagingDevice(ctx, stageParams)
	if err != nil {
		if u.logger != nil {
			u.logger.Error("failed to persist discovered device to staging", slog.String("ip", norm.IPAddress), slog.Any("error", err))
		}
		return
	}

	if u.eventBus != nil {
		_ = u.eventBus.Publish(ctx, eventbus.NewBaseEvent("device.staged", map[string]interface{}{
			"staging_id":  staged.ID.String(),
			"ip_address":  norm.IPAddress,
			"source_type": sourceType,
		}))
	}
}

func isTrustedProvider(sourceType string) bool {
	switch sourceType {
	case "proxmox", "docker", "unifi":
		return true
	default:
		return false
	}
}

