// Package repository provides data access for devices, staging queue, and subnets.
package repository

import (
	"context"
	"encoding/json"
	"errors"
	"fmt"
	"net"
	"net/netip"

	"github.com/google/uuid"
	"github.com/jackc/pgx/v5"
	"github.com/jackc/pgx/v5/pgtype"
	"github.com/jackc/pgx/v5/pgxpool"
	"github.com/matheussouza/inframap/internal/platform/db"
)

var (
	// ErrDeviceNotFound indicates a device was not found in database.
	ErrDeviceNotFound = errors.New("device not found")
	// ErrStagingDeviceNotFound indicates a staged device was not found.
	ErrStagingDeviceNotFound = errors.New("staging device not found")
	// ErrSubnetNotFound indicates a subnet was not found.
	ErrSubnetNotFound = errors.New("subnet not found")
)

// InventoryRepository defines database operations for inventory resources.
type InventoryRepository interface {
	CreateDevice(ctx context.Context, params db.CreateDeviceParams) (*db.Device, error)
	GetDeviceByID(ctx context.Context, id uuid.UUID, includeDeleted bool) (*db.Device, error)
	ListDevices(ctx context.Context, searchQuery, deviceType string, limit, offset int32, includeDeleted bool) ([]db.Device, int64, error)
	UpdateDevice(ctx context.Context, params db.UpdateDeviceParams) (*db.Device, error)
	SoftDeleteDevice(ctx context.Context, id uuid.UUID) error

	ListDevicesByProviderScope(ctx context.Context, providerScope string) ([]db.Device, error)
	MarkDeviceAbsent(ctx context.Context, id uuid.UUID, archiveThreshold int32) (*db.Device, error)

	CreateStagingDevice(ctx context.Context, params db.CreateStagingDeviceParams) (*db.DeviceStaging, error)
	GetStagingDeviceByID(ctx context.Context, id uuid.UUID) (*db.DeviceStaging, error)
	ListStagingDevices(ctx context.Context, status string, limit, offset int32) ([]db.DeviceStaging, int64, error)
	UpdateStagingDeviceStatus(ctx context.Context, id uuid.UUID, status string) error

	CreateSubnet(ctx context.Context, params db.CreateSubnetParams) (*db.Subnet, error)
	ListSubnets(ctx context.Context) ([]db.Subnet, error)
}

// PgInventoryRepository implements InventoryRepository using pgxpool.
type PgInventoryRepository struct {
	pool *pgxpool.Pool
}

// NewPgInventoryRepository creates a new PgInventoryRepository instance.
func NewPgInventoryRepository(pool *pgxpool.Pool) *PgInventoryRepository {
	return &PgInventoryRepository{pool: pool}
}

// CreateDevice inserts a new device record into devices.
func (r *PgInventoryRepository) CreateDevice(ctx context.Context, params db.CreateDeviceParams) (*db.Device, error) {
	queries := db.New(r.pool)
	device, err := queries.CreateDevice(ctx, params)
	if err != nil {
		return nil, fmt.Errorf("failed to create device: %w", err)
	}
	return &device, nil
}

// GetDeviceByID fetches a device by UUID.
func (r *PgInventoryRepository) GetDeviceByID(ctx context.Context, id uuid.UUID, includeDeleted bool) (*db.Device, error) {
	queries := db.New(r.pool)
	device, err := queries.GetDeviceByID(ctx, db.GetDeviceByIDParams{
		ID:      id,
		Column2: includeDeleted,
	})
	if err != nil {
		if errors.Is(err, pgx.ErrNoRows) {
			return nil, ErrDeviceNotFound
		}
		return nil, fmt.Errorf("failed to fetch device: %w", err)
	}
	return &device, nil
}

// ListDevices fetches devices with offset pagination and search filtering.
func (r *PgInventoryRepository) ListDevices(ctx context.Context, searchQuery, deviceType string, limit, offset int32, includeDeleted bool) ([]db.Device, int64, error) {
	queries := db.New(r.pool)
	devices, err := queries.ListDevices(ctx, db.ListDevicesParams{
		Column1: includeDeleted,
		Column2: searchQuery,
		Column3: deviceType,
		Limit:   limit,
		Offset:  offset,
	})
	if err != nil {
		return nil, 0, fmt.Errorf("failed to list devices: %w", err)
	}

	total, err := queries.CountDevices(ctx, db.CountDevicesParams{
		Column1: includeDeleted,
		Column2: searchQuery,
		Column3: deviceType,
	})
	if err != nil {
		return nil, 0, fmt.Errorf("failed to count devices: %w", err)
	}

	return devices, total, nil
}

// UpdateDevice updates device attributes.
func (r *PgInventoryRepository) UpdateDevice(ctx context.Context, params db.UpdateDeviceParams) (*db.Device, error) {
	queries := db.New(r.pool)
	device, err := queries.UpdateDevice(ctx, params)
	if err != nil {
		return nil, fmt.Errorf("failed to update device: %w", err)
	}
	return &device, nil
}

// SoftDeleteDevice marks a device as deleted by setting deleted_at.
func (r *PgInventoryRepository) SoftDeleteDevice(ctx context.Context, id uuid.UUID) error {
	queries := db.New(r.pool)
	return queries.SoftDeleteDevice(ctx, id)
}

// ListDevicesByProviderScope returns every non-archived device an authoritative provider
// owns in a scope, which is the candidate set the lifecycle hysteresis evaluates.
func (r *PgInventoryRepository) ListDevicesByProviderScope(ctx context.Context, providerScope string) ([]db.Device, error) {
	queries := db.New(r.pool)
	devices, err := queries.ListDevicesByProviderScope(ctx, pgtype.Text{String: providerScope, Valid: true})
	if err != nil {
		return nil, fmt.Errorf("failed to list devices by provider scope: %w", err)
	}
	return devices, nil
}

// MarkDeviceAbsent advances a device along the absence hysteresis, archiving it once the
// consecutive absence streak reaches archiveThreshold.
func (r *PgInventoryRepository) MarkDeviceAbsent(ctx context.Context, id uuid.UUID, archiveThreshold int32) (*db.Device, error) {
	queries := db.New(r.pool)
	device, err := queries.MarkDeviceAbsent(ctx, db.MarkDeviceAbsentParams{ID: id, ArchiveThreshold: archiveThreshold})
	if err != nil {
		return nil, fmt.Errorf("failed to mark device absent: %w", err)
	}
	return &device, nil
}

// CreateStagingDevice inserts a new unverified device into device_staging.
func (r *PgInventoryRepository) CreateStagingDevice(ctx context.Context, params db.CreateStagingDeviceParams) (*db.DeviceStaging, error) {
	queries := db.New(r.pool)
	staging, err := queries.CreateStagingDevice(ctx, params)
	if err != nil {
		return nil, fmt.Errorf("failed to create staging device: %w", err)
	}
	return &staging, nil
}

// GetStagingDeviceByID fetches a staged device by UUID.
func (r *PgInventoryRepository) GetStagingDeviceByID(ctx context.Context, id uuid.UUID) (*db.DeviceStaging, error) {
	queries := db.New(r.pool)
	staging, err := queries.GetStagingDeviceByID(ctx, id)
	if err != nil {
		if errors.Is(err, pgx.ErrNoRows) {
			return nil, ErrStagingDeviceNotFound
		}
		return nil, fmt.Errorf("failed to fetch staging device: %w", err)
	}
	return &staging, nil
}

// ListStagingDevices lists staged devices filtered by status with offset pagination.
func (r *PgInventoryRepository) ListStagingDevices(ctx context.Context, status string, limit, offset int32) ([]db.DeviceStaging, int64, error) {
	queries := db.New(r.pool)
	items, err := queries.ListStagingDevices(ctx, db.ListStagingDevicesParams{
		Status: status,
		Limit:  limit,
		Offset: offset,
	})
	if err != nil {
		return nil, 0, fmt.Errorf("failed to list staging devices: %w", err)
	}

	total, err := queries.CountStagingDevices(ctx, status)
	if err != nil {
		return nil, 0, fmt.Errorf("failed to count staging devices: %w", err)
	}

	return items, total, nil
}

// UpdateStagingDeviceStatus updates the status of a staged device ('approved', 'dismissed').
func (r *PgInventoryRepository) UpdateStagingDeviceStatus(ctx context.Context, id uuid.UUID, status string) error {
	queries := db.New(r.pool)
	return queries.UpdateStagingDeviceStatus(ctx, db.UpdateStagingDeviceStatusParams{
		ID:     id,
		Status: status,
	})
}

// CreateSubnet inserts a new subnet record.
func (r *PgInventoryRepository) CreateSubnet(ctx context.Context, params db.CreateSubnetParams) (*db.Subnet, error) {
	queries := db.New(r.pool)
	subnet, err := queries.CreateSubnet(ctx, params)
	if err != nil {
		return nil, fmt.Errorf("failed to create subnet: %w", err)
	}
	return &subnet, nil
}

// ListSubnets lists all configured subnets.
func (r *PgInventoryRepository) ListSubnets(ctx context.Context) ([]db.Subnet, error) {
	queries := db.New(r.pool)
	subnets, err := queries.ListSubnets(ctx)
	if err != nil {
		return nil, fmt.Errorf("failed to list subnets: %w", err)
	}
	return subnets, nil
}

// ExtractUserLockedFields reads 'user_locked_fields' from metadata JSONB.
func ExtractUserLockedFields(metadata []byte) []string {
	if len(metadata) == 0 {
		return nil
	}
	var metaMap map[string]any
	if err := json.Unmarshal(metadata, &metaMap); err != nil {
		return nil
	}
	rawFields, exists := metaMap["user_locked_fields"]
	if !exists {
		return nil
	}
	slice, ok := rawFields.([]any)
	if !ok {
		return nil
	}
	fields := make([]string, len(slice))
	for i, v := range slice {
		if str, isStr := v.(string); isStr {
			fields[i] = str
		}
	}
	return fields
}

// ExtractPowerState reads the runtime state a provider reported for a workload from
// metadata JSONB. It is empty for devices no provider owns, such as those found by network
// sweeps.
func ExtractPowerState(metadata []byte) string {
	if len(metadata) == 0 {
		return ""
	}
	var metaMap map[string]any
	if err := json.Unmarshal(metadata, &metaMap); err != nil {
		return ""
	}
	state, _ := metaMap["power_state"].(string)
	return state
}

// MapUUIDToString converts a nullable UUID column to its string form, returning an empty
// string when the column is NULL.
func MapUUIDToString(id pgtype.UUID) string {
	if !id.Valid {
		return ""
	}
	return uuid.UUID(id.Bytes).String()
}

// FormatMetadataWithLockedFields attaches user_locked_fields array into metadata JSONB bytes.
func FormatMetadataWithLockedFields(existingMetadata []byte, lockedFields []string) ([]byte, error) {
	metaMap := make(map[string]any)
	if len(existingMetadata) > 0 {
		_ = json.Unmarshal(existingMetadata, &metaMap)
	}
	metaMap["user_locked_fields"] = lockedFields
	return json.Marshal(metaMap)
}

// MapInetToString converts *netip.Addr to string.
func MapInetToString(addr *netip.Addr) string {
	if addr == nil {
		return ""
	}
	return addr.String()
}

// MapMacToString converts net.HardwareAddr to string.
func MapMacToString(mac net.HardwareAddr) string {
	if len(mac) == 0 {
		return ""
	}
	return mac.String()
}
