// Package repository provides database persistence for Discovery Engine entities.
package repository

import (
	"context"
	"encoding/json"
	"errors"
	"fmt"

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

// DiscoveryRepository contract defines data persistence operations for discovery sources and records.
type DiscoveryRepository interface {
	CreateSource(ctx context.Context, req *dto.CreateDiscoverySourceRequest) (*dto.DiscoverySourceResponse, error)
	GetSourceByID(ctx context.Context, id uuid.UUID) (*dto.DiscoverySourceResponse, error)
	ListSources(ctx context.Context) ([]*dto.DiscoverySourceResponse, error)
	UpdateSourceStatus(ctx context.Context, id uuid.UUID, status string) (*dto.DiscoverySourceResponse, error)
	DeleteSource(ctx context.Context, id uuid.UUID) error
	UpsertRecord(ctx context.Context, deviceID, sourceID uuid.UUID, matchedBy string, rawPayload map[string]interface{}) (*dto.DiscoveryRecordResponse, error)
	ListRecordsByDevice(ctx context.Context, deviceID uuid.UUID) ([]*dto.DiscoveryRecordResponse, error)
}

// PgDiscoveryRepository implements DiscoveryRepository backed by PostgreSQL.
type PgDiscoveryRepository struct {
	queries   *db.Queries
	encryptor crypto.Encryptor
}

// NewPgDiscoveryRepository constructs a PgDiscoveryRepository instance.
func NewPgDiscoveryRepository(database db.DBTX, encryptor crypto.Encryptor) *PgDiscoveryRepository {
	return &PgDiscoveryRepository{
		queries:   db.New(database),
		encryptor: encryptor,
	}
}

// CreateSource inserts a new discovery source into PostgreSQL.
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
		if r.encryptor != nil {
			encStr, err := r.encryptor.Encrypt(configBytes)
			if err != nil {
				return nil, fmt.Errorf("failed to encrypt discovery config: %w", err)
			}
			encryptedConfig = pgtype.Text{String: encStr, Valid: true}
		} else {
			encryptedConfig = pgtype.Text{String: string(configBytes), Valid: true}
		}
	}

	row, err := r.queries.CreateDiscoverySource(ctx, db.CreateDiscoverySourceParams{
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

	return mapSourceToDTO(&row), nil
}

// GetSourceByID retrieves a single discovery source by ID.
func (r *PgDiscoveryRepository) GetSourceByID(ctx context.Context, id uuid.UUID) (*dto.DiscoverySourceResponse, error) {
	row, err := r.queries.GetDiscoverySourceByID(ctx, id)
	if err != nil {
		if errors.Is(err, pgx.ErrNoRows) {
			return nil, fmt.Errorf("%w: %v", ErrSourceNotFound, id)
		}
		return nil, fmt.Errorf("failed to query discovery source: %w", err)
	}

	return mapSourceToDTO(&row), nil
}

// ListSources retrieves all discovery sources ordered by creation date descending.
func (r *PgDiscoveryRepository) ListSources(ctx context.Context) ([]*dto.DiscoverySourceResponse, error) {
	rows, err := r.queries.ListDiscoverySources(ctx)
	if err != nil {
		return nil, fmt.Errorf("failed to list discovery sources: %w", err)
	}

	items := make([]*dto.DiscoverySourceResponse, len(rows))
	for i, row := range rows {
		items[i] = mapSourceToDTO(&row)
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

	return mapSourceToDTO(&row), nil
}

// DeleteSource removes a discovery source record.
func (r *PgDiscoveryRepository) DeleteSource(ctx context.Context, id uuid.UUID) error {
	err := r.queries.DeleteDiscoverySource(ctx, id)
	if err != nil {
		return fmt.Errorf("failed to delete discovery source: %w", err)
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

func mapSourceToDTO(row *db.DiscoverySource) *dto.DiscoverySourceResponse {
	resp := &dto.DiscoverySourceResponse{
		ID:         row.ID,
		Name:       row.Name,
		Type:       row.Type,
		Enabled:    row.Enabled,
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
	return resp
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
