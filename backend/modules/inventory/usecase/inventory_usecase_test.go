package usecase_test

import (
	"context"
	"errors"
	"testing"
	"time"

	"github.com/google/uuid"
	"github.com/jackc/pgx/v5/pgtype"
	"github.com/matheussouza/inframap/internal/platform/db"
	"github.com/matheussouza/inframap/modules/inventory/dto"
	"github.com/matheussouza/inframap/modules/inventory/repository"
	"github.com/matheussouza/inframap/modules/inventory/usecase"
)

type mockInventoryRepository struct {
	devices  map[uuid.UUID]*db.Device
	staging  map[uuid.UUID]*db.DeviceStaging
	subnets  map[uuid.UUID]*db.Subnet
	errToRet error
}

func newMockInventoryRepository() *mockInventoryRepository {
	return &mockInventoryRepository{
		devices: make(map[uuid.UUID]*db.Device),
		staging: make(map[uuid.UUID]*db.DeviceStaging),
		subnets: make(map[uuid.UUID]*db.Subnet),
	}
}

func (m *mockInventoryRepository) CreateDevice(_ context.Context, params db.CreateDeviceParams) (*db.Device, error) {
	if m.errToRet != nil {
		return nil, m.errToRet
	}
	d := &db.Device{
		ID:           params.ID,
		Hostname:     params.Hostname,
		IpAddress:    params.IpAddress,
		MacAddress:   params.MacAddress,
		Manufacturer: params.Manufacturer,
		Model:        params.Model,
		SerialNumber: params.SerialNumber,
		DeviceType:   params.DeviceType,
		Status:       params.Status,
		Metadata:     params.Metadata,
		CreatedAt:    pgtype.Timestamptz{Time: time.Now(), Valid: true},
		UpdatedAt:    pgtype.Timestamptz{Time: time.Now(), Valid: true},
	}
	m.devices[params.ID] = d
	return d, nil
}

func (m *mockInventoryRepository) GetDeviceByID(_ context.Context, id uuid.UUID, _ bool) (*db.Device, error) {
	if m.errToRet != nil {
		return nil, m.errToRet
	}
	d, exists := m.devices[id]
	if !exists {
		return nil, repository.ErrDeviceNotFound
	}
	return d, nil
}

func (m *mockInventoryRepository) ListDevices(_ context.Context, _, _ string, _, _ int32, _ bool) ([]db.Device, int64, error) {
	if m.errToRet != nil {
		return nil, 0, m.errToRet
	}
	res := make([]db.Device, 0, len(m.devices))
	for _, d := range m.devices {
		res = append(res, *d)
	}
	return res, int64(len(res)), nil
}

func (m *mockInventoryRepository) UpdateDevice(_ context.Context, params db.UpdateDeviceParams) (*db.Device, error) {
	if m.errToRet != nil {
		return nil, m.errToRet
	}
	d, exists := m.devices[params.ID]
	if !exists {
		return nil, repository.ErrDeviceNotFound
	}
	d.Hostname = params.Hostname
	d.IpAddress = params.IpAddress
	d.MacAddress = params.MacAddress
	d.Manufacturer = params.Manufacturer
	d.Model = params.Model
	d.DeviceType = params.DeviceType
	d.Status = params.Status
	d.Metadata = params.Metadata
	return d, nil
}

func (m *mockInventoryRepository) SoftDeleteDevice(_ context.Context, id uuid.UUID) error {
	if m.errToRet != nil {
		return m.errToRet
	}
	delete(m.devices, id)
	return nil
}

func (m *mockInventoryRepository) CreateStagingDevice(_ context.Context, params db.CreateStagingDeviceParams) (*db.DeviceStaging, error) {
	if m.errToRet != nil {
		return nil, m.errToRet
	}
	st := &db.DeviceStaging{
		ID:         params.ID,
		Hostname:   params.Hostname,
		DeviceType: params.DeviceType,
		Status:     params.Status,
		CreatedAt:  pgtype.Timestamptz{Time: time.Now(), Valid: true},
	}
	m.staging[params.ID] = st
	return st, nil
}

func (m *mockInventoryRepository) GetStagingDeviceByID(_ context.Context, id uuid.UUID) (*db.DeviceStaging, error) {
	if m.errToRet != nil {
		return nil, m.errToRet
	}
	st, exists := m.staging[id]
	if !exists {
		return nil, repository.ErrStagingDeviceNotFound
	}
	return st, nil
}

func (m *mockInventoryRepository) ListStagingDevices(_ context.Context, _ string, _, _ int32) ([]db.DeviceStaging, int64, error) {
	if m.errToRet != nil {
		return nil, 0, m.errToRet
	}
	res := make([]db.DeviceStaging, 0, len(m.staging))
	for _, st := range m.staging {
		res = append(res, *st)
	}
	return res, int64(len(res)), nil
}

func (m *mockInventoryRepository) UpdateStagingDeviceStatus(_ context.Context, id uuid.UUID, status string) error {
	if m.errToRet != nil {
		return m.errToRet
	}
	st, exists := m.staging[id]
	if !exists {
		return repository.ErrStagingDeviceNotFound
	}
	st.Status = status
	return nil
}

func (m *mockInventoryRepository) CreateSubnet(_ context.Context, params db.CreateSubnetParams) (*db.Subnet, error) {
	if m.errToRet != nil {
		return nil, m.errToRet
	}
	sn := &db.Subnet{
		ID:               params.ID,
		Name:             params.Name,
		Description:      params.Description,
		DiscoveryEnabled: params.DiscoveryEnabled,
		CreatedAt:        pgtype.Timestamptz{Time: time.Now(), Valid: true},
	}
	m.subnets[params.ID] = sn
	return sn, nil
}

func (m *mockInventoryRepository) ListSubnets(_ context.Context) ([]db.Subnet, error) {
	if m.errToRet != nil {
		return nil, m.errToRet
	}
	res := make([]db.Subnet, 0, len(m.subnets))
	for _, sn := range m.subnets {
		res = append(res, *sn)
	}
	return res, nil
}

func TestInventoryUseCase_Unit(t *testing.T) {
	mockRepo := newMockInventoryRepository()
	uc := usecase.NewDefaultInventoryUseCase(mockRepo, nil, nil)

	t.Run("CreateDevice Success", func(t *testing.T) {
		req := dto.CreateDeviceRequest{
			Hostname:     "server-01",
			DeviceType:   "server",
			Manufacturer: "Dell",
		}
		res, err := uc.CreateDevice(context.Background(), req)
		if err != nil {
			t.Fatalf("unexpected error on CreateDevice: %v", err)
		}
		if res.Hostname != "server-01" {
			t.Errorf("expected hostname server-01, got %s", res.Hostname)
		}
	})

	t.Run("GetDeviceByID Invalid UUID", func(t *testing.T) {
		_, err := uc.GetDeviceByID(context.Background(), "invalid-uuid", false)
		if !errors.Is(err, usecase.ErrInvalidUUID) {
			t.Errorf("expected ErrInvalidUUID, got %v", err)
		}
	})

	t.Run("ListDevices Success", func(t *testing.T) {
		items, total, err := uc.ListDevices(context.Background(), "", "", 1, 10, false)
		if err != nil {
			t.Fatalf("unexpected error listing devices: %v", err)
		}
		if total < 0 {
			t.Errorf("expected total >= 0, got %d", total)
		}
		_ = items
	})

	t.Run("UpdateDevice Appends UserLockedFields", func(t *testing.T) {
		createReq := dto.CreateDeviceRequest{Hostname: "pve01", DeviceType: "hypervisor"}
		created, _ := uc.CreateDevice(context.Background(), createReq)

		newName := "pve01-renamed"
		updateReq := dto.UpdateDeviceRequest{Hostname: &newName}
		updated, err := uc.UpdateDevice(context.Background(), created.ID, updateReq)
		if err != nil {
			t.Fatalf("unexpected error on UpdateDevice: %v", err)
		}

		if updated.Hostname != "pve01-renamed" {
			t.Errorf("expected hostname pve01-renamed, got %s", updated.Hostname)
		}

		if len(updated.UserLockedFields) != 1 || updated.UserLockedFields[0] != "hostname" {
			t.Errorf("expected user_locked_fields ['hostname'], got %v", updated.UserLockedFields)
		}
	})

	t.Run("UpdateDevice InvalidIP returns ErrInvalidInput", func(t *testing.T) {
		createReq := dto.CreateDeviceRequest{Hostname: "ip-test", DeviceType: "server"}
		created, _ := uc.CreateDevice(context.Background(), createReq)

		badIP := "not-an-ip"
		updateReq := dto.UpdateDeviceRequest{IPAddress: &badIP}
		_, err := uc.UpdateDevice(context.Background(), created.ID, updateReq)
		if !errors.Is(err, usecase.ErrInvalidInput) {
			t.Errorf("expected ErrInvalidInput for invalid IP, got %v", err)
		}
	})

	t.Run("UpdateDevice InvalidMAC returns ErrInvalidInput", func(t *testing.T) {
		createReq := dto.CreateDeviceRequest{Hostname: "mac-test", DeviceType: "server"}
		created, _ := uc.CreateDevice(context.Background(), createReq)

		badMAC := "ZZ:ZZ:ZZ:ZZ:ZZ:ZZ"
		updateReq := dto.UpdateDeviceRequest{MACAddress: &badMAC}
		_, err := uc.UpdateDevice(context.Background(), created.ID, updateReq)
		if !errors.Is(err, usecase.ErrInvalidInput) {
			t.Errorf("expected ErrInvalidInput for invalid MAC, got %v", err)
		}
	})

	t.Run("UpdateDevice ValidIP succeeds", func(t *testing.T) {
		createReq := dto.CreateDeviceRequest{Hostname: "valid-ip-test", DeviceType: "server"}
		created, _ := uc.CreateDevice(context.Background(), createReq)

		validIP := "192.168.1.100"
		updateReq := dto.UpdateDeviceRequest{IPAddress: &validIP}
		updated, err := uc.UpdateDevice(context.Background(), created.ID, updateReq)
		if err != nil {
			t.Fatalf("unexpected error: %v", err)
		}
		if updated.IPAddress != "192.168.1.100" {
			t.Errorf("expected IP 192.168.1.100, got %s", updated.IPAddress)
		}
	})

	t.Run("SoftDeleteDevice Success", func(t *testing.T) {
		createReq := dto.CreateDeviceRequest{Hostname: "to-delete", DeviceType: "server"}
		created, _ := uc.CreateDevice(context.Background(), createReq)

		err := uc.SoftDeleteDevice(context.Background(), created.ID)
		if err != nil {
			t.Fatalf("unexpected error soft deleting device: %v", err)
		}
	})

	t.Run("ApproveStagingDevice Promotes to Active Inventory", func(t *testing.T) {
		stID := uuid.New()
		mockRepo.staging[stID] = &db.DeviceStaging{
			ID:         stID,
			Hostname:   "nas-staged",
			DeviceType: "storage",
			Status:     "pending",
		}

		device, err := uc.ApproveStagingDevice(context.Background(), stID.String())
		if err != nil {
			t.Fatalf("unexpected error approving staging device: %v", err)
		}

		if device.Hostname != "nas-staged" {
			t.Errorf("expected hostname nas-staged, got %s", device.Hostname)
		}

		if mockRepo.staging[stID].Status != "approved" {
			t.Errorf("expected staging status approved, got %s", mockRepo.staging[stID].Status)
		}
	})

	t.Run("DismissStagingDevice Success", func(t *testing.T) {
		stID := uuid.New()
		mockRepo.staging[stID] = &db.DeviceStaging{
			ID:       stID,
			Hostname: "dismissed-dev",
			Status:   "pending",
		}

		err := uc.DismissStagingDevice(context.Background(), stID.String())
		if err != nil {
			t.Fatalf("unexpected error dismissing staging device: %v", err)
		}

		if mockRepo.staging[stID].Status != "dismissed" {
			t.Errorf("expected status dismissed, got %s", mockRepo.staging[stID].Status)
		}
	})

	t.Run("CreateSubnet Success", func(t *testing.T) {
		req := dto.CreateSubnetRequest{
			Name:             "Management VLAN",
			CIDR:             "10.0.0.0/24",
			DiscoveryEnabled: true,
		}
		res, err := uc.CreateSubnet(context.Background(), req)
		if err != nil {
			t.Fatalf("unexpected error creating subnet: %v", err)
		}
		if res.Name != "Management VLAN" {
			t.Errorf("expected subnet name Management VLAN, got %s", res.Name)
		}
	})

	t.Run("ListSubnets Success", func(t *testing.T) {
		subnets, err := uc.ListSubnets(context.Background())
		if err != nil {
			t.Fatalf("unexpected error listing subnets: %v", err)
		}
		if len(subnets) < 1 {
			t.Error("expected at least 1 subnet")
		}
	})
}
