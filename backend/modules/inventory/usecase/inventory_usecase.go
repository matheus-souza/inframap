// Package usecase implements business logic for inventory management.
package usecase

import (
	"context"
	"errors"
	"fmt"
	"log/slog"
	"net"
	"net/netip"
	"slices"
	"strings"

	"github.com/google/uuid"
	"github.com/jackc/pgx/v5/pgtype"

	"github.com/matheussouza/inframap/internal/platform/db"
	"github.com/matheussouza/inframap/internal/platform/eventbus"
	"github.com/matheussouza/inframap/modules/inventory/dto"
	"github.com/matheussouza/inframap/modules/inventory/repository"
)

var (
	// ErrInvalidUUID indicates a malformed UUID string.
	ErrInvalidUUID = errors.New("invalid resource UUID format")

	// ErrInvalidInput indicates malformed user input.
	ErrInvalidInput = errors.New("invalid input")
)

// InventoryUseCase defines business capabilities for inventory resources.
type InventoryUseCase interface {
	CreateDevice(ctx context.Context, req dto.CreateDeviceRequest) (*dto.DeviceResponse, error)
	GetDeviceByID(ctx context.Context, idStr string, includeDeleted bool) (*dto.DeviceResponse, error)
	ListDevices(ctx context.Context, searchQuery, deviceType string, page, perPage int32, includeDeleted bool) ([]dto.DeviceResponse, int64, error)
	UpdateDevice(ctx context.Context, idStr string, req dto.UpdateDeviceRequest) (*dto.DeviceResponse, error)
	SoftDeleteDevice(ctx context.Context, idStr string) error

	ListStagingDevices(ctx context.Context, status string, page, perPage int32) ([]dto.StagingDeviceResponse, int64, error)
	ApproveStagingDevice(ctx context.Context, stagingIDStr string) (*dto.DeviceResponse, error)
	DismissStagingDevice(ctx context.Context, stagingIDStr string) error

	CreateSubnet(ctx context.Context, req dto.CreateSubnetRequest) (*dto.SubnetResponse, error)
	ListSubnets(ctx context.Context) ([]dto.SubnetResponse, error)
}

// DefaultInventoryUseCase implements InventoryUseCase.
type DefaultInventoryUseCase struct {
	repo     repository.InventoryRepository
	eventBus eventbus.EventBus
	logger   *slog.Logger
}

// NewDefaultInventoryUseCase creates a new DefaultInventoryUseCase.
func NewDefaultInventoryUseCase(repo repository.InventoryRepository, bus eventbus.EventBus, logger *slog.Logger) *DefaultInventoryUseCase {
	return &DefaultInventoryUseCase{
		repo:     repo,
		eventBus: bus,
		logger:   logger,
	}
}

// CreateDevice registers a new active device and publishes 'device.created' domain event.
func (uc *DefaultInventoryUseCase) CreateDevice(ctx context.Context, req dto.CreateDeviceRequest) (*dto.DeviceResponse, error) {
	deviceID := uuid.New()

	params := db.CreateDeviceParams{
		ID:         deviceID,
		Hostname:   strings.TrimSpace(req.Hostname),
		DeviceType: strings.TrimSpace(req.DeviceType),
		Status:     "active",
		Metadata:   []byte("{}"),
	}

	if req.Manufacturer != "" {
		params.Manufacturer = pgtype.Text{String: strings.TrimSpace(req.Manufacturer), Valid: true}
	}
	if req.Model != "" {
		params.Model = pgtype.Text{String: strings.TrimSpace(req.Model), Valid: true}
	}
	if req.SerialNumber != "" {
		params.SerialNumber = pgtype.Text{String: strings.TrimSpace(req.SerialNumber), Valid: true}
	}
	if req.IPAddress != "" {
		if parsedIP, err := netip.ParseAddr(strings.TrimSpace(req.IPAddress)); err == nil {
			params.IpAddress = &parsedIP
		}
	}
	if req.MACAddress != "" {
		if parsedMAC, err := net.ParseMAC(strings.TrimSpace(req.MACAddress)); err == nil {
			params.MacAddress = parsedMAC
		}
	}

	device, err := uc.repo.CreateDevice(ctx, params)
	if err != nil {
		return nil, err
	}

	// Publish device.created domain event
	if uc.eventBus != nil {
		evt := eventbus.NewBaseEvent("device.created", map[string]string{
			"device_id": device.ID.String(),
			"hostname":  device.Hostname,
		})
		_ = uc.eventBus.Publish(ctx, evt)
	}

	return uc.mapDeviceToResponse(device), nil
}

// GetDeviceByID fetches details of a specific device.
func (uc *DefaultInventoryUseCase) GetDeviceByID(ctx context.Context, idStr string, includeDeleted bool) (*dto.DeviceResponse, error) {
	deviceID, err := uuid.Parse(idStr)
	if err != nil {
		return nil, ErrInvalidUUID
	}

	device, err := uc.repo.GetDeviceByID(ctx, deviceID, includeDeleted)
	if err != nil {
		return nil, err
	}

	return uc.mapDeviceToResponse(device), nil
}

// ListDevices returns paginated active devices.
func (uc *DefaultInventoryUseCase) ListDevices(ctx context.Context, searchQuery, deviceType string, page, perPage int32, includeDeleted bool) ([]dto.DeviceResponse, int64, error) {
	if page < 1 {
		page = 1
	}
	if perPage < 1 || perPage > 100 {
		perPage = 50
	}
	offset := (page - 1) * perPage

	devices, total, err := uc.repo.ListDevices(ctx, strings.TrimSpace(searchQuery), strings.TrimSpace(deviceType), perPage, offset, includeDeleted)
	if err != nil {
		return nil, 0, err
	}

	responses := make([]dto.DeviceResponse, len(devices))
	for i, d := range devices {
		responses[i] = *uc.mapDeviceToResponse(&d)
	}

	return responses, total, nil
}

// UpdateDevice updates device properties and appends modified fields to user_locked_fields in metadata.
func (uc *DefaultInventoryUseCase) UpdateDevice(ctx context.Context, idStr string, req dto.UpdateDeviceRequest) (*dto.DeviceResponse, error) {
	deviceID, err := uuid.Parse(idStr)
	if err != nil {
		return nil, ErrInvalidUUID
	}

	existing, err := uc.repo.GetDeviceByID(ctx, deviceID, false)
	if err != nil {
		return nil, err
	}

	lockedFields := repository.ExtractUserLockedFields(existing.Metadata)

	params := db.UpdateDeviceParams{
		ID:           existing.ID,
		Hostname:     existing.Hostname,
		IpAddress:    existing.IpAddress,
		MacAddress:   existing.MacAddress,
		Manufacturer: existing.Manufacturer,
		Model:        existing.Model,
		SerialNumber: existing.SerialNumber,
		DeviceType:   existing.DeviceType,
		Status:       existing.Status,
		Metadata:     existing.Metadata,
	}

	if req.Hostname != nil {
		params.Hostname = strings.TrimSpace(*req.Hostname)
		if !slices.Contains(lockedFields, "hostname") {
			lockedFields = append(lockedFields, "hostname")
		}
	}
	if req.Manufacturer != nil {
		params.Manufacturer = pgtype.Text{String: strings.TrimSpace(*req.Manufacturer), Valid: true}
		if !slices.Contains(lockedFields, "manufacturer") {
			lockedFields = append(lockedFields, "manufacturer")
		}
	}
	if req.Model != nil {
		params.Model = pgtype.Text{String: strings.TrimSpace(*req.Model), Valid: true}
		if !slices.Contains(lockedFields, "model") {
			lockedFields = append(lockedFields, "model")
		}
	}
	if req.DeviceType != nil {
		params.DeviceType = strings.TrimSpace(*req.DeviceType)
		if !slices.Contains(lockedFields, "device_type") {
			lockedFields = append(lockedFields, "device_type")
		}
	}
	if req.IPAddress != nil {
		parsedIP, err := netip.ParseAddr(strings.TrimSpace(*req.IPAddress))
		if err != nil {
			return nil, fmt.Errorf("%w: invalid IP address %q", ErrInvalidInput, *req.IPAddress)
		}
		params.IpAddress = &parsedIP
		if !slices.Contains(lockedFields, "ip_address") {
			lockedFields = append(lockedFields, "ip_address")
		}
	}
	if req.MACAddress != nil {
		parsedMAC, err := net.ParseMAC(strings.TrimSpace(*req.MACAddress))
		if err != nil {
			return nil, fmt.Errorf("%w: invalid MAC address %q", ErrInvalidInput, *req.MACAddress)
		}
		params.MacAddress = parsedMAC
		if !slices.Contains(lockedFields, "mac_address") {
			lockedFields = append(lockedFields, "mac_address")
		}
	}
	if req.Status != nil {
		params.Status = strings.TrimSpace(*req.Status)
	}

	updatedMeta, err := repository.FormatMetadataWithLockedFields(existing.Metadata, lockedFields)
	if err == nil {
		params.Metadata = updatedMeta
	}

	updated, err := uc.repo.UpdateDevice(ctx, params)
	if err != nil {
		return nil, err
	}

	// Publish device.updated event
	if uc.eventBus != nil {
		evt := eventbus.NewBaseEvent("device.updated", map[string]string{
			"device_id": updated.ID.String(),
			"hostname":  updated.Hostname,
		})
		_ = uc.eventBus.Publish(ctx, evt)
	}

	return uc.mapDeviceToResponse(updated), nil
}

// SoftDeleteDevice soft-deletes a device and emits 'device.deleted'.
func (uc *DefaultInventoryUseCase) SoftDeleteDevice(ctx context.Context, idStr string) error {
	deviceID, err := uuid.Parse(idStr)
	if err != nil {
		return ErrInvalidUUID
	}

	device, err := uc.repo.GetDeviceByID(ctx, deviceID, false)
	if err != nil {
		return err
	}

	if err := uc.repo.SoftDeleteDevice(ctx, deviceID); err != nil {
		return err
	}

	// Publish device.deleted event
	if uc.eventBus != nil {
		evt := eventbus.NewBaseEvent("device.deleted", map[string]string{
			"device_id": device.ID.String(),
			"hostname":  device.Hostname,
		})
		_ = uc.eventBus.Publish(ctx, evt)
	}

	return nil
}

// ListStagingDevices returns paginated items in the staging queue.
func (uc *DefaultInventoryUseCase) ListStagingDevices(ctx context.Context, status string, page, perPage int32) ([]dto.StagingDeviceResponse, int64, error) {
	if status == "" {
		status = "pending"
	}
	if page < 1 {
		page = 1
	}
	if perPage < 1 || perPage > 100 {
		perPage = 50
	}
	offset := (page - 1) * perPage

	items, total, err := uc.repo.ListStagingDevices(ctx, status, perPage, offset)
	if err != nil {
		return nil, 0, err
	}

	responses := make([]dto.StagingDeviceResponse, len(items))
	for i, st := range items {
		responses[i] = dto.StagingDeviceResponse{
			ID:           st.ID.String(),
			Hostname:     st.Hostname,
			IPAddress:    repository.MapInetToString(st.IpAddress),
			MACAddress:   repository.MapMacToString(st.MacAddress),
			Manufacturer: st.Manufacturer.String,
			Model:        st.Model.String,
			DeviceType:   st.DeviceType,
			Status:       st.Status,
			CreatedAt:    st.CreatedAt.Time,
		}
	}

	return responses, total, nil
}

// ApproveStagingDevice promotes a staged device into active inventory and emits 'device.approved'.
func (uc *DefaultInventoryUseCase) ApproveStagingDevice(ctx context.Context, stagingIDStr string) (*dto.DeviceResponse, error) {
	stagingID, err := uuid.Parse(stagingIDStr)
	if err != nil {
		return nil, ErrInvalidUUID
	}

	staged, err := uc.repo.GetStagingDeviceByID(ctx, stagingID)
	if err != nil {
		return nil, err
	}

	if staged.Status != "pending" {
		return nil, fmt.Errorf("staging device is already %s", staged.Status)
	}

	// Create active device from staged data
	newDeviceReq := dto.CreateDeviceRequest{
		Hostname:     staged.Hostname,
		IPAddress:    repository.MapInetToString(staged.IpAddress),
		MACAddress:   repository.MapMacToString(staged.MacAddress),
		Manufacturer: staged.Manufacturer.String,
		Model:        staged.Model.String,
		DeviceType:   staged.DeviceType,
	}

	device, err := uc.CreateDevice(ctx, newDeviceReq)
	if err != nil {
		return nil, err
	}

	// Mark staging item approved
	if err := uc.repo.UpdateStagingDeviceStatus(ctx, stagingID, "approved"); err != nil {
		return nil, err
	}

	// Publish device.approved event
	if uc.eventBus != nil {
		evt := eventbus.NewBaseEvent("device.approved", map[string]string{
			"staging_id": staged.ID.String(),
			"device_id":  device.ID,
			"hostname":   device.Hostname,
		})
		_ = uc.eventBus.Publish(ctx, evt)
	}

	return device, nil
}

// DismissStagingDevice rejects a staged item and marks it 'dismissed'.
func (uc *DefaultInventoryUseCase) DismissStagingDevice(ctx context.Context, stagingIDStr string) error {
	stagingID, err := uuid.Parse(stagingIDStr)
	if err != nil {
		return ErrInvalidUUID
	}

	staged, err := uc.repo.GetStagingDeviceByID(ctx, stagingID)
	if err != nil {
		return err
	}

	if err := uc.repo.UpdateStagingDeviceStatus(ctx, stagingID, "dismissed"); err != nil {
		return err
	}

	if uc.eventBus != nil {
		evt := eventbus.NewBaseEvent("device.dismissed", map[string]string{
			"staging_id": staged.ID.String(),
			"hostname":   staged.Hostname,
		})
		_ = uc.eventBus.Publish(ctx, evt)
	}

	return nil
}

// CreateSubnet registers a new subnet.
func (uc *DefaultInventoryUseCase) CreateSubnet(ctx context.Context, req dto.CreateSubnetRequest) (*dto.SubnetResponse, error) {
	subnetID := uuid.New()

	params := db.CreateSubnetParams{
		ID:               subnetID,
		Name:             strings.TrimSpace(req.Name),
		DiscoveryEnabled: req.DiscoveryEnabled,
	}

	if req.CIDR != "" {
		if parsedCIDR, err := netip.ParsePrefix(strings.TrimSpace(req.CIDR)); err == nil {
			params.Cidr = parsedCIDR
		}
	}

	if req.Description != "" {
		params.Description = pgtype.Text{String: strings.TrimSpace(req.Description), Valid: true}
	}
	if req.VLANID != nil {
		params.VlanID = pgtype.Int4{Int32: *req.VLANID, Valid: true}
	}
	if req.GatewayIP != "" {
		if parsedGateway, err := netip.ParseAddr(strings.TrimSpace(req.GatewayIP)); err == nil {
			params.GatewayIp = &parsedGateway
		}
	}

	subnet, err := uc.repo.CreateSubnet(ctx, params)
	if err != nil {
		return nil, err
	}

	resp := &dto.SubnetResponse{
		ID:               subnet.ID.String(),
		Name:             subnet.Name,
		CIDR:             subnet.Cidr.String(),
		GatewayIP:        repository.MapInetToString(subnet.GatewayIp),
		Description:      subnet.Description.String,
		DiscoveryEnabled: subnet.DiscoveryEnabled,
		CreatedAt:        subnet.CreatedAt.Time,
	}
	if subnet.VlanID.Valid {
		vlan := subnet.VlanID.Int32
		resp.VLANID = &vlan
	}
	return resp, nil
}

// ListSubnets lists all configured subnets.
func (uc *DefaultInventoryUseCase) ListSubnets(ctx context.Context) ([]dto.SubnetResponse, error) {
	subnets, err := uc.repo.ListSubnets(ctx)
	if err != nil {
		return nil, err
	}

	responses := make([]dto.SubnetResponse, len(subnets))
	for i, s := range subnets {
		responses[i] = dto.SubnetResponse{
			ID:               s.ID.String(),
			Name:             s.Name,
			CIDR:             s.Cidr.String(),
			GatewayIP:        repository.MapInetToString(s.GatewayIp),
			Description:      s.Description.String,
			DiscoveryEnabled: s.DiscoveryEnabled,
			CreatedAt:        s.CreatedAt.Time,
		}
		if s.VlanID.Valid {
			vlan := s.VlanID.Int32
			responses[i].VLANID = &vlan
		}
	}

	return responses, nil
}

func (uc *DefaultInventoryUseCase) mapDeviceToResponse(device *db.Device) *dto.DeviceResponse {
	if device == nil {
		return nil
	}

	lockedFields := repository.ExtractUserLockedFields(device.Metadata)

	return &dto.DeviceResponse{
		ID:               device.ID.String(),
		Hostname:         device.Hostname,
		IPAddress:        repository.MapInetToString(device.IpAddress),
		MACAddress:       repository.MapMacToString(device.MacAddress),
		Manufacturer:     device.Manufacturer.String,
		Model:            device.Model.String,
		SerialNumber:     device.SerialNumber.String,
		DeviceType:       device.DeviceType,
		Status:           device.Status,
		FirstSeenAt:      device.FirstSeenAt.Time,
		LastSeenAt:       device.LastSeenAt.Time,
		UserLockedFields: lockedFields,
		CreatedAt:        device.CreatedAt.Time,
		UpdatedAt:        device.UpdatedAt.Time,
	}
}
