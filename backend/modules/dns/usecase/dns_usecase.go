// Package usecase implements business logic for DNS records.
package usecase

import (
	"context"
	"errors"
	"fmt"
	"net/netip"
	"strings"

	"github.com/google/uuid"
	"github.com/jackc/pgx/v5"
	"github.com/jackc/pgx/v5/pgtype"

	"github.com/matheussouza/inframap/internal/platform/db"
	"github.com/matheussouza/inframap/modules/dns/dto"
	"github.com/matheussouza/inframap/modules/dns/repository"
)

var (
	// ErrDNSRecordNotFound indicates a DNS record was not found.
	ErrDNSRecordNotFound = errors.New("dns record not found")

	// ErrDNSRecordNameConflict indicates a CNAME shares its name with another record.
	ErrDNSRecordNameConflict = errors.New("dns name conflict")

	// ErrDNSInvalidAddressType indicates an A record address is not IPv4.
	ErrDNSInvalidAddressType = errors.New("a record address must be IPv4")

	// ErrInvalidUUID indicates a malformed UUID string.
	ErrInvalidUUID = errors.New("invalid resource UUID format")
)

// DNSRecordUseCase defines business capabilities for DNS records.
type DNSRecordUseCase interface {
	CreateDNSRecord(ctx context.Context, req dto.CreateDNSRecordRequest) (*dto.DNSRecordResponse, error)
	GetDNSRecordByID(ctx context.Context, idStr string) (*dto.DNSRecordResponse, error)
	ListDNSRecords(ctx context.Context, page, perPage int32) ([]dto.DNSRecordResponse, int64, error)
	UpdateDNSRecord(ctx context.Context, idStr string, req dto.UpdateDNSRecordRequest) (*dto.DNSRecordResponse, error)
	DeleteDNSRecord(ctx context.Context, idStr string) error
	ListDNSRecordsByDevice(ctx context.Context, deviceIDStr string) ([]dto.DNSRecordResponse, error)
}

// DefaultDNSRecordUseCase implements DNSRecordUseCase.
type DefaultDNSRecordUseCase struct {
	repo repository.DNSRecordRepository
}

// NewDefaultDNSRecordUseCase creates a new DefaultDNSRecordUseCase.
func NewDefaultDNSRecordUseCase(repo repository.DNSRecordRepository) *DefaultDNSRecordUseCase {
	return &DefaultDNSRecordUseCase{repo: repo}
}

// CreateDNSRecord creates a new DNS record.
func (uc *DefaultDNSRecordUseCase) CreateDNSRecord(ctx context.Context, req dto.CreateDNSRecordRequest) (*dto.DNSRecordResponse, error) {
	req.Normalize()
	if valErrs := req.Validate(); len(valErrs) > 0 {
		return nil, fmt.Errorf("%w: %s", ErrDNSInvalidAddressType, valErrs[0].Issue)
	}

	name := strings.ToLower(strings.TrimSuffix(req.Name, "."))

	params := db.CreateDNSRecordParams{
		ID:          uuid.New(),
		Name:        name,
		RecordType:  req.RecordType,
		DeviceID:    pgtype.UUID{},
		Notes:       pgtype.Text{},
	}

	if req.Address != "" {
		addr, err := netip.ParseAddr(req.Address)
		if err != nil {
			return nil, fmt.Errorf("%w: invalid address %q", ErrDNSInvalidAddressType, req.Address)
		}
		if !addr.Is4() {
			return nil, fmt.Errorf("%w: %q is not IPv4", ErrDNSInvalidAddressType, req.Address)
		}
		params.Address = &addr
	}
	if req.Target != "" {
		params.Target = pgtype.Text{String: req.Target, Valid: true}
	}
	if req.DeviceID != nil && *req.DeviceID != "" {
		id, err := uuid.Parse(*req.DeviceID)
		if err != nil {
			return nil, fmt.Errorf("%w: invalid device id", ErrInvalidUUID)
		}
		params.DeviceID = pgtype.UUID{Bytes: id, Valid: true}
	}
	if req.Notes != "" {
		params.Notes = pgtype.Text{String: req.Notes, Valid: true}
	}

	record, err := uc.repo.CreateDNSRecord(ctx, params)
	if err != nil {
		if isUniqueViolation(err) {
			return nil, ErrDNSRecordNameConflict
		}
		return nil, err
	}

	return mapRecordToResponse(record), nil
}

// GetDNSRecordByID fetches a single DNS record by UUID.
func (uc *DefaultDNSRecordUseCase) GetDNSRecordByID(ctx context.Context, idStr string) (*dto.DNSRecordResponse, error) {
	id, err := uuid.Parse(idStr)
	if err != nil {
		return nil, ErrInvalidUUID
	}

	record, err := uc.repo.GetDNSRecordByID(ctx, id)
	if err != nil {
		if errors.Is(err, repository.ErrDNSRecordNotFound) {
			return nil, ErrDNSRecordNotFound
		}
		return nil, err
	}

	return mapRecordToResponse(record), nil
}

// ListDNSRecords returns paginated DNS records.
func (uc *DefaultDNSRecordUseCase) ListDNSRecords(ctx context.Context, page, perPage int32) ([]dto.DNSRecordResponse, int64, error) {
	if page < 1 {
		page = 1
	}
	if perPage < 1 || perPage > 100 {
		perPage = 50
	}
	offset := (page - 1) * perPage

	records, total, err := uc.repo.ListDNSRecords(ctx, perPage, offset)
	if err != nil {
		return nil, 0, err
	}

	responses := make([]dto.DNSRecordResponse, len(records))
	for i, r := range records {
		responses[i] = *mapRecordToResponse(&r)
	}

	return responses, total, nil
}

// UpdateDNSRecord updates an existing DNS record.
func (uc *DefaultDNSRecordUseCase) UpdateDNSRecord(ctx context.Context, idStr string, req dto.UpdateDNSRecordRequest) (*dto.DNSRecordResponse, error) {
	id, err := uuid.Parse(idStr)
	if err != nil {
		return nil, ErrInvalidUUID
	}

	if valErrs := req.Validate(); len(valErrs) > 0 {
		return nil, fmt.Errorf("%w: %s", ErrDNSInvalidAddressType, valErrs[0].Issue)
	}

	existing, err := uc.repo.GetDNSRecordByID(ctx, id)
	if err != nil {
		if errors.Is(err, repository.ErrDNSRecordNotFound) {
			return nil, ErrDNSRecordNotFound
		}
		return nil, err
	}

	name := existing.Name
	if req.Name != nil {
		name = strings.ToLower(strings.TrimSuffix(*req.Name, "."))
	}
	recordType := existing.RecordType
	if req.RecordType != nil {
		recordType = *req.RecordType
	}

	address := existing.Address
	if req.Address != nil && *req.Address != "" {
		addr, err := netip.ParseAddr(*req.Address)
		if err != nil {
			return nil, fmt.Errorf("%w: invalid address %q", ErrDNSInvalidAddressType, *req.Address)
		}
		if !addr.Is4() {
			return nil, fmt.Errorf("%w: %q is not IPv4", ErrDNSInvalidAddressType, *req.Address)
		}
		address = &addr
	} else if req.Address != nil && *req.Address == "" {
		address = nil
	}

	target := existing.Target
	if req.Target != nil && *req.Target != "" {
		target = pgtype.Text{String: *req.Target, Valid: true}
	} else if req.Target != nil && *req.Target == "" {
		target = pgtype.Text{}
	}

	deviceID := existing.DeviceID
	if req.DeviceID != nil && *req.DeviceID != "" {
		did, err := uuid.Parse(*req.DeviceID)
		if err != nil {
			return nil, fmt.Errorf("%w: invalid device id", ErrInvalidUUID)
		}
		deviceID = pgtype.UUID{Bytes: did, Valid: true}
	} else if req.DeviceID != nil && *req.DeviceID == "" {
		deviceID = pgtype.UUID{}
	}

	notes := existing.Notes
	if req.Notes != nil {
		notes = pgtype.Text{String: *req.Notes, Valid: true}
	}

	params := db.UpdateDNSRecordParams{
		ID:          id,
		Name:        name,
		RecordType:  recordType,
		Address:     address,
		Target:      target,
		DeviceID:    deviceID,
		Notes:       notes,
	}

	record, err := uc.repo.UpdateDNSRecord(ctx, params)
	if err != nil {
		if isUniqueViolation(err) {
			return nil, ErrDNSRecordNameConflict
		}
		return nil, err
	}

	return mapRecordToResponse(record), nil
}

// DeleteDNSRecord deletes a DNS record by UUID.
func (uc *DefaultDNSRecordUseCase) DeleteDNSRecord(ctx context.Context, idStr string) error {
	id, err := uuid.Parse(idStr)
	if err != nil {
		return ErrInvalidUUID
	}

	if err := uc.repo.DeleteDNSRecord(ctx, id); err != nil {
		if errors.Is(err, pgx.ErrNoRows) {
			return ErrDNSRecordNotFound
		}
		return err
	}

	return nil
}

// ListDNSRecordsByDevice lists DNS records linked to a device.
func (uc *DefaultDNSRecordUseCase) ListDNSRecordsByDevice(ctx context.Context, deviceIDStr string) ([]dto.DNSRecordResponse, error) {
	deviceID, err := uuid.Parse(deviceIDStr)
	if err != nil {
		return nil, ErrInvalidUUID
	}

	records, err := uc.repo.ListDNSRecordsByDevice(ctx, deviceID)
	if err != nil {
		return nil, err
	}

	responses := make([]dto.DNSRecordResponse, len(records))
	for i, r := range records {
		responses[i] = *mapRecordToResponse(&r)
	}

	return responses, nil
}

func mapRecordToResponse(record *db.DNSRecord) *dto.DNSRecordResponse {
	resp := &dto.DNSRecordResponse{
		ID:         record.ID.String(),
		Name:       record.Name,
		RecordType: record.RecordType,
		Address:    mapInetToString(record.Address),
		Target:     record.Target.String,
		Notes:      record.Notes.String,
		CreatedAt:  record.CreatedAt.Time,
		UpdatedAt:  record.UpdatedAt.Time,
	}
	if record.DeviceID.Valid {
		id := uuid.UUID(record.DeviceID.Bytes).String()
		resp.DeviceID = &id
	}
	return resp
}

func mapInetToString(addr *netip.Addr) string {
	if addr == nil {
		return ""
	}
	return addr.String()
}

func isUniqueViolation(err error) bool {
	msg := err.Error()
	return strings.Contains(msg, "23505") || strings.Contains(msg, "duplicate key") || strings.Contains(msg, "unique constraint")
}
