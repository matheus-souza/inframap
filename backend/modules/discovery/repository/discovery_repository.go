// Package repository provides database persistence for Discovery Engine entities.
package repository

import (
	"context"
	"encoding/json"
	"errors"
	"fmt"
	"log/slog"
	"math"
	"net/netip"
	"strings"
	"time"

	"github.com/google/uuid"
	"github.com/jackc/pgx/v5"
	"github.com/jackc/pgx/v5/pgtype"
	"github.com/matheussouza/inframap/internal/platform/crypto"
	"github.com/matheussouza/inframap/internal/platform/db"
	"github.com/matheussouza/inframap/internal/platform/sdk"
	"github.com/matheussouza/inframap/modules/discovery/dto"
)

var (
	// ErrSourceNotFound is returned when a requested discovery source does not exist.
	ErrSourceNotFound = errors.New("discovery source not found")

	// ErrRecordNotFound is returned when a requested discovery record does not exist.
	ErrRecordNotFound = errors.New("discovery record not found")
)

// TxBeginner abstracts the transaction begin operation from pgxpool.Pool.
type TxBeginner interface {
	Begin(ctx context.Context) (pgx.Tx, error)
}

// DiscoveryRepository contract defines data persistence operations for discovery sources and records.
type DiscoveryRepository interface {
	CreateSource(ctx context.Context, req *dto.CreateDiscoverySourceRequest) (*dto.DiscoverySourceResponse, error)
	GetSourceByID(ctx context.Context, id uuid.UUID) (*dto.DiscoverySourceResponse, error)
	ListSources(ctx context.Context) ([]*dto.DiscoverySourceResponse, error)
	UpdateSourceStatus(ctx context.Context, id uuid.UUID, status string) (*dto.DiscoverySourceResponse, error)
	DeleteSource(ctx context.Context, id uuid.UUID) error
	GetDeletionImpact(ctx context.Context, id uuid.UUID) (*dto.DiscoverySourceDeletionImpact, error)
	SoftDeleteSource(ctx context.Context, id uuid.UUID) (*dto.DiscoverySourceDeletionImpact, error)
	UpsertRecord(ctx context.Context, deviceID, sourceID uuid.UUID, matchedBy string, rawPayload map[string]interface{}) (*dto.DiscoveryRecordResponse, error)
	ListRecordsByDevice(ctx context.Context, deviceID uuid.UUID) ([]*dto.DiscoveryRecordResponse, error)
	CreateCollectorRun(ctx context.Context, run *db.CreateCollectorRunParams) error
	ListRunsBySourceID(ctx context.Context, sourceID uuid.UUID, limit int) ([]*dto.CollectorRunResponse, error)
	ListRunsBySourceIDPaged(ctx context.Context, sourceID uuid.UUID, limit, offset int) ([]*dto.CollectorRunResponse, int64, error)
	PurgeOldCollectorRuns(ctx context.Context, cutoff time.Time, batchSize int) (int64, error)
	UpdateSource(ctx context.Context, id uuid.UUID, req *dto.UpdateDiscoverySourceRequest) (*dto.DiscoverySourceResponse, error)
	GetRawCollectorConfigs(ctx context.Context, sourceID uuid.UUID) (map[string]map[string]interface{}, error)
	ResolveCollectorConfig(ctx context.Context, sourceID uuid.UUID, collectorType string) (sdk.ProviderConfig, error)
}

// ConfigPayload represents decrypted discovery source configuration (RFC-026 §1).
type ConfigPayload struct {
	Targets []dto.SourceTarget `json:"targets,omitempty"`

	// CIDR is the single-target form stored before targets existed. It is only read:
	// every write stores Targets and drops it.
	CIDR string `json:"cidr,omitempty"`
}

// SourceTargets returns the configured targets. A payload without targets is read as a
// one-target list made of its legacy CIDR, so existing sources need no data migration.
func (p *ConfigPayload) SourceTargets() []dto.SourceTarget {
	if p.Targets != nil {
		return p.Targets
	}
	if cidr := strings.TrimSpace(p.CIDR); cidr != "" {
		return []dto.SourceTarget{{CIDR: cidr}}
	}
	return []dto.SourceTarget{}
}

// PgDiscoveryRepository implements DiscoveryRepository backed by PostgreSQL.
type PgDiscoveryRepository struct {
	database  db.DBTX
	queries   *db.Queries
	encryptor crypto.Encryptor

	// credentials resolves a collector config that points at a stored credential instead of
	// carrying its secrets inline. Optional: when absent, a config referencing a credential
	// fails loudly rather than silently authenticating with nothing.
	credentials CredentialResolver

	// subnets names the Subnets that linked targets point at. Optional: when absent, linked
	// targets are returned without subnet_name and with subnet_deleted false.
	subnets SubnetLookup
}

// SubnetLookup fetches Subnets by id in one call, soft-deleted ones included, so a source
// response can name the Subnet behind each linked target and flag deleted ones (RFC-026 §7).
// Declared here rather than imported so the discovery module does not depend on the
// inventory module's concrete repository.
type SubnetLookup interface {
	ListSubnetsByIDs(ctx context.Context, ids []uuid.UUID) ([]db.Subnet, error)
}

// CredentialResolver looks up a stored credential and returns its decrypted payload.
// Declared here rather than imported so the discovery module does not depend on the
// credentials module's concrete repository.
type CredentialResolver interface {
	GetByID(ctx context.Context, id uuid.UUID) (*db.Credential, string, error)
}

// NewPgDiscoveryRepository constructs a PgDiscoveryRepository instance.
func NewPgDiscoveryRepository(database db.DBTX, encryptor crypto.Encryptor) *PgDiscoveryRepository {
	return &PgDiscoveryRepository{
		database:  database,
		queries:   db.New(database),
		encryptor: encryptor,
	}
}

// WithCredentialResolver enables resolving collector configs that reference a stored
// credential by id (ADR-013 section 2).
func (r *PgDiscoveryRepository) WithCredentialResolver(resolver CredentialResolver) *PgDiscoveryRepository {
	r.credentials = resolver
	return r
}

// WithSubnetLookup enables filling the Subnet name and deleted flag of linked targets in
// source responses (RFC-026 §7).
func (r *PgDiscoveryRepository) WithSubnetLookup(lookup SubnetLookup) *PgDiscoveryRepository {
	r.subnets = lookup
	return r
}

// CreateSource inserts a new discovery source and its associated collectors into PostgreSQL in a single transaction.
func (r *PgDiscoveryRepository) CreateSource(ctx context.Context, req *dto.CreateDiscoverySourceRequest) (*dto.DiscoverySourceResponse, error) {
	sourceID := uuid.New()
	enabled := true
	if req.Enabled != nil {
		enabled = *req.Enabled
	}

	var cronStr pgtype.Text
	if req.ScheduleCron != "" {
		cronStr = pgtype.Text{String: req.ScheduleCron, Valid: true}
	}

	var encryptedConfig pgtype.Text
	if len(req.Config) > 0 {
		configBytes, err := json.Marshal(req.Config)
		if err != nil {
			return nil, fmt.Errorf("failed to marshal discovery config: %w", err)
		}
		if r.encryptor == nil {
			return nil, fmt.Errorf("discovery config encryption is required but no encryptor is configured")
		}
		encStr, err := r.encryptor.Encrypt(configBytes)
		if err != nil {
			return nil, fmt.Errorf("failed to encrypt discovery config: %w", err)
		}
		encryptedConfig = pgtype.Text{String: encStr, Valid: true}
	}

	beginner, ok := r.database.(TxBeginner)
	if !ok {
		return nil, fmt.Errorf("database driver must implement TxBeginner for atomic multi-collector source creation")
	}

	tx, err := beginner.Begin(ctx)
	if err != nil {
		return nil, fmt.Errorf("failed to begin transaction: %w", err)
	}
	defer func() { _ = tx.Rollback(ctx) }()
	qtx := r.queries.WithTx(tx)

	row, err := qtx.CreateDiscoverySource(ctx, db.CreateDiscoverySourceParams{
		ID:              sourceID,
		Name:            req.Name,
		Type:            req.Type,
		Enabled:         enabled,
		ScheduleCron:    cronStr,
		ConfigEncrypted: encryptedConfig,
		LastStatus:      "idle",
	})
	if err != nil {
		return nil, fmt.Errorf("failed to create discovery source: %w", err)
	}

	collectorResponses := make([]dto.CollectorResponse, 0, len(req.Collectors))
	for _, col := range req.Collectors {
		colID := uuid.New()
		colEnabled := true
		if col.Enabled != nil {
			colEnabled = *col.Enabled
		}

		var encColConfig pgtype.Text
		if len(col.Config) > 0 {
			colBytes, err := json.Marshal(col.Config)
			if err != nil {
				return nil, fmt.Errorf("failed to marshal collector config for %s: %w", col.Type, err)
			}
			if r.encryptor == nil {
				return nil, fmt.Errorf("collector config encryption is required but no encryptor is configured")
			}
			encStr, err := r.encryptor.Encrypt(colBytes)
			if err != nil {
				return nil, fmt.Errorf("failed to encrypt collector config for %s: %w", col.Type, err)
			}
			encColConfig = pgtype.Text{String: encStr, Valid: true}
		}

		colRow, err := qtx.CreateDiscoverySourceCollector(ctx, db.CreateDiscoverySourceCollectorParams{
			ID:              colID,
			SourceID:        sourceID,
			CollectorType:   col.Type,
			ConfigEncrypted: encColConfig,
			Enabled:         colEnabled,
			CreatedAt:       pgtype.Timestamptz{Time: time.Now(), Valid: true},
		})
		if err != nil {
			return nil, fmt.Errorf("failed to create discovery source collector %s: %w", col.Type, err)
		}

		collectorResponses = append(collectorResponses, dto.CollectorResponse{
			ID:            colRow.ID,
			CollectorType: colRow.CollectorType,
			Enabled:       colRow.Enabled,
		})
	}

	if tx != nil {
		if err := tx.Commit(ctx); err != nil {
			return nil, fmt.Errorf("failed to commit discovery source transaction: %w", err)
		}
	}

	resp, err := r.mapSourceToDTO(&row)
	if err != nil {
		return nil, err
	}
	resp.Collectors = collectorResponses
	r.attachTargetSubnets(ctx, resp)
	return resp, nil
}

// GetSourceByID retrieves a single discovery source by ID with its attached collectors.
func (r *PgDiscoveryRepository) GetSourceByID(ctx context.Context, id uuid.UUID) (*dto.DiscoverySourceResponse, error) {
	row, err := r.queries.GetDiscoverySourceByID(ctx, id)
	if err != nil {
		if errors.Is(err, pgx.ErrNoRows) {
			return nil, fmt.Errorf("%w: %v", ErrSourceNotFound, id)
		}
		return nil, fmt.Errorf("failed to query discovery source: %w", err)
	}

	resp, err := r.mapSourceToDTO(&row)
	if err != nil {
		return nil, err
	}

	collectors, err := r.queries.ListCollectorsBySourceID(ctx, id)
	if err != nil {
		return nil, fmt.Errorf("failed to list collectors for discovery source: %w", err)
	}

	resp.Collectors = make([]dto.CollectorResponse, len(collectors))
	for i, c := range collectors {
		var sanitizedConfig map[string]interface{}
		var configuredSecrets []string

		if c.ConfigEncrypted.Valid && c.ConfigEncrypted.String != "" && r.encryptor != nil {
			dec, decErr := r.encryptor.Decrypt(c.ConfigEncrypted.String)
			if decErr == nil && len(dec) > 0 {
				var rawMap map[string]interface{}
				if json.Unmarshal(dec, &rawMap) == nil {
					sanitizedConfig = make(map[string]interface{})
					for k, v := range rawMap {
						if dto.IsSecretKey(c.CollectorType, k) {
							if v != nil && strings.TrimSpace(fmt.Sprint(v)) != "" {
								configuredSecrets = append(configuredSecrets, k)
							}
						} else {
							sanitizedConfig[k] = v
						}
					}
				}
			}
		}

		resp.Collectors[i] = dto.CollectorResponse{
			ID:                c.ID,
			CollectorType:     c.CollectorType,
			Enabled:           c.Enabled,
			Config:            sanitizedConfig,
			ConfiguredSecrets: configuredSecrets,
		}
	}

	r.attachLastRun(ctx, resp)
	r.attachTargetSubnets(ctx, resp)

	return resp, nil
}

// ListSources retrieves all discovery sources ordered by creation date descending with their
// collectors and the summary of their latest run.
func (r *PgDiscoveryRepository) ListSources(ctx context.Context) ([]*dto.DiscoverySourceResponse, error) {
	rows, err := r.queries.ListDiscoverySources(ctx)
	if err != nil {
		return nil, fmt.Errorf("failed to list discovery sources: %w", err)
	}

	allCollectors, err := r.queries.ListAllDiscoverySourceCollectors(ctx)
	if err != nil {
		return nil, fmt.Errorf("failed to list all discovery source collectors: %w", err)
	}

	collectorsBySource := make(map[uuid.UUID][]dto.CollectorResponse)
	for _, c := range allCollectors {
		collectorsBySource[c.SourceID] = append(collectorsBySource[c.SourceID], dto.CollectorResponse{
			ID:            c.ID,
			CollectorType: c.CollectorType,
			Enabled:       c.Enabled,
		})
	}

	items := make([]*dto.DiscoverySourceResponse, len(rows))
	for i, row := range rows {
		mapped, mapErr := r.mapSourceToDTO(&row)
		if mapErr != nil {
			return nil, mapErr
		}
		cols := collectorsBySource[row.ID]
		if cols == nil {
			cols = make([]dto.CollectorResponse, 0)
		}
		mapped.Collectors = cols
		items[i] = mapped
	}
	r.attachLastRun(ctx, items...)
	r.attachTargetSubnets(ctx, items...)
	return items, nil
}

// ListSourcesLinkedToSubnet returns the active discovery sources holding at least one target
// linked to subnetID, in the order of ListSources (RFC-026 §6). Targets live in the encrypted
// source configuration, so the filter runs on the decrypted payloads rather than in SQL.
// A source whose configuration cannot be decrypted is skipped with a warning, so it does not
// block reporting, or deleting, an unrelated subnet.
func (r *PgDiscoveryRepository) ListSourcesLinkedToSubnet(ctx context.Context, subnetID uuid.UUID) ([]dto.SourceRef, error) {
	rows, err := r.queries.ListDiscoverySources(ctx)
	if err != nil {
		return nil, fmt.Errorf("failed to list discovery sources: %w", err)
	}

	refs := make([]dto.SourceRef, 0)
	for i := range rows {
		targets, decodeErr := r.decodeSourceTargets(&rows[i])
		if decodeErr != nil {
			slog.Warn("skipping discovery source with an unreadable config while listing the sources linked to a subnet",
				slog.String("source_id", rows[i].ID.String()),
				slog.Any("error", decodeErr),
			)
			continue
		}
		for _, target := range targets {
			if target.SubnetID != nil && *target.SubnetID == subnetID {
				refs = append(refs, dto.SourceRef{ID: rows[i].ID, Name: rows[i].Name})
				break
			}
		}
	}
	return refs, nil
}

// attachTargetSubnets fills the Subnet name and deleted flag of every linked target across
// sources with one lookup, and shows an active Subnet's current CIDR in place of the stored
// snapshot, which is only refreshed when the source is saved: it is the CIDR the next run
// scans (RFC-026 §1). A Subnet the lookup does not return is reported as deleted; a failed
// lookup only leaves the details empty, like attachLastRun.
func (r *PgDiscoveryRepository) attachTargetSubnets(ctx context.Context, sources ...*dto.DiscoverySourceResponse) {
	if r.subnets == nil {
		return
	}

	var ids []uuid.UUID
	seen := make(map[uuid.UUID]struct{})
	for _, src := range sources {
		if src == nil {
			continue
		}
		for _, target := range src.Targets {
			if target.SubnetID == nil {
				continue
			}
			if _, dup := seen[*target.SubnetID]; dup {
				continue
			}
			seen[*target.SubnetID] = struct{}{}
			ids = append(ids, *target.SubnetID)
		}
	}
	if len(ids) == 0 {
		return
	}

	subnets, err := r.subnets.ListSubnetsByIDs(ctx, ids)
	if err != nil {
		slog.Warn("failed to look up the subnets linked to discovery source targets", slog.Any("error", err))
		return
	}
	byID := make(map[uuid.UUID]db.Subnet, len(subnets))
	for _, subnet := range subnets {
		byID[subnet.ID] = subnet
	}

	for _, src := range sources {
		if src == nil {
			continue
		}
		for i := range src.Targets {
			target := &src.Targets[i]
			if target.SubnetID == nil {
				continue
			}
			subnet, found := byID[*target.SubnetID]
			if !found {
				target.SubnetDeleted = true
				continue
			}
			name := subnet.Name
			target.SubnetName = &name
			target.SubnetDeleted = subnet.DeletedAt.Valid
			if !target.SubnetDeleted && subnet.Cidr.IsValid() {
				target.CIDR = subnet.Cidr.String()
			}
		}
		if len(src.Targets) > 0 {
			src.ConfigCIDR = src.Targets[0].CIDR
		}
	}
}

// UpdateSourceStatus updates the execution status and timestamp of a discovery source.
func (r *PgDiscoveryRepository) UpdateSourceStatus(ctx context.Context, id uuid.UUID, status string) (*dto.DiscoverySourceResponse, error) {
	row, err := r.queries.UpdateDiscoverySourceStatus(ctx, db.UpdateDiscoverySourceStatusParams{
		ID:         id,
		LastStatus: status,
	})
	if err != nil {
		if errors.Is(err, pgx.ErrNoRows) {
			return nil, fmt.Errorf("%w: %v", ErrSourceNotFound, id)
		}
		return nil, fmt.Errorf("failed to update discovery source status: %w", err)
	}

	resp, err := r.mapSourceToDTO(&row)
	if err != nil {
		return nil, err
	}

	collectors, err := r.queries.ListCollectorsBySourceID(ctx, id)
	if err != nil {
		return nil, fmt.Errorf("failed to list collectors for discovery source: %w", err)
	}

	resp.Collectors = make([]dto.CollectorResponse, len(collectors))
	for i, c := range collectors {
		resp.Collectors[i] = dto.CollectorResponse{
			ID:            c.ID,
			CollectorType: c.CollectorType,
			Enabled:       c.Enabled,
		}
	}

	r.attachLastRun(ctx, resp)
	r.attachTargetSubnets(ctx, resp)

	return resp, nil
}

// GetDeletionImpact returns the impact of deleting a discovery source.
func (r *PgDiscoveryRepository) GetDeletionImpact(ctx context.Context, id uuid.UUID) (*dto.DiscoverySourceDeletionImpact, error) {
	_, err := r.queries.GetDiscoverySourceByID(ctx, id)
	if err != nil {
		if errors.Is(err, pgx.ErrNoRows) {
			return nil, fmt.Errorf("%w: %v", ErrSourceNotFound, id)
		}
		return nil, fmt.Errorf("failed to get discovery source: %w", err)
	}

	collectorsCount, err := r.queries.CountCollectorsBySourceID(ctx, id)
	if err != nil {
		return nil, fmt.Errorf("failed to count collectors: %w", err)
	}

	devicesCount, err := r.queries.CountDiscoveredDevicesBySourceID(ctx, id)
	if err != nil {
		return nil, fmt.Errorf("failed to count discovered devices: %w", err)
	}

	return &dto.DiscoverySourceDeletionImpact{
		CollectorsHalted: int(collectorsCount),
		DevicesUnlinked:  int(devicesCount),
	}, nil
}

// SoftDeleteSource marks a discovery source as deleted and deactivates its collectors in a single atomic transaction.
func (r *PgDiscoveryRepository) SoftDeleteSource(ctx context.Context, id uuid.UUID) (*dto.DiscoverySourceDeletionImpact, error) {
	beginner, ok := r.database.(TxBeginner)
	if !ok {
		return nil, fmt.Errorf("database driver must implement TxBeginner for atomic deletion")
	}

	tx, err := beginner.Begin(ctx)
	if err != nil {
		return nil, fmt.Errorf("failed to begin transaction: %w", err)
	}
	defer func() { _ = tx.Rollback(ctx) }()
	qtx := r.queries.WithTx(tx)

	_, err = qtx.GetDiscoverySourceByID(ctx, id)
	if err != nil {
		if errors.Is(err, pgx.ErrNoRows) {
			return nil, fmt.Errorf("%w: %v", ErrSourceNotFound, id)
		}
		return nil, fmt.Errorf("failed to get discovery source: %w", err)
	}

	collectorsCount, err := qtx.CountCollectorsBySourceID(ctx, id)
	if err != nil {
		return nil, fmt.Errorf("failed to count collectors: %w", err)
	}

	devicesCount, err := qtx.CountDiscoveredDevicesBySourceID(ctx, id)
	if err != nil {
		return nil, fmt.Errorf("failed to count discovered devices: %w", err)
	}

	if _, err := qtx.DeactivateCollectorsBySourceID(ctx, id); err != nil {
		return nil, fmt.Errorf("failed to deactivate collectors: %w", err)
	}

	rows, err := qtx.DeleteDiscoverySource(ctx, id)
	if err != nil {
		return nil, fmt.Errorf("failed to delete discovery source: %w", err)
	}
	if rows == 0 {
		return nil, fmt.Errorf("%w: %v", ErrSourceNotFound, id)
	}

	if err := tx.Commit(ctx); err != nil {
		return nil, fmt.Errorf("failed to commit transaction: %w", err)
	}

	return &dto.DiscoverySourceDeletionImpact{
		CollectorsHalted: int(collectorsCount),
		DevicesUnlinked:  int(devicesCount),
	}, nil
}

// DeleteSource removes a discovery source record.
func (r *PgDiscoveryRepository) DeleteSource(ctx context.Context, id uuid.UUID) error {
	_, err := r.SoftDeleteSource(ctx, id)
	return err
}

// UpsertRecord creates or updates a device discovery observation record.
func (r *PgDiscoveryRepository) UpsertRecord(ctx context.Context, deviceID, sourceID uuid.UUID, matchedBy string, rawPayload map[string]interface{}) (*dto.DiscoveryRecordResponse, error) {
	recordID := uuid.New()
	payloadBytes, err := json.Marshal(rawPayload)
	if err != nil {
		return nil, fmt.Errorf("failed to marshal raw payload: %w", err)
	}

	row, err := r.queries.UpsertDeviceDiscoveryRecord(ctx, db.UpsertDeviceDiscoveryRecordParams{
		ID:                recordID,
		DeviceID:          deviceID,
		DiscoverySourceID: sourceID,
		MatchedBy:         matchedBy,
		RawPayload:        payloadBytes,
	})
	if err != nil {
		return nil, fmt.Errorf("failed to upsert discovery record: %w", err)
	}

	return mapRecordToDTO(&row), nil
}

// ListRecordsByDevice retrieves all discovery observation records for a target device.
func (r *PgDiscoveryRepository) ListRecordsByDevice(ctx context.Context, deviceID uuid.UUID) ([]*dto.DiscoveryRecordResponse, error) {
	rows, err := r.queries.ListDiscoveryRecordsByDevice(ctx, deviceID)
	if err != nil {
		return nil, fmt.Errorf("failed to list discovery records: %w", err)
	}

	items := make([]*dto.DiscoveryRecordResponse, len(rows))
	for i, row := range rows {
		items[i] = mapRecordToDTO(&row)
	}
	return items, nil
}

func (r *PgDiscoveryRepository) mapSourceToDTO(row *db.DiscoverySource) (*dto.DiscoverySourceResponse, error) {
	resp := &dto.DiscoverySourceResponse{
		ID:         row.ID,
		Name:       row.Name,
		Type:       row.Type,
		Enabled:    row.Enabled,
		Targets:    make([]dto.SourceTargetResponse, 0),
		Collectors: make([]dto.CollectorResponse, 0),
		LastStatus: row.LastStatus,
		CreatedAt:  row.CreatedAt.Time,
		UpdatedAt:  row.UpdatedAt.Time,
	}
	if row.ScheduleCron.Valid {
		resp.ScheduleCron = &row.ScheduleCron.String
	}
	if row.LastRunAt.Valid {
		resp.LastRunAt = &row.LastRunAt.Time
	}
	targets, err := r.decodeSourceTargets(row)
	if err != nil {
		return nil, err
	}
	if targets != nil {
		resp.Targets = dto.NewSourceTargetResponses(targets)
		if len(resp.Targets) > 0 {
			resp.ConfigCIDR = resp.Targets[0].CIDR
		}
	}
	return resp, nil
}

// decodeSourceTargets decrypts a source configuration and returns its targets. It returns nil
// when the source has no stored configuration.
func (r *PgDiscoveryRepository) decodeSourceTargets(row *db.DiscoverySource) ([]dto.SourceTarget, error) {
	if !row.ConfigEncrypted.Valid {
		return nil, nil
	}
	if r.encryptor == nil {
		return nil, fmt.Errorf("cannot decrypt source config for %s: encryptor is not configured", row.ID)
	}
	decrypted, err := r.encryptor.Decrypt(row.ConfigEncrypted.String)
	if err != nil {
		return nil, fmt.Errorf("failed to decrypt source config for %s: %w", row.ID, err)
	}
	var cfg ConfigPayload
	if err := json.Unmarshal(decrypted, &cfg); err != nil {
		return nil, fmt.Errorf("failed to parse source config for %s: %w", row.ID, err)
	}
	return cfg.SourceTargets(), nil
}

func mapRecordToDTO(row *db.DeviceDiscoveryRecord) *dto.DiscoveryRecordResponse {
	var payload map[string]interface{}
	_ = json.Unmarshal(row.RawPayload, &payload)
	if payload == nil {
		payload = make(map[string]interface{})
	}

	return &dto.DiscoveryRecordResponse{
		ID:                row.ID,
		DeviceID:          row.DeviceID,
		DiscoverySourceID: row.DiscoverySourceID,
		MatchedBy:         row.MatchedBy,
		RawPayload:        payload,
		LastScannedAt:     row.LastScannedAt.Time,
	}
}

// CreateCollectorRun records an execution record for an individual collector.
func (r *PgDiscoveryRepository) CreateCollectorRun(ctx context.Context, run *db.CreateCollectorRunParams) error {
	if run == nil {
		return errors.New("collector run params cannot be nil")
	}
	if run.ID == uuid.Nil {
		run.ID = uuid.New()
	}
	_, err := r.queries.CreateCollectorRun(ctx, *run)
	if err != nil {
		return fmt.Errorf("failed to create collector run: %w", err)
	}
	return nil
}

func mapCollectorRunToResponse(row db.DiscoveryCollectorRun) *dto.CollectorRunResponse {
	var errMsg string
	if row.ErrorMessage.Valid {
		errMsg = row.ErrorMessage.String
	}
	return &dto.CollectorRunResponse{
		ID:            row.ID,
		SourceID:      row.SourceID,
		CollectorType: row.CollectorType,
		Status:        row.Status,
		DevicesFound:  int(row.DevicesFound),
		DurationMs:    int64(row.DurationMs),
		ErrorMessage:  errMsg,
		StartedAt:     row.StartedAt.Time,
		FinishedAt:    row.FinishedAt.Time,
		TargetCIDR:    targetCIDRString(row.TargetCidr),
	}
}

// targetCIDRString renders a nullable run target as a JSON-friendly pointer, nil when unset.
func targetCIDRString(p *netip.Prefix) *string {
	if p == nil {
		return nil
	}
	s := p.String()
	return &s
}

// ListRunsBySourceID retrieves the latest collector execution records for a source.
func (r *PgDiscoveryRepository) ListRunsBySourceID(ctx context.Context, sourceID uuid.UUID, limit int) ([]*dto.CollectorRunResponse, error) {
	if limit <= 0 {
		limit = 50
	} else if limit > 1000 {
		limit = 1000
	}
	rows, err := r.queries.ListCollectorRunsBySource(ctx, db.ListCollectorRunsBySourceParams{
		SourceID: sourceID,
		Limit:    int32(limit), //nolint:gosec // clamped to [1,1000] above
		Offset:   0,
	})
	if err != nil {
		return nil, fmt.Errorf("failed to list collector runs for source %s: %w", sourceID, err)
	}

	items := make([]*dto.CollectorRunResponse, len(rows))
	for i, row := range rows {
		items[i] = mapCollectorRunToResponse(row)
	}
	return items, nil
}

// buildLastRunSummary aggregates the rows of one run into its summary. The rows must all
// belong to the same run: every row counts, whatever its collector or target, so a run with a
// row per (collector, target) is summarized whole (RFC-026 §4).
func buildLastRunSummary(runs []db.DiscoveryCollectorRun) *dto.CollectorRunSummary {
	if len(runs) == 0 {
		return nil
	}

	summary := &dto.CollectorRunSummary{
		StartedAt:  runs[0].StartedAt.Time,
		FinishedAt: runs[0].FinishedAt.Time,
		Collectors: make([]dto.CollectorRunDetail, 0, len(runs)),
	}

	successCount := 0
	errorCount := 0
	for _, r := range runs {
		var errMsg string
		if r.ErrorMessage.Valid {
			errMsg = r.ErrorMessage.String
		}
		if r.Status == "success" {
			successCount++
		} else {
			errorCount++
		}
		summary.DevicesFound += int(r.DevicesFound)
		if int64(r.DurationMs) > summary.DurationMs {
			summary.DurationMs = int64(r.DurationMs)
		}
		if r.StartedAt.Time.Before(summary.StartedAt) {
			summary.StartedAt = r.StartedAt.Time
		}
		if r.FinishedAt.Time.After(summary.FinishedAt) {
			summary.FinishedAt = r.FinishedAt.Time
		}
		summary.Collectors = append(summary.Collectors, dto.CollectorRunDetail{
			CollectorType: r.CollectorType,
			Status:        r.Status,
			DevicesFound:  int(r.DevicesFound),
			DurationMs:    int64(r.DurationMs),
			ErrorMessage:  errMsg,
			TargetCIDR:    targetCIDRString(r.TargetCidr),
		})
	}

	if errorCount == 0 {
		summary.Status = "success"
	} else if successCount > 0 && errorCount > 0 {
		summary.Status = "partial"
	} else {
		summary.Status = "error"
	}

	return summary
}

// attachLastRun fills LastRun on every given source with the summary of its latest run, read
// with one query for all of them: the rows sharing the source's newest started_at, which
// TriggerRun writes once for every row of a run. A failed query is logged and only leaves
// LastRun empty, so a source listing never fails on its run history (guideline #158).
func (r *PgDiscoveryRepository) attachLastRun(ctx context.Context, sources ...*dto.DiscoverySourceResponse) {
	ids := make([]uuid.UUID, 0, len(sources))
	for _, src := range sources {
		if src != nil {
			ids = append(ids, src.ID)
		}
	}
	if len(ids) == 0 {
		return
	}

	rows, err := r.queries.ListLatestCollectorRunsBySources(ctx, ids)
	if err != nil {
		slog.Warn("failed to list the latest collector runs of discovery sources",
			slog.Int("sources", len(ids)),
			slog.Any("error", err),
		)
		return
	}

	runsBySource := make(map[uuid.UUID][]db.DiscoveryCollectorRun, len(ids))
	for _, row := range rows {
		runsBySource[row.SourceID] = append(runsBySource[row.SourceID], row)
	}
	for _, src := range sources {
		if src == nil {
			continue
		}
		if runs := runsBySource[src.ID]; len(runs) > 0 {
			src.LastRun = buildLastRunSummary(runs)
		}
	}
}

// ListRunsBySourceIDPaged retrieves collector execution records with pagination.
// Returns an empty slice (never nil) if no records are found.
func (r *PgDiscoveryRepository) ListRunsBySourceIDPaged(ctx context.Context, sourceID uuid.UUID, limit, offset int) ([]*dto.CollectorRunResponse, int64, error) {
	var safeLimit int32 = 20
	if limit > 0 && limit <= 1000 {
		safeLimit = int32(limit) //nolint:gosec // bounds checked in condition
	}
	var safeOffset int32
	if offset > 0 && offset <= 1000000 {
		safeOffset = int32(offset) //nolint:gosec // bounds checked in condition
	}

	total, err := r.queries.CountCollectorRunsBySource(ctx, sourceID)
	if err != nil {
		return nil, 0, fmt.Errorf("failed to count collector runs for source %s: %w", sourceID, err)
	}

	rows, err := r.queries.ListCollectorRunsBySource(ctx, db.ListCollectorRunsBySourceParams{
		SourceID: sourceID,
		Limit:    safeLimit,
		Offset:   safeOffset,
	})
	if err != nil {
		return nil, 0, fmt.Errorf("failed to list paged collector runs for source %s: %w", sourceID, err)
	}

	items := make([]*dto.CollectorRunResponse, len(rows))
	for i, row := range rows {
		items[i] = mapCollectorRunToResponse(row)
	}
	return items, total, nil
}

// PurgeOldCollectorRuns deletes discovery collector runs finished before cutoff in chunks of batchSize
// until no rows remain, respecting context cancellation. Returns the total count of purged rows.
func (r *PgDiscoveryRepository) PurgeOldCollectorRuns(ctx context.Context, cutoff time.Time, batchSize int) (int64, error) {
	if batchSize <= 0 {
		batchSize = 500
	} else if batchSize > math.MaxInt32 {
		batchSize = math.MaxInt32
	}

	cutoffTs := pgtype.Timestamptz{Time: cutoff, Valid: true}
	var totalDeleted int64

	for {
		if err := ctx.Err(); err != nil {
			return totalDeleted, err
		}

		deleted, err := r.queries.PurgeOldCollectorRunsChunk(ctx, db.PurgeOldCollectorRunsChunkParams{
			FinishedAt: cutoffTs,
			Limit:      int32(batchSize), //nolint:gosec // clamped to [1, math.MaxInt32] above
		})
		if err != nil {
			return totalDeleted, fmt.Errorf("failed to purge old collector runs chunk: %w", err)
		}

		totalDeleted += deleted
		if deleted == 0 || deleted < int64(batchSize) {
			break
		}
	}

	return totalDeleted, nil
}

// ResolveCollectorConfig retrieves and decrypts the provider configuration for a specific source collector.
// Returns an empty sdk.ProviderConfig and nil error if no collector or configuration is found.
func (r *PgDiscoveryRepository) ResolveCollectorConfig(ctx context.Context, sourceID uuid.UUID, collectorType string) (sdk.ProviderConfig, error) {
	row, err := r.queries.GetDiscoverySourceCollectorBySourceAndType(ctx, db.GetDiscoverySourceCollectorBySourceAndTypeParams{
		SourceID:      sourceID,
		CollectorType: collectorType,
	})
	if err != nil {
		if errors.Is(err, pgx.ErrNoRows) {
			return sdk.ProviderConfig{}, nil
		}
		return nil, fmt.Errorf("failed to query discovery source collector config: %w", err)
	}

	if !row.ConfigEncrypted.Valid || row.ConfigEncrypted.String == "" {
		return sdk.ProviderConfig{}, nil
	}

	if r.encryptor == nil {
		return nil, fmt.Errorf("cannot decrypt collector config for %s: encryptor is not configured", collectorType)
	}

	decrypted, err := r.encryptor.Decrypt(row.ConfigEncrypted.String)
	if err != nil {
		return nil, fmt.Errorf("failed to decrypt collector config for %s: %w", collectorType, err)
	}

	if len(decrypted) == 0 {
		return sdk.ProviderConfig{}, nil
	}

	var config sdk.ProviderConfig
	if err := json.Unmarshal(decrypted, &config); err != nil {
		return nil, fmt.Errorf("failed to parse collector config for %s: %w", collectorType, err)
	}

	if config == nil {
		config = sdk.ProviderConfig{}
	}

	return r.resolveCredentialReference(ctx, config, collectorType)
}

// CredentialConfigKey is the collector config key that points at a stored credential
// instead of carrying the provider secrets inline.
const CredentialConfigKey = "credential_id"

// resolveCredentialReference merges a referenced credential's secrets into the config.
//
// Values written directly on the collector win over the credential's, so an operator can
// point at a shared credential and still override, say, the api_url for one plan. A
// reference that cannot be resolved is an error rather than a silent fallback: continuing
// would attempt the connection with no credentials at all and report it as an
// authentication failure, sending the operator to look in the wrong place.
func (r *PgDiscoveryRepository) resolveCredentialReference(
	ctx context.Context,
	config sdk.ProviderConfig,
	collectorType string,
) (sdk.ProviderConfig, error) {
	rawID, present := config[CredentialConfigKey]
	if !present {
		return config, nil
	}

	idStr := strings.TrimSpace(fmt.Sprintf("%v", rawID))
	if idStr == "" {
		delete(config, CredentialConfigKey)
		return config, nil
	}

	if r.credentials == nil {
		return nil, fmt.Errorf("collector %s references a credential but no credential resolver is configured", collectorType)
	}

	credentialID, err := uuid.Parse(idStr)
	if err != nil {
		return nil, fmt.Errorf("collector %s has a malformed credential reference %q: %w", collectorType, idStr, err)
	}

	_, secret, err := r.credentials.GetByID(ctx, credentialID)
	if err != nil {
		return nil, fmt.Errorf("failed to resolve credential %s for collector %s: %w", credentialID, collectorType, err)
	}

	var secrets map[string]interface{}
	if err := json.Unmarshal([]byte(secret), &secrets); err != nil {
		return nil, fmt.Errorf("credential %s does not hold a JSON object of provider settings: %w", credentialID, err)
	}
	// A literal "null" payload unmarshals into a nil map without error, which would merge
	// nothing and let the run proceed with no credentials at all — the silent failure this
	// resolution exists to prevent.
	if secrets == nil {
		return nil, fmt.Errorf("credential %s holds no provider settings", credentialID)
	}

	for key, value := range secrets {
		if _, overridden := config[key]; !overridden {
			config[key] = value
		}
	}
	// The reference itself is not a provider setting and must not reach the provider.
	delete(config, CredentialConfigKey)

	return config, nil
}

// GetRawCollectorConfigs returns decrypted, un-sanitized configurations for all collectors belonging to a source.
func (r *PgDiscoveryRepository) GetRawCollectorConfigs(ctx context.Context, sourceID uuid.UUID) (map[string]map[string]interface{}, error) {
	collectors, err := r.queries.ListCollectorsBySourceID(ctx, sourceID)
	if err != nil {
		return nil, fmt.Errorf("failed to list collectors for discovery source: %w", err)
	}

	result := make(map[string]map[string]interface{})
	for _, c := range collectors {
		if !c.ConfigEncrypted.Valid || c.ConfigEncrypted.String == "" {
			result[c.CollectorType] = make(map[string]interface{})
			continue
		}
		if r.encryptor == nil {
			return nil, fmt.Errorf("cannot decrypt collector config for %s: encryptor is not configured", c.CollectorType)
		}
		decrypted, err := r.encryptor.Decrypt(c.ConfigEncrypted.String)
		if err != nil {
			return nil, fmt.Errorf("failed to decrypt collector config for %s: %w", c.CollectorType, err)
		}
		var cfg map[string]interface{}
		if err := json.Unmarshal(decrypted, &cfg); err != nil {
			return nil, fmt.Errorf("failed to parse collector config for %s: %w", c.CollectorType, err)
		}
		result[c.CollectorType] = cfg
	}
	return result, nil
}

// UpdateSource updates a discovery source and atomically replaces its collectors in a single transaction.
func (r *PgDiscoveryRepository) UpdateSource(ctx context.Context, id uuid.UUID, req *dto.UpdateDiscoverySourceRequest) (*dto.DiscoverySourceResponse, error) {
	existing, err := r.queries.GetDiscoverySourceByID(ctx, id)
	if err != nil {
		if errors.Is(err, pgx.ErrNoRows) {
			return nil, fmt.Errorf("%w: %v", ErrSourceNotFound, id)
		}
		return nil, fmt.Errorf("failed to query discovery source: %w", err)
	}

	enabled := true
	if req.Enabled != nil {
		enabled = *req.Enabled
	}

	var cronStr pgtype.Text
	if req.ScheduleCron != "" {
		cronStr = pgtype.Text{String: req.ScheduleCron, Valid: true}
	}

	encryptedConfig := existing.ConfigEncrypted
	if len(req.Config) > 0 {
		configBytes, err := json.Marshal(req.Config)
		if err != nil {
			return nil, fmt.Errorf("failed to marshal discovery config: %w", err)
		}
		if r.encryptor == nil {
			return nil, fmt.Errorf("discovery config encryption is required but no encryptor is configured")
		}
		encStr, err := r.encryptor.Encrypt(configBytes)
		if err != nil {
			return nil, fmt.Errorf("failed to encrypt discovery config: %w", err)
		}
		encryptedConfig = pgtype.Text{String: encStr, Valid: true}
	}

	beginner, ok := r.database.(TxBeginner)
	if !ok {
		return nil, fmt.Errorf("database driver must implement TxBeginner for atomic source update")
	}

	tx, err := beginner.Begin(ctx)
	if err != nil {
		return nil, fmt.Errorf("failed to begin transaction: %w", err)
	}
	defer func() { _ = tx.Rollback(ctx) }()
	qtx := r.queries.WithTx(tx)

	row, err := qtx.UpdateDiscoverySource(ctx, db.UpdateDiscoverySourceParams{
		ID:              id,
		Name:            req.Name,
		Enabled:         enabled,
		ScheduleCron:    cronStr,
		ConfigEncrypted: encryptedConfig,
	})
	if err != nil {
		return nil, fmt.Errorf("failed to update discovery source: %w", err)
	}

	if _, err := qtx.DeleteCollectorsBySourceID(ctx, id); err != nil {
		return nil, fmt.Errorf("failed to clear previous collectors: %w", err)
	}

	collectorResponses := make([]dto.CollectorResponse, 0, len(req.Collectors))
	for _, col := range req.Collectors {
		colID := uuid.New()
		colEnabled := true
		if col.Enabled != nil {
			colEnabled = *col.Enabled
		}

		var encColConfig pgtype.Text
		var sanitizedConfig map[string]interface{}
		var configuredSecrets []string

		if len(col.Config) > 0 {
			colBytes, err := json.Marshal(col.Config)
			if err != nil {
				return nil, fmt.Errorf("failed to marshal collector config for %s: %w", col.Type, err)
			}
			if r.encryptor == nil {
				return nil, fmt.Errorf("collector config encryption is required but no encryptor is configured")
			}
			encStr, err := r.encryptor.Encrypt(colBytes)
			if err != nil {
				return nil, fmt.Errorf("failed to encrypt collector config for %s: %w", col.Type, err)
			}
			encColConfig = pgtype.Text{String: encStr, Valid: true}

			sanitizedConfig = make(map[string]interface{})
			for k, v := range col.Config {
				if dto.IsSecretKey(col.Type, k) {
					if v != nil && strings.TrimSpace(fmt.Sprint(v)) != "" {
						configuredSecrets = append(configuredSecrets, k)
					}
				} else {
					sanitizedConfig[k] = v
				}
			}
		}

		colRow, err := qtx.CreateDiscoverySourceCollector(ctx, db.CreateDiscoverySourceCollectorParams{
			ID:              colID,
			SourceID:        id,
			CollectorType:   col.Type,
			ConfigEncrypted: encColConfig,
			Enabled:         colEnabled,
			CreatedAt:       pgtype.Timestamptz{Time: time.Now(), Valid: true},
		})
		if err != nil {
			return nil, fmt.Errorf("failed to recreate collector %s: %w", col.Type, err)
		}

		collectorResponses = append(collectorResponses, dto.CollectorResponse{
			ID:                colRow.ID,
			CollectorType:     colRow.CollectorType,
			Enabled:           colRow.Enabled,
			Config:            sanitizedConfig,
			ConfiguredSecrets: configuredSecrets,
		})
	}

	if err := tx.Commit(ctx); err != nil {
		return nil, fmt.Errorf("failed to commit update source transaction: %w", err)
	}

	resp, err := r.mapSourceToDTO(&row)
	if err != nil {
		return nil, err
	}
	resp.Collectors = collectorResponses
	r.attachLastRun(ctx, resp)
	r.attachTargetSubnets(ctx, resp)
	return resp, nil
}
