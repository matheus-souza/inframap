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
	"strconv"
	"strings"
	"time"

	"github.com/google/uuid"
	"github.com/jackc/pgx/v5/pgtype"
	"github.com/matheussouza/inframap/internal/platform/db"
	"github.com/matheussouza/inframap/internal/platform/eventbus"
	"github.com/matheussouza/inframap/internal/platform/sdk"
	"github.com/matheussouza/inframap/modules/discovery/collectors"
	"github.com/matheussouza/inframap/modules/discovery/collectors/mdns"
	"github.com/matheussouza/inframap/modules/discovery/dto"

	"github.com/matheussouza/inframap/modules/discovery/engine"
	"github.com/matheussouza/inframap/modules/discovery/repository"
	dockerprovider "github.com/matheussouza/inframap/modules/integrations/providers/docker"
	proxmoxprovider "github.com/matheussouza/inframap/modules/integrations/providers/proxmox"
	inventoryRepo "github.com/matheussouza/inframap/modules/inventory/repository"
)

const (
	// DefaultRetentionDays is the default retention period in days for discovery collector runs.
	DefaultRetentionDays = 7

	// MaxRetentionDays defines the upper boundary for retention duration in days (100 years)
	// to prevent integer overflow when calculating time.Duration.
	MaxRetentionDays = 36500

	// RunRetentionDaysEnvVar is the environment variable key for configuring run retention.
	RunRetentionDaysEnvVar = "INFRAMAP_DISCOVERY_RUN_RETENTION_DAYS"
)

// GetRetentionDays returns the configured retention duration in days, checking INFRAMAP_DISCOVERY_RUN_RETENTION_DAYS
// and falling back to DefaultRetentionDays (7). Clamps to MaxRetentionDays.
func GetRetentionDays() int {
	if envVal := os.Getenv(RunRetentionDaysEnvVar); envVal != "" {
		if parsed, err := strconv.Atoi(envVal); err == nil && parsed > 0 {
			if parsed > MaxRetentionDays {
				return MaxRetentionDays
			}
			return parsed
		}
	}
	return DefaultRetentionDays
}

var (
	// ErrInvalidUUID indicates a malformed UUID string.
	ErrInvalidUUID = errors.New("invalid resource UUID format")

	// ErrInvalidInput indicates malformed user input.
	ErrInvalidInput = errors.New("invalid input")

	// ErrInvalidPayload indicates that the raw discovery payload is malformed.
	ErrInvalidPayload = errors.New("invalid discovery payload format")

	// ErrNoProviderForSource indicates that the source has no integration provider configured.
	ErrNoProviderForSource = errors.New("source does not have an integration provider configured")

	// ErrProviderNotFound indicates that the requested provider is not registered.
	ErrProviderNotFound = errors.New("provider not found")
)

// DiscoveryUseCase contract defines application orchestration for discovery sources and scan ingestion.
type DiscoveryUseCase interface {
	CreateSource(ctx context.Context, req *dto.CreateDiscoverySourceRequest) (*dto.DiscoverySourceResponse, error)
	UpdateSource(ctx context.Context, id string, req *dto.UpdateDiscoverySourceRequest) (*dto.DiscoverySourceResponse, error)
	GetSourceByID(ctx context.Context, id string) (*dto.DiscoverySourceResponse, error)
	ListSources(ctx context.Context) ([]*dto.DiscoverySourceResponse, error)
	TriggerRun(ctx context.Context, sourceID string) (*dto.DiscoverySourceResponse, error)
	DeleteSource(ctx context.Context, id string) error
	GetDeletionImpact(ctx context.Context, id string) (*dto.DiscoverySourceDeletionImpactResponse, error)
	SoftDeleteSource(ctx context.Context, id string) (*dto.DeleteDiscoverySourceResponse, error)
	TriggerScan(ctx context.Context, req *dto.TriggerScanRequest) (*dto.ScanResultResponse, error)
	IngestNormalizedDevice(ctx context.Context, sourceID uuid.UUID, norm *dto.NormalizedDeviceDTO) (*dto.DiscoveryRecordResponse, error)
	ListRecordsByDevice(ctx context.Context, deviceID string) ([]*dto.DiscoveryRecordResponse, error)
	PurgeCollectorRuns(ctx context.Context, retentionDays int) (int64, error)
	ListRunsBySource(ctx context.Context, sourceID string, limit, offset int) ([]*dto.CollectorRunResponse, int64, error)
	TestHealth(ctx context.Context, sourceID string, providerID string) (*dto.SourceHealthResponse, error)
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
	providers    map[string]sdk.Provider
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

	proxmox := proxmoxprovider.NewProvider()
	docker := dockerprovider.NewProvider()

	orch := engine.NewDefaultOrchestrator(eventBus)
	orch.RegisterCollector(collectors.NewICMPCollector(nil))
	orch.RegisterCollector(collectors.NewARPCollector(arpReader))
	orch.RegisterCollector(collectors.NewReverseDNSCollector(dnsResolver))
	orch.RegisterCollector(collectors.NewSNMPCollector(nil, nil))
	orch.RegisterCollector(mdns.NewMDNSCollector(nil))
	orch.RegisterCollector(collectors.NewProviderCollector(proxmox, discRepo))
	orch.RegisterCollector(collectors.NewProviderCollector(docker, discRepo))

	uc := &DefaultDiscoveryUseCase{
		discRepo:     discRepo,
		invRepo:      invRepo,
		eventBus:     eventBus,
		logger:       logger,
		matcher:      engine.NewDefaultIdentityMatcher(),
		reconciler:   engine.NewDefaultFieldReconciler(),
		orchestrator: orch,
		providers: map[string]sdk.Provider{
			"proxmox": proxmox,
			"docker":  docker,
		},
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

// UpdateSource updates an existing discovery source after secret preservation and target change validation.
func (u *DefaultDiscoveryUseCase) UpdateSource(ctx context.Context, idStr string, req *dto.UpdateDiscoverySourceRequest) (*dto.DiscoverySourceResponse, error) {
	id, err := uuid.Parse(idStr)
	if err != nil {
		return nil, ErrInvalidUUID
	}

	req.Normalize()
	if err := req.Validate(); err != nil {
		return nil, fmt.Errorf("%w: %v", ErrInvalidInput, err)
	}

	// Fetch previous configurations to verify target changes and preserve blank secrets.
	prevRawConfigs, err := u.discRepo.GetRawCollectorConfigs(ctx, id)
	if err != nil {
		return nil, err
	}

	for i := range req.Collectors {
		col := &req.Collectors[i]
		prevConfig := prevRawConfigs[col.Type]
		if prevConfig == nil {
			prevConfig = make(map[string]interface{})
		}

		var changedTargetFields []string

		// Check if any incoming target field changed relative to previous config
		for k, incomingVal := range col.Config {
			if dto.IsTargetKey(col.Type, k) {
				prevVal := prevConfig[k]
				incomingStr := strings.TrimSpace(fmt.Sprint(incomingVal))
				prevStr := ""
				if prevVal != nil {
					prevStr = strings.TrimSpace(fmt.Sprint(prevVal))
				}
				if incomingStr != prevStr {
					changedTargetFields = append(changedTargetFields, k)
				}
			}
		}

		// Also check if any target field that was in prevConfig was deleted / omitted
		for k, prevVal := range prevConfig {
			if dto.IsTargetKey(col.Type, k) {
				if _, exists := col.Config[k]; !exists {
					prevStr := strings.TrimSpace(fmt.Sprint(prevVal))
					if prevStr != "" {
						changedTargetFields = append(changedTargetFields, k)
					}
				}
			}
		}

		// Check secret fields: if target changed and secret is blank -> 422 error!
		// If target unchanged and secret is blank -> copy from previous config (secret preservation).
		for k, prevVal := range prevConfig {
			if dto.IsSecretKey(col.Type, k) {
				prevSecret := strings.TrimSpace(fmt.Sprint(prevVal))
				if prevSecret == "" {
					continue
				}

				incomingVal, exists := col.Config[k]
				incomingSecret := ""
				if exists && incomingVal != nil {
					incomingSecret = strings.TrimSpace(fmt.Sprint(incomingVal))
				}

				if incomingSecret == "" {
					if len(changedTargetFields) > 0 {
						return nil, &dto.ErrSecretRequiredOnTargetChange{Fields: changedTargetFields}
					}
					if col.Config == nil {
						col.Config = make(map[string]interface{})
					}
					col.Config[k] = prevSecret
				}
			}
		}
	}

	src, err := u.discRepo.UpdateSource(ctx, id, req)
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
	if pubErr := u.eventBus.Publish(ctx, eventbus.NewBaseEvent("discovery_source.updated", payload)); pubErr != nil {
		u.logger.Error("failed to publish discovery_source.updated event",
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

// GetDeletionImpact returns the impact summary for deleting a discovery source.
func (u *DefaultDiscoveryUseCase) GetDeletionImpact(ctx context.Context, idStr string) (*dto.DiscoverySourceDeletionImpactResponse, error) {
	id, err := uuid.Parse(idStr)
	if err != nil {
		return nil, ErrInvalidUUID
	}

	impact, err := u.discRepo.GetDeletionImpact(ctx, id)
	if err != nil {
		return nil, err
	}

	return &dto.DiscoverySourceDeletionImpactResponse{
		SourceID: idStr,
		Impact:   *impact,
	}, nil
}

// SoftDeleteSource deactivates collectors, soft deletes the discovery source, and publishes a domain event.
func (u *DefaultDiscoveryUseCase) SoftDeleteSource(ctx context.Context, idStr string) (*dto.DeleteDiscoverySourceResponse, error) {
	id, err := uuid.Parse(idStr)
	if err != nil {
		return nil, ErrInvalidUUID
	}

	impact, err := u.discRepo.SoftDeleteSource(ctx, id)
	if err != nil {
		return nil, err
	}

	if u.eventBus != nil {
		_ = u.eventBus.Publish(ctx, eventbus.NewBaseEvent("discovery_source.deleted", map[string]interface{}{
			"source_id":         id.String(),
			"collectors_halted": impact.CollectorsHalted,
			"devices_unlinked":  impact.DevicesUnlinked,
		}))
	}

	return &dto.DeleteDiscoverySourceResponse{
		DeletedID: idStr,
		Impact:    *impact,
	}, nil
}

// DeleteSource removes a discovery source and publishes a domain event.
func (u *DefaultDiscoveryUseCase) DeleteSource(ctx context.Context, idStr string) error {
	_, err := u.SoftDeleteSource(ctx, idStr)
	return err
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
			devs, total, listErr := u.invRepo.ListDevices(ctx, "", "", pageSize, offset, true)
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
		CIDR:     cidr,
		SourceID: &source.ID,
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
				DevicesFound:  int32(devicesFound), //nolint:gosec // clamped to MaxInt32 above
				DurationMs:    int32(durationMs),   //nolint:gosec // clamped to MaxInt32 above
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

	// 5. Apply the workload lifecycle hysteresis for the authoritative scopes this run covered
	if !isCancelled && scanErr == nil && scanResult != nil {
		u.applyLifecycleHysteresis(persistCtx, scanResult)
	}

	// 6. Update discovery source last_status and last_run_at
	res, updateErr := u.discRepo.UpdateSourceStatus(persistCtx, source.ID, finalStatus)
	if updateErr != nil {
		return nil, fmt.Errorf("failed to update discovery source status to %s: %w", finalStatus, updateErr)
	}

	// 7. Emit discovery_source.run_finished on eventbus
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
			devs, total, err := u.invRepo.ListDevices(ctx, "", "", pageSize, offset, true)
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
		devices, total, fetchErr := u.invRepo.ListDevices(ctx, "", "", limit, offset, true)
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

	rawBytes, _ := json.Marshal(buildDeviceMetadata(norm))

	effectiveType := source.Type
	if norm.ProtocolSource != "" {
		isAllowedProtocol := strings.EqualFold(source.Type, norm.ProtocolSource)
		if !isAllowedProtocol {
			for _, col := range source.Collectors {
				if col.Enabled && strings.EqualFold(col.CollectorType, norm.ProtocolSource) {
					isAllowedProtocol = true
					break
				}
			}
		}
		if isAllowedProtocol {
			effectiveType = norm.ProtocolSource
		}
	}

	if match.DeviceID != nil {
		targetDeviceID = *match.DeviceID
		matchedBy = match.MatchedBy

		existingDB, fetchErr := u.invRepo.GetDeviceByID(ctx, targetDeviceID, true)
		if fetchErr != nil {
			if u.logger != nil {
				u.logger.Error("matched device not found in inventory", slog.String("device_id", targetDeviceID.String()), slog.Any("error", fetchErr))
			}
			return nil, fmt.Errorf("failed to fetch matched device %s: %w", targetDeviceID, fetchErr)
		}

		if existingDB.DeletedAt.Valid {
			norm.MatchedDeviceID = &existingDB.ID
			norm.PreviouslyDeleted = true
			staged, created, stageErr := u.stageDeviceWithIdempotency(ctx, norm, &source.ID)
			if stageErr != nil {
				return nil, fmt.Errorf("failed to stage previously deleted device: %w", stageErr)
			}
			targetDeviceID = staged.ID
			if created && u.eventBus != nil {
				_ = u.eventBus.Publish(ctx, eventbus.NewBaseEvent("device.staged", map[string]interface{}{
					"staging_id":         staged.ID.String(),
					"source":             effectiveType,
					"previously_deleted": true,
					"matched_device_id":  existingDB.ID.String(),
				}))
			}
			return u.discRepo.UpsertRecord(ctx, targetDeviceID, source.ID, "previously_deleted", norm.RawPayload)
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
				// Empty for network sweeps, in which case the query keeps whatever scope the
				// device already had: a sweep must never claim a workload out of its provider.
				ProviderScope: observationProviderScope(norm),
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
				ID:            uuid.New(),
				Hostname:      norm.Hostname,
				DeviceType:    norm.DeviceType,
				Status:        "active",
				Metadata:      rawBytes,
				ProviderScope: providerScopeText(norm),
				// Written to its own column, not just metadata: the partial index
				// idx_devices_parent_provider_ref_pending is what lets a host adopt the
				// children that were discovered before it.
				ParentProviderRef: parentProviderRefText(norm),
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
			staged, created, stageErr := u.stageDeviceWithIdempotency(ctx, norm, &source.ID)
			if stageErr != nil {
				return nil, fmt.Errorf("failed to create staging device: %w", stageErr)
			}
			targetDeviceID = staged.ID
			if created && u.eventBus != nil {
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

// stageDeviceWithIdempotency creates or updates a staging device record idempotently.
// If an unapproved record with the same IP, MAC, or matched_device_id exists, it updates it;
// otherwise, it inserts a new one.
func (u *DefaultDiscoveryUseCase) stageDeviceWithIdempotency(ctx context.Context, norm *dto.NormalizedDeviceDTO, sourceID *uuid.UUID) (*db.DeviceStaging, bool, error) {
	payload := make(map[string]interface{})
	for k, v := range norm.RawPayload {
		payload[k] = v
	}
	if norm.PreviouslyDeleted {
		payload["previously_deleted"] = true
	}
	if norm.MatchedDeviceID != nil {
		payload["matched_device_id"] = norm.MatchedDeviceID.String()
	}

	metaMap, ok := payload["metadata"].(map[string]interface{})
	if !ok {
		metaMap = make(map[string]interface{})
	}
	if norm.PreviouslyDeleted {
		metaMap["previously_deleted"] = true
	}
	if norm.MatchedDeviceID != nil {
		metaMap["matched_device_id"] = norm.MatchedDeviceID.String()
	}
	payload["metadata"] = metaMap

	rawBytes, _ := json.Marshal(payload)

	var ipAddr *netip.Addr
	if norm.IPAddress != "" {
		if addr, parseErr := netip.ParseAddr(norm.IPAddress); parseErr == nil {
			ipAddr = &addr
		}
	}
	var macAddr net.HardwareAddr
	if norm.MACAddress != "" {
		if hw, parseErr := net.ParseMAC(norm.MACAddress); parseErr == nil {
			macAddr = hw
		}
	}

	var matchedIDText pgtype.Text
	if norm.MatchedDeviceID != nil {
		matchedIDText = pgtype.Text{String: norm.MatchedDeviceID.String(), Valid: true}
	}

	existingStaged, err := u.invRepo.FindPendingStagingDevice(ctx, db.FindPendingStagingDeviceParams{
		IpAddress:       ipAddr,
		MacAddress:      macAddr,
		MatchedDeviceID: matchedIDText,
	})
	if err == nil && existingStaged != nil {
		updateParams := db.UpdateStagingDeviceParams{
			ID:         existingStaged.ID,
			Hostname:   norm.Hostname,
			IpAddress:  ipAddr,
			MacAddress: macAddr,
			DeviceType: norm.DeviceType,
			RawPayload: rawBytes,
		}
		staged, updateErr := u.invRepo.UpdateStagingDevice(ctx, updateParams)
		if updateErr != nil {
			return nil, false, fmt.Errorf("failed to update staging device: %w", updateErr)
		}
		return staged, false, nil
	}

	stageParams := db.CreateStagingDeviceParams{
		ID:         uuid.New(),
		Hostname:   norm.Hostname,
		IpAddress:  ipAddr,
		MacAddress: macAddr,
		DeviceType: norm.DeviceType,
		RawPayload: rawBytes,
		Status:     "discovered",
	}
	if sourceID != nil {
		stageParams.DiscoverySourceID = pgtype.UUID{Bytes: *sourceID, Valid: true}
	}
	staged, createErr := u.invRepo.CreateStagingDevice(ctx, stageParams)
	if createErr != nil {
		return nil, false, fmt.Errorf("failed to create staging device: %w", createErr)
	}
	return staged, true, nil
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

	staged, created, err := u.stageDeviceWithIdempotency(ctx, norm, nil)
	if err != nil {
		if u.logger != nil {
			u.logger.Error("failed to persist discovered device to staging", slog.String("ip", norm.IPAddress), slog.Any("error", err))
		}
		return
	}

	if created && u.eventBus != nil {
		eventData := map[string]interface{}{
			"staging_id":  staged.ID.String(),
			"ip_address":  norm.IPAddress,
			"source_type": sourceType,
		}
		if norm.PreviouslyDeleted {
			eventData["previously_deleted"] = true
			if norm.MatchedDeviceID != nil {
				eventData["matched_device_id"] = norm.MatchedDeviceID.String()
			}
		}
		_ = u.eventBus.Publish(ctx, eventbus.NewBaseEvent("device.staged", eventData))
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

// PurgeCollectorRuns removes collector runs older than retentionDays (defaulting to GetRetentionDays if <= 0).
func (u *DefaultDiscoveryUseCase) PurgeCollectorRuns(ctx context.Context, retentionDays int) (int64, error) {
	if retentionDays <= 0 {
		retentionDays = GetRetentionDays()
	}
	if retentionDays > MaxRetentionDays {
		retentionDays = MaxRetentionDays
	}
	cutoff := time.Now().Add(-time.Duration(retentionDays) * 24 * time.Hour)
	const batchSize = 500

	totalPurged, err := u.discRepo.PurgeOldCollectorRuns(ctx, cutoff, batchSize)
	if err != nil {
		if u.logger != nil {
			u.logger.Error("failed to purge old collector runs",
				slog.Int("retention_days", retentionDays),
				slog.Time("cutoff", cutoff),
				slog.Any("error", err),
			)
		}
		return totalPurged, fmt.Errorf("failed to purge old collector runs: %w", err)
	}

	if u.logger != nil {
		u.logger.Info("purged old collector runs",
			slog.Int64("purged_count", totalPurged),
			slog.Int("retention_days", retentionDays),
			slog.Time("cutoff", cutoff),
		)
	}

	return totalPurged, nil
}

// ListRunsBySource retrieves execution history records for a discovery source with pagination.
func (u *DefaultDiscoveryUseCase) ListRunsBySource(ctx context.Context, sourceID string, limit, offset int) ([]*dto.CollectorRunResponse, int64, error) {
	id, err := uuid.Parse(sourceID)
	if err != nil {
		return nil, 0, ErrInvalidUUID
	}

	if _, err := u.discRepo.GetSourceByID(ctx, id); err != nil {
		return nil, 0, err
	}

	if limit <= 0 {
		limit = 20
	} else if limit > 100 {
		limit = 100
	}
	if offset < 0 {
		offset = 0
	}

	return u.discRepo.ListRunsBySourceIDPaged(ctx, id, limit, offset)
}

// buildDeviceMetadata merges the collector payload with the canonical provider identity.
//
// The identity has to live in devices.metadata because that is what the Matcher reads at
// Tier 0 and what the partial unique index uq_devices_provider_ref is built on
// (metadata->>'provider_ref'), which requires the canonical key as a plain string.
func buildDeviceMetadata(norm *dto.NormalizedDeviceDTO) map[string]interface{} {
	metadata := make(map[string]interface{}, len(norm.RawPayload)+2)
	for k, v := range norm.RawPayload {
		metadata[k] = v
	}

	if norm.ProviderRef != nil && !norm.ProviderRef.IsZero() {
		metadata[engine.DeviceMetadataProviderRefKey] = norm.ProviderRef.Key()
	}
	if norm.ParentProviderRef != nil && !norm.ParentProviderRef.IsZero() {
		metadata["parent_provider_ref"] = norm.ParentProviderRef.Key()
	}

	if state := reportedPowerState(metadata, norm.ProviderRef); state != "" {
		metadata[DeviceMetadataPowerStateKey] = state
	}

	return metadata
}

// DeviceMetadataPowerStateKey is the devices.metadata key holding the runtime state a
// provider reports for a workload. It is deliberately separate from devices.status, which
// records what InfraMap itself observed: a container can be a healthy, actively discovered
// device (status "active") while being powered off (power_state "stopped").
const DeviceMetadataPowerStateKey = "power_state"

// reportedPowerState hoists the runtime state out of the provider's own metadata namespace
// to a canonical top-level key, so readers do not need to know which provider produced it.
func reportedPowerState(metadata map[string]interface{}, ref *collectors.ProviderRef) string {
	if ref == nil || ref.IsZero() {
		return ""
	}
	namespace, ok := metadata[ref.Provider].(map[string]interface{})
	if !ok {
		return ""
	}
	state, _ := namespace[DeviceMetadataPowerStateKey].(string)
	return strings.TrimSpace(state)
}

// archiveAbsenceThreshold is the number of consecutive absences from complete, authoritative
// runs after which a workload is archived. The first absence only takes it offline, which
// leaves room for a single transient miss before the device leaves the active inventory.
const archiveAbsenceThreshold int32 = 2

// applyLifecycleHysteresis retires workloads that an authoritative provider stopped
// reporting.
//
// Only collectors that completed successfully are considered. A partial or failed run
// carries no information about absence — a cluster outage or an expired token makes every
// workload look gone — so state is frozen instead, which is the scope guard-rail of
// CONTEXT.md guideline #171.
func (u *DefaultDiscoveryUseCase) applyLifecycleHysteresis(ctx context.Context, scan *engine.ScanResult) {
	if u.invRepo == nil {
		return
	}

	for _, collector := range scan.Collectors {
		if collector.Status != "success" {
			continue
		}

		for _, scope := range collector.Scopes {
			observed := make(map[string]bool, len(scope.ObservedRefs))
			for _, ref := range scope.ObservedRefs {
				observed[ref] = true
			}
			// An authoritative run that reported nothing is indistinguishable from one that
			// failed to enumerate, so it must not retire the whole scope.
			if len(observed) == 0 {
				continue
			}

			u.retireAbsentWorkloads(ctx, collector.CollectorType, scope.Scope, observed)
		}
	}
}

// retireAbsentWorkloads advances every device of a scope that the provider did not report.
func (u *DefaultDiscoveryUseCase) retireAbsentWorkloads(ctx context.Context, collectorType, scope string, observed map[string]bool) {
	devices, err := u.invRepo.ListDevicesByProviderScope(ctx, scope)
	if err != nil {
		if u.logger != nil {
			u.logger.Warn("failed to list devices for lifecycle evaluation",
				slog.String("collector_type", collectorType),
				slog.String("provider_scope", scope),
				slog.Any("error", err),
			)
		}
		return
	}

	for i := range devices {
		device := &devices[i]

		ref := deviceProviderRef(device)
		// Devices without a provider identity reached this scope some other way and are not
		// this provider's to retire.
		if ref == "" || observed[ref] {
			continue
		}

		updated, absentErr := u.invRepo.MarkDeviceAbsent(ctx, device.ID, archiveAbsenceThreshold)
		if absentErr != nil {
			if u.logger != nil {
				u.logger.Warn("failed to advance device absence",
					slog.String("device_id", device.ID.String()),
					slog.Any("error", absentErr),
				)
			}
			continue
		}

		if u.eventBus != nil {
			_ = u.eventBus.Publish(ctx, eventbus.NewBaseEvent("device.lifecycle_changed", map[string]interface{}{
				"device_id":      device.ID.String(),
				"provider_ref":   ref,
				"provider_scope": scope,
				"status":         updated.Status,
				"absence_count":  updated.AbsenceCount,
			}))
		}
	}
}

// deviceProviderRef reads the canonical provider identity stored on a device, returning an
// empty string for devices that carry none.
func deviceProviderRef(device *db.Device) string {
	if len(device.Metadata) == 0 {
		return ""
	}
	var metadata map[string]interface{}
	if err := json.Unmarshal(device.Metadata, &metadata); err != nil {
		return ""
	}
	ref, _ := metadata[engine.DeviceMetadataProviderRefKey].(string)
	return strings.TrimSpace(ref)
}

// observationProviderScope returns the authoritative scope an observation belongs to, or an
// empty string when it came from a network sweep.
func observationProviderScope(norm *dto.NormalizedDeviceDTO) string {
	if norm.ProviderRef == nil || norm.ProviderRef.IsZero() {
		return ""
	}
	return norm.ProviderRef.Scope
}

// providerScopeText adapts observationProviderScope to the nullable column, so devices
// discovered by network sweeps keep provider_scope NULL rather than an empty string.
func providerScopeText(norm *dto.NormalizedDeviceDTO) pgtype.Text {
	scope := observationProviderScope(norm)
	if scope == "" {
		return pgtype.Text{}
	}
	return pgtype.Text{String: scope, Valid: true}
}

// parentProviderRefText adapts the declared parentage to the nullable column, leaving it
// NULL for entities at the top of the hierarchy and for network sweep observations.
func parentProviderRefText(norm *dto.NormalizedDeviceDTO) pgtype.Text {
	if norm.ParentProviderRef == nil || norm.ParentProviderRef.IsZero() {
		return pgtype.Text{}
	}
	return pgtype.Text{String: norm.ParentProviderRef.Key(), Valid: true}
}

// RegisterProvider registers or overrides an integration provider for health checking and collection.
func (u *DefaultDiscoveryUseCase) RegisterProvider(provider sdk.Provider) {
	if provider != nil && provider.ID() != "" {
		if u.providers == nil {
			u.providers = make(map[string]sdk.Provider)
		}
		u.providers[provider.ID()] = provider
	}
}

// SanitizeHealthError scrubs sensitive secret values and credentials from health check error messages.
func SanitizeHealthError(err error, config sdk.ProviderConfig) string {
	if err == nil {
		return ""
	}
	msg := err.Error()
	for k, v := range config {
		if strVal, ok := v.(string); ok && strVal != "" && len(strVal) >= 3 {
			if dto.IsSecretKey("", k) {
				msg = strings.ReplaceAll(msg, strVal, "[REDACTED]")
			}
		}
	}
	return msg
}

// TestHealth tests connectivity of an integration provider configured for the source using server-side credentials.
func (u *DefaultDiscoveryUseCase) TestHealth(ctx context.Context, sourceIDStr string, providerID string) (*dto.SourceHealthResponse, error) {
	sourceUUID, err := uuid.Parse(sourceIDStr)
	if err != nil {
		return nil, ErrInvalidUUID
	}

	source, err := u.discRepo.GetSourceByID(ctx, sourceUUID)
	if err != nil {
		return nil, err
	}

	if providerID == "" {
		for _, col := range source.Collectors {
			if _, ok := u.providers[col.CollectorType]; ok {
				providerID = col.CollectorType
				break
			}
		}
		if providerID == "" {
			if _, ok := u.providers[source.Type]; ok {
				providerID = source.Type
			}
		}
	}

	if providerID == "" {
		return nil, ErrNoProviderForSource
	}

	provider, ok := u.providers[providerID]
	if !ok {
		return nil, fmt.Errorf("%w: %s", ErrProviderNotFound, providerID)
	}

	resolvedConfig, err := u.discRepo.ResolveCollectorConfig(ctx, sourceUUID, providerID)
	if err != nil {
		u.logger.Error("failed to resolve collector config for health check",
			slog.String("source_id", sourceIDStr),
			slog.String("provider_id", providerID),
			slog.Any("error", err),
		)
		return &dto.SourceHealthResponse{
			ProviderID: providerID,
			Status:     "error",
			Message:    SanitizeHealthError(err, nil),
		}, nil
	}

	if err := provider.HealthCheck(ctx, resolvedConfig); err != nil {
		u.logger.Warn("provider health check failed",
			slog.String("source_id", sourceIDStr),
			slog.String("provider_id", providerID),
			slog.Any("error", err),
		)
		return &dto.SourceHealthResponse{
			ProviderID: providerID,
			Status:     "error",
			Message:    SanitizeHealthError(err, resolvedConfig),
		}, nil
	}

	return &dto.SourceHealthResponse{
		ProviderID: providerID,
		Status:     "ok",
		Message:    "Connection established",
	}, nil
}
