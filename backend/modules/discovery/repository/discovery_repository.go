// Package repository provides database persistence for Discovery Engine entities.
package repository

import (
	"context"
	"encoding/json"
	"errors"
	"fmt"
	"log/slog"
	"time"

	"github.com/google/uuid"
	"github.com/jackc/pgx/v5"
	"github.com/jackc/pgx/v5/pgtype"
	"github.com/matheussouza/inframap/internal/platform/crypto"
	"github.com/matheussouza/inframap/internal/platform/db"
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
	UpsertRecord(ctx context.Context, deviceID, sourceID uuid.UUID, matchedBy string, rawPayload map[string]interface{}) (*dto.DiscoveryRecordResponse, error)
	ListRecordsByDevice(ctx context.Context, deviceID uuid.UUID) ([]*dto.DiscoveryRecordResponse, error)
	CreateCollectorRun(ctx context.Context, run *db.CreateCollectorRunParams) error
	ListRunsBySourceID(ctx context.Context, sourceID uuid.UUID, limit int) ([]*dto.CollectorRunResponse, error)
}

// ConfigPayload represents decrypted discovery source configuration.
type ConfigPayload struct {
	CIDR string `json:"cidr"`
}

// PgDiscoveryRepository implements DiscoveryRepository backed by PostgreSQL.
type PgDiscoveryRepository struct {
	database  db.DBTX
	queries   *db.Queries
	encryptor crypto.Encryptor
}

// NewPgDiscoveryRepository constructs a PgDiscoveryRepository instance.
func NewPgDiscoveryRepository(database db.DBTX, encryptor crypto.Encryptor) *PgDiscoveryRepository {
	return &PgDiscoveryRepository{
		database:  database,
		queries:   db.New(database),
		encryptor: encryptor,
	}
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
		resp.Collectors[i] = dto.CollectorResponse{
			ID:            c.ID,
			CollectorType: c.CollectorType,
			Enabled:       c.Enabled,
		}
	}

	r.attachLastRun(ctx, resp)

	return resp, nil
}

// ListSources retrieves all discovery sources ordered by creation date descending with their collectors.
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
	return items, nil
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

	return resp, nil
}

// DeleteSource removes a discovery source record.
func (r *PgDiscoveryRepository) DeleteSource(ctx context.Context, id uuid.UUID) error {
	rows, err := r.queries.DeleteDiscoverySource(ctx, id)
	if err != nil {
		return fmt.Errorf("failed to delete discovery source: %w", err)
	}
	if rows == 0 {
		return fmt.Errorf("%w: %v", ErrSourceNotFound, id)
	}
	return nil
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
	if row.ConfigEncrypted.Valid {
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
		resp.ConfigCIDR = cfg.CIDR
	}
	return resp, nil
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
		var errMsg string
		if row.ErrorMessage.Valid {
			errMsg = row.ErrorMessage.String
		}
		items[i] = &dto.CollectorRunResponse{
			ID:            row.ID,
			SourceID:      row.SourceID,
			CollectorType: row.CollectorType,
			Status:        row.Status,
			DevicesFound:  int(row.DevicesFound),
			DurationMs:    int64(row.DurationMs),
			ErrorMessage:  errMsg,
			StartedAt:     row.StartedAt.Time,
			FinishedAt:    row.FinishedAt.Time,
		}
	}
	return items, nil
}

func buildLastRunSummary(runs []db.DiscoveryCollectorRun) *dto.CollectorRunSummary {
	if len(runs) == 0 {
		return nil
	}
	latestTime := runs[0].StartedAt.Time
	var batch []db.DiscoveryCollectorRun
	for _, r := range runs {
		diff := latestTime.Sub(r.StartedAt.Time)
		if diff < 0 {
			diff = -diff
		}
		if diff <= 5*time.Second {
			batch = append(batch, r)
		}
	}
	if len(batch) == 0 {
		batch = runs[:1]
	}

	summary := &dto.CollectorRunSummary{
		StartedAt:  batch[0].StartedAt.Time,
		FinishedAt: batch[0].FinishedAt.Time,
		Collectors: make([]dto.CollectorRunDetail, 0, len(batch)),
	}

	successCount := 0
	errorCount := 0
	for _, r := range batch {
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

func (r *PgDiscoveryRepository) attachLastRun(ctx context.Context, resp *dto.DiscoverySourceResponse) {
	if resp == nil {
		return
	}
	runs, err := r.queries.ListCollectorRunsBySource(ctx, db.ListCollectorRunsBySourceParams{
		SourceID: resp.ID,
		Limit:    10,
		Offset:   0,
	})
	if err != nil {
		slog.Warn("failed to list recent collector runs for discovery source",
			slog.String("source_id", resp.ID.String()),
			slog.Any("error", err),
		)
		return
	}
	if len(runs) > 0 {
		resp.LastRun = buildLastRunSummary(runs)
	}
}
