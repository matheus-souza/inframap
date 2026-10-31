// Package repository provides data access for DNS records.
package repository

import (
	"context"
	"errors"
	"fmt"
	"net/netip"

	"github.com/google/uuid"
	"github.com/jackc/pgx/v5"
	"github.com/jackc/pgx/v5/pgxpool"
	"github.com/matheussouza/inframap/internal/platform/db"
)

// ErrDNSRecordNotFound indicates a DNS record was not found.
var ErrDNSRecordNotFound = fmt.Errorf("dns record not found")

// DNSRecordRepository defines database operations for DNS records.
type DNSRecordRepository interface {
	CreateDNSRecord(ctx context.Context, params db.CreateDNSRecordParams) (*db.DNSRecord, error)
	GetDNSRecordByID(ctx context.Context, id uuid.UUID) (*db.DNSRecord, error)
	ListDNSRecords(ctx context.Context, limit, offset int32) ([]db.DNSRecord, int64, error)
	UpdateDNSRecord(ctx context.Context, params db.UpdateDNSRecordParams) (*db.DNSRecord, error)
	DeleteDNSRecord(ctx context.Context, id uuid.UUID) error
	ListDNSRecordsByDevice(ctx context.Context, deviceID uuid.UUID) ([]db.DNSRecord, error)
}

// PgDNSRecordRepository implements DNSRecordRepository using pgxpool directly.
type PgDNSRecordRepository struct {
	pool *pgxpool.Pool
}

// NewPgDNSRecordRepository creates a new PgDNSRecordRepository instance.
func NewPgDNSRecordRepository(pool *pgxpool.Pool) *PgDNSRecordRepository {
	return &PgDNSRecordRepository{pool: pool}
}

// CreateDNSRecord inserts a new DNS record and returns it with generated fields.
func (r *PgDNSRecordRepository) CreateDNSRecord(ctx context.Context, params db.CreateDNSRecordParams) (*db.DNSRecord, error) {
	var d db.DNSRecord
	err := r.pool.QueryRow(ctx, `
		INSERT INTO dns_records (id, name, record_type, address, target, device_id, notes)
		VALUES ($1, $2, $3, $4, $5, $6, $7)
		RETURNING id, name, record_type, address, target, device_id, notes, created_at, updated_at
	`, params.ID, params.Name, params.RecordType, params.Address, params.Target, params.DeviceID, params.Notes).
		Scan(&d.ID, &d.Name, &d.RecordType, &d.Address, &d.Target, &d.DeviceID, &d.Notes, &d.CreatedAt, &d.UpdatedAt)
	if err != nil {
		return nil, fmt.Errorf("failed to create DNS record: %w", err)
	}
	return &d, nil
}

// GetDNSRecordByID returns a DNS record by its UUID.
func (r *PgDNSRecordRepository) GetDNSRecordByID(ctx context.Context, id uuid.UUID) (*db.DNSRecord, error) {
	var d db.DNSRecord
	err := r.pool.QueryRow(ctx, `
		SELECT id, name, record_type, address, target, device_id, notes, created_at, updated_at
		FROM dns_records WHERE id = $1
	`, id).Scan(&d.ID, &d.Name, &d.RecordType, &d.Address, &d.Target, &d.DeviceID, &d.Notes, &d.CreatedAt, &d.UpdatedAt)
	if err != nil {
		if errors.Is(err, pgx.ErrNoRows) {
			return nil, ErrDNSRecordNotFound
		}
		return nil, fmt.Errorf("failed to fetch DNS record: %w", err)
	}
	return &d, nil
}

// ListDNSRecords returns a paginated list of all DNS records.
func (r *PgDNSRecordRepository) ListDNSRecords(ctx context.Context, limit, offset int32) ([]db.DNSRecord, int64, error) {
	var total int64
	err := r.pool.QueryRow(ctx, `SELECT COUNT(*) FROM dns_records`).Scan(&total)
	if err != nil {
		return nil, 0, fmt.Errorf("failed to count DNS records: %w", err)
	}

	rows, err := r.pool.Query(ctx, `
		SELECT id, name, record_type, address, target, device_id, notes, created_at, updated_at
		FROM dns_records ORDER BY name ASC, record_type ASC LIMIT $1 OFFSET $2
	`, limit, offset)
	if err != nil {
		return nil, 0, fmt.Errorf("failed to list DNS records: %w", err)
	}
	defer rows.Close()

	var records []db.DNSRecord
	for rows.Next() {
		var d db.DNSRecord
		if err := rows.Scan(&d.ID, &d.Name, &d.RecordType, &d.Address, &d.Target, &d.DeviceID, &d.Notes, &d.CreatedAt, &d.UpdatedAt); err != nil {
			return nil, 0, fmt.Errorf("failed to scan DNS record: %w", err)
		}
		records = append(records, d)
	}
	if records == nil {
		records = []db.DNSRecord{}
	}
	return records, total, nil
}

// UpdateDNSRecord updates an existing DNS record and returns it.
func (r *PgDNSRecordRepository) UpdateDNSRecord(ctx context.Context, params db.UpdateDNSRecordParams) (*db.DNSRecord, error) {
	var d db.DNSRecord
	err := r.pool.QueryRow(ctx, `
		UPDATE dns_records
		SET name = $2, record_type = $3, address = $4, target = $5, device_id = $6, notes = $7, updated_at = NOW()
		WHERE id = $1
		RETURNING id, name, record_type, address, target, device_id, notes, created_at, updated_at
	`, params.ID, params.Name, params.RecordType, params.Address, params.Target, params.DeviceID, params.Notes).
		Scan(&d.ID, &d.Name, &d.RecordType, &d.Address, &d.Target, &d.DeviceID, &d.Notes, &d.CreatedAt, &d.UpdatedAt)
	if err != nil {
		if errors.Is(err, pgx.ErrNoRows) {
			return nil, ErrDNSRecordNotFound
		}
		return nil, fmt.Errorf("failed to update DNS record: %w", err)
	}
	return &d, nil
}

// DeleteDNSRecord deletes a DNS record by its UUID.
func (r *PgDNSRecordRepository) DeleteDNSRecord(ctx context.Context, id uuid.UUID) error {
	res, err := r.pool.Exec(ctx, `DELETE FROM dns_records WHERE id = $1`, id)
	if err != nil {
		return fmt.Errorf("failed to delete DNS record: %w", err)
	}
	if res.RowsAffected() == 0 {
		return ErrDNSRecordNotFound
	}
	return nil
}

// ListDNSRecordsByDevice returns all DNS records associated with a device.
func (r *PgDNSRecordRepository) ListDNSRecordsByDevice(ctx context.Context, deviceID uuid.UUID) ([]db.DNSRecord, error) {
	rows, err := r.pool.Query(ctx, `
		SELECT id, name, record_type, address, target, device_id, notes, created_at, updated_at
		FROM dns_records WHERE device_id = $1 ORDER BY name ASC, record_type ASC
	`, deviceID)
	if err != nil {
		return nil, fmt.Errorf("failed to list DNS records by device: %w", err)
	}
	defer rows.Close()

	var records []db.DNSRecord
	for rows.Next() {
		var d db.DNSRecord
		if err := rows.Scan(&d.ID, &d.Name, &d.RecordType, &d.Address, &d.Target, &d.DeviceID, &d.Notes, &d.CreatedAt, &d.UpdatedAt); err != nil {
			return nil, fmt.Errorf("failed to scan DNS record: %w", err)
		}
		records = append(records, d)
	}
	if records == nil {
		records = []db.DNSRecord{}
	}
	return records, nil
}

// MapInetToString converts *netip.Addr to string.
func MapInetToString(addr *netip.Addr) string {
	if addr == nil {
		return ""
	}
	return addr.String()
}
