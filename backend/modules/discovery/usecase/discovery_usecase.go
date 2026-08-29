// Package usecase implements application business logic for the Discovery Engine.
package usecase

import (
	"context"
	"encoding/json"
	"errors"
	"fmt"
	"log/slog"
	"math"
	"net"
	"net/netip"
	"os"
	"strings"
	"time"

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
	DeleteSource(ctx context.Context, id string) error
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

	src, err := u.discRepo.CreateSource(ctx, req)
	if err != nil {
		return nil, err
	}

	payload := map[string]interface{}{
		"source_id": src.ID.String(),
		"enabled":   src.Enabled,
	}
	if src.ScheduleCron != nil {
		payload["schedule_cron"] = *src.ScheduleCron
	}
	if pubErr := u.eventBus.Publish(ctx, eventbus.NewBaseEvent("discovery_source.created", payload)); pubErr != nil {
		u.logger.Error("failed to publish discovery_source.created event",
			slog.String("source_id", src.ID.String()),
			slog.Any("error", pubErr),
		)
	}

	return src, nil
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

// DeleteSource removes a discovery source and publishes a domain event.
func (u *DefaultDiscoveryUseCase) DeleteSource(ctx context.Context, idStr string) error {
	id, err := uuid.Parse(idStr)
	if err != nil {
		return ErrInvalidUUID
	}
	if err := u.discRepo.DeleteSource(ctx, id); err != nil {
		return err
	}
	_ = u.eventBus.Publish(ctx, eventbus.NewBaseEvent("discovery_source.deleted", map[string]interface{}{
		"source_id": id.String(),
	}))
	return nil
}

// TriggerRun triggers a manual or scheduled scan sweep for a discovery source.
func (u *DefaultDiscoveryUseCase) TriggerRun(ctx context.Context, idStr string) (*dto.DiscoverySourceResponse, error) {
	id, err := uuid.Parse(idStr)
	if err != nil {
		return nil, ErrInvalidUUID
	}

	source, err := u.discRepo.GetSourceByID(ctx, id)
	if err != nil {
		return nil, err
	}

	persistCtx := context.WithoutCancel(ctx)

	if _, updateErr := u.discRepo.UpdateSourceStatus(persistCtx, source.ID, "running"); updateErr != nil {
		return nil, fmt.Errorf("failed to set discovery status to running: %w", updateErr)
	}

	cidr := source.ConfigCIDR
	if cidr == "" {
		if u.logger != nil {
			u.logger.Warn("discovery source has no CIDR configured, skipping scan", slog.String("source_id", source.ID.String()))
		}
		if _, errStatusErr := u.discRepo.UpdateSourceStatus(persistCtx, source.ID, "error"); errStatusErr != nil {
			return nil, fmt.Errorf("failed to set error status for missing CIDR: %w", errStatusErr)
		}
		return nil, fmt.Errorf("discovery source %s has no CIDR configured", source.ID)
	}

	if _, err := collectors.ParseTargetPrefix(cidr); err != nil {
		if u.logger != nil {
			u.logger.Error("discovery source has invalid CIDR configured", slog.String("source_id", source.ID.String()), slog.Any("error", err))
		}
		if _, errStatusErr := u.discRepo.UpdateSourceStatus(persistCtx, source.ID, "error"); errStatusErr != nil {
			return nil, fmt.Errorf("failed to set error status for invalid CIDR: %w", errStatusErr)
		}
		return nil, fmt.Errorf("discovery source %s has invalid CIDR %q: %w", source.ID, cidr, err)
	}

	// 1. Determine enabled collectors
	var enabledCollectorTypes []string
	for _, col := range source.Collectors {
		if col.Enabled {
			enabledCollectorTypes = append(enabledCollectorTypes, col.CollectorType)
		}
	}
	if len(enabledCollectorTypes) == 0 {
		if source.Type != "" {
			enabledCollectorTypes = []string{source.Type}
		}
	}

	// 2. Load active devices from inventory (paginated)
	var activeDevices []db.Device
	if u.invRepo != nil {
		const pageSize int32 = 1000
		var offset int32
		for {
			devs, total, listErr := u.invRepo.ListDevices(ctx, "", "", pageSize, offset, false)
			if listErr != nil {
				if errors.Is(ctx.Err(), context.Canceled) || errors.Is(ctx.Err(), context.DeadlineExceeded) {
					_, _ = u.discRepo.UpdateSourceStatus(persistCtx, source.ID, "cancelled")
					return nil, ctx.Err()
				}
				_, _ = u.discRepo.UpdateSourceStatus(persistCtx, source.ID, "error")
				return nil, fmt.Errorf("failed to load active inventory: %w", listErr)
			}
			activeDevices = append(activeDevices, devs...)
			offset += pageSize
			if int64(offset) >= total {
				break
			}
		}
	}

	target := collectors.DiscoveryTarget{
		CIDR: cidr,
	}

	startTime := time.Now()
	scanResult, scanErr := u.orchestrator.RunScan(ctx, target, activeDevices, enabledCollectorTypes)
	duration := time.Since(startTime)

	// 3. Compute overall source outcome status
	var finalStatus string
	isCancelled := errors.Is(ctx.Err(), context.Canceled) || errors.Is(ctx.Err(), context.DeadlineExceeded) ||
		errors.Is(scanErr, context.Canceled) || errors.Is(scanErr, context.DeadlineExceeded)

	if isCancelled {
		finalStatus = "cancelled"
	} else if scanErr != nil {
		finalStatus = "error"
	} else if scanResult != nil && len(scanResult.Collectors) > 0 {
		successCount := 0
		errorCount := 0
		for _, c := range scanResult.Collectors {
			if c.Status == "success" {
				successCount++
			} else {
				errorCount++
			}
		}
		if errorCount == 0 {
			finalStatus = "idle"
		} else if successCount > 0 && errorCount > 0 {
			finalStatus = "partial"
		} else {
			finalStatus = "error"
		}
	} else {
		finalStatus = "idle"
	}

	// 4. Record individual collector runs in discovery_collector_runs (Guideline #85: errors do not abort the scan)
	if scanResult != nil && len(scanResult.Collectors) > 0 {
		for _, c := range scanResult.Collectors {
			var errMsg pgtype.Text
			if c.ErrorMessage != "" {
				errMsg = pgtype.Text{String: c.ErrorMessage, Valid: true}
			}

			runStatus := c.Status
			if isCancelled && runStatus != "success" {
				runStatus = "timeout"
			}
			dbColType := normalizeToDBCollectorType(c.CollectorType)

			devicesFound := c.DevicesFound
			if devicesFound < 0 {
				devicesFound = 0
			} else if devicesFound > math.MaxInt32 {
				devicesFound = math.MaxInt32
			}

			durationMs := c.DurationMs
			if durationMs < 0 {
				durationMs = 0
			} else if durationMs > math.MaxInt32 {
				durationMs = math.MaxInt32
			}

			colRunParams := &db.CreateCollectorRunParams{
				ID:            uuid.New(),
				SourceID:      source.ID,
				CollectorType: dbColType,
				Status:        runStatus,
				DevicesFound:  int32(devicesFound),
				DurationMs:    int32(durationMs),
				ErrorMessage:  errMsg,
				StartedAt:     pgtype.Timestamptz{Time: startTime, Valid: true},
				FinishedAt:    pgtype.Timestamptz{Time: time.Now(), Valid: true},
			}

			if recErr := u.discRepo.CreateCollectorRun(persistCtx, colRunParams); recErr != nil {
				if u.logger != nil {
					u.logger.Warn("failed to record collector run",
						slog.String("source_id", source.ID.String()),
						slog.String("collector_type", c.CollectorType),
						slog.Any("error", recErr),
					)
				}
			}
		}
	}

	// 5. Update discovery source last_status and last_run_at
	res, updateErr := u.discRepo.UpdateSourceStatus(persistCtx, source.ID, finalStatus)
	if updateErr != nil {
		return nil, fmt.Errorf("failed to update discovery source status to %s: %w", finalStatus, updateErr)
	}

	// 6. Emit discovery_source.run_finished on eventbus
	if u.eventBus != nil {
		payload := map[string]interface{}{
			"source_id":   source.ID.String(),
			"status":      finalStatus,
			"duration_ms": duration.Milliseconds(),
		}
		if scanResult != nil {
			payload["total_collected"] = scanResult.TotalCollected
			payload["total_valid"] = scanResult.TotalValid
			payload["total_discovered"] = scanResult.TotalDiscovered
			payload["total_updated"] = scanResult.TotalUpdated
		}
		if pubErr := u.eventBus.Publish(persistCtx, eventbus.NewBaseEvent("discovery_source.run_finished", payload)); pubErr != nil {
			if u.logger != nil {
				u.logger.Warn("failed to publish discovery_source.run_finished event",
					slog.String("source_id", source.ID.String()),
					slog.Any("error", pubErr),
				)
			}
		}
	}

	if isCancelled {
		if scanErr != nil {
			return res, scanErr
		}
		return res, ctx.Err()
	}
	if finalStatus == "error" {
		if scanErr != nil {
			return res, scanErr
		}
		return res, fmt.Errorf("discovery scan failed for source %s: all collectors failed", source.ID)
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

	res, err := u.orchestrator.RunScan(ctx, target, activeDevices, req.Collectors)
	if err != nil {
		return nil, err
	}

	var collectorDTOs []dto.CollectorRunDetail
	if res.Collectors != nil {
		collectorDTOs = make([]dto.CollectorRunDetail, len(res.Collectors))
		for i, c := range res.Collectors {
			collectorDTOs[i] = dto.CollectorRunDetail{
				CollectorType: c.CollectorType,
				Status:        c.Status,
				DevicesFound:  c.DevicesFound,
				DurationMs:    c.DurationMs,
				ErrorMessage:  c.ErrorMessage,
			}
		}
	}

	return &dto.ScanResultResponse{
		CIDR:            res.Target.CIDR,
		TotalCollected:  res.TotalCollected,
		TotalValid:      res.TotalValid,
		TotalDiscovered: res.TotalDiscovered,
		TotalUpdated:    res.TotalUpdated,
		DurationMs:      res.Duration.Milliseconds(),
		Collectors:      collectorDTOs,
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

	effectiveType := norm.ProtocolSource
	if effectiveType == "" {
		effectiveType = source.Type
	}

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

		reconciledDB, changed := u.reconciler.Reconcile(existingDB, norm, effectiveType)
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
					"source_type": effectiveType,
				}))
			}
		}
	} else {
		matchedBy = "new_discovery"
		if isTrustedProvider(effectiveType) {
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
					"source":    effectiveType,
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
					"source":     effectiveType,
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
	if u.invRepo == nil {
		if u.logger != nil {
			u.logger.Error("cannot persist discovered device: inventory repository is not configured")
		}
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
	switch collectors.CanonicalType(sourceType) {
	case "proxmox", "docker", "unifi":
		return true
	default:
		return false
	}
}

func normalizeToDBCollectorType(t string) string {
	switch strings.ToLower(strings.TrimSpace(t)) {
	case "icmp", "icmp_sweep":
		return "icmp_sweep"
	case "arp", "arp_sweep":
		return "arp_sweep"
	case "reversedns", "reverse_dns", "reverse-dns":
		return "reverse_dns"
	case "snmp":
		return "snmp"
	case "mdns":
		return "mdns"
	case "proxmox":
		return "proxmox"
	case "docker":
		return "docker"
	case "unifi":
		return "unifi"
	default:
		return t
	}
}

