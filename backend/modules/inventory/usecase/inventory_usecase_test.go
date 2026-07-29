package usecase_test

import (
	"context"
	"errors"
	"log/slog"
	"net"
	"net/netip"
	"testing"
	"time"

	"github.com/google/uuid"
	"github.com/jackc/pgx/v5/pgtype"
	"github.com/matheussouza/inframap/internal/platform/db"
	"github.com/matheussouza/inframap/internal/platform/eventbus"
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
		Cidr:             params.Cidr,
		VlanID:           params.VlanID,
		GatewayIp:        params.GatewayIp,
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

	t.Run("UpdateDevice ValidMAC succeeds", func(t *testing.T) {
		createReq := dto.CreateDeviceRequest{Hostname: "valid-mac-test", DeviceType: "server"}
		created, _ := uc.CreateDevice(context.Background(), createReq)

		validMAC := "AA:BB:CC:DD:EE:FF"
		updateReq := dto.UpdateDeviceRequest{MACAddress: &validMAC}
		updated, err := uc.UpdateDevice(context.Background(), created.ID, updateReq)
		if err != nil {
			t.Fatalf("unexpected error: %v", err)
		}
		if updated.MACAddress != "aa:bb:cc:dd:ee:ff" {
			t.Errorf("expected MAC aa:bb:cc:dd:ee:ff, got %s", updated.MACAddress)
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

	t.Run("CreateSubnet with VLANID", func(t *testing.T) {
		vlanID := int32(100)
		req := dto.CreateSubnetRequest{
			Name:             "VLAN 100",
			CIDR:             "10.100.0.0/24",
			VLANID:           &vlanID,
			DiscoveryEnabled: false,
		}
		res, err := uc.CreateSubnet(context.Background(), req)
		if err != nil {
			t.Fatalf("unexpected error creating subnet with VLAN: %v", err)
		}
		if res.Name != "VLAN 100" {
			t.Errorf("expected subnet name 'VLAN 100', got %s", res.Name)
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

func TestCreateDevice_WithIPAndMAC(t *testing.T) {
	repo := newMockInventoryRepository()
	uc := usecase.NewDefaultInventoryUseCase(repo, nil, nil)

	req := dto.CreateDeviceRequest{
		Hostname:     "switch-core",
		DeviceType:   "switch",
		IPAddress:    "10.0.0.1",
		MACAddress:   "AA:BB:CC:DD:EE:FF",
		SerialNumber: "SN-12345",
	}
	res, err := uc.CreateDevice(context.Background(), req)
	if err != nil {
		t.Fatalf("unexpected error: %v", err)
	}
	if res.IPAddress != "10.0.0.1" {
		t.Errorf("expected IP 10.0.0.1, got %s", res.IPAddress)
	}
	if res.MACAddress != "aa:bb:cc:dd:ee:ff" {
		t.Errorf("expected MAC aa:bb:cc:dd:ee:ff, got %s", res.MACAddress)
	}
	if res.SerialNumber != "SN-12345" {
		t.Errorf("expected serial SN-12345, got %s", res.SerialNumber)
	}
}

func TestCreateDevice_WithEventBus(t *testing.T) {
	repo := newMockInventoryRepository()
	bus := eventbus.NewInMemoryEventBus(1, 10)
	defer func() { _ = bus.Close() }()

	var published []eventbus.DomainEvent
	_ = bus.Subscribe("device.created", func(_ context.Context, event eventbus.DomainEvent) error {
		published = append(published, event)
		return nil
	})

	logger := slog.Default()
	uc := usecase.NewDefaultInventoryUseCase(repo, bus, logger)

	req := dto.CreateDeviceRequest{Hostname: "evt-server", DeviceType: "server"}
	_, err := uc.CreateDevice(context.Background(), req)
	if err != nil {
		t.Fatalf("unexpected error: %v", err)
	}

	time.Sleep(100 * time.Millisecond)

	if len(published) != 1 {
		t.Fatalf("expected 1 event published, got %d", len(published))
	}
	if published[0].EventType() != "device.created" {
		t.Errorf("expected device.created event, got %s", published[0].EventType())
	}
}

func TestCreateDevice_RepoError(t *testing.T) {
	repo := newMockInventoryRepository()
	repo.errToRet = errors.New("db failure")
	uc := usecase.NewDefaultInventoryUseCase(repo, nil, nil)

	_, err := uc.CreateDevice(context.Background(), dto.CreateDeviceRequest{Hostname: "fail", DeviceType: "server"})
	if err == nil {
		t.Fatal("expected error from repo")
	}
}

func TestUpdateDevice_WithEventBus(t *testing.T) {
	repo := newMockInventoryRepository()
	bus := eventbus.NewInMemoryEventBus(1, 10)
	defer func() { _ = bus.Close() }()

	var published []eventbus.DomainEvent
	_ = bus.Subscribe("device.updated", func(_ context.Context, event eventbus.DomainEvent) error {
		published = append(published, event)
		return nil
	})

	uc := usecase.NewDefaultInventoryUseCase(repo, bus, slog.Default())

	created, _ := uc.CreateDevice(context.Background(), dto.CreateDeviceRequest{Hostname: "upd-test", DeviceType: "server"})
	newName := "upd-test-renamed"
	_, err := uc.UpdateDevice(context.Background(), created.ID, dto.UpdateDeviceRequest{Hostname: &newName})
	if err != nil {
		t.Fatalf("unexpected error: %v", err)
	}

	time.Sleep(100 * time.Millisecond)

	found := false
	for _, evt := range published {
		if evt.EventType() == "device.updated" {
			found = true
		}
	}
	if !found {
		t.Error("expected device.updated event to be published")
	}
}

func TestSoftDeleteDevice_WithEventBus(t *testing.T) {
	repo := newMockInventoryRepository()
	bus := eventbus.NewInMemoryEventBus(1, 10)
	defer func() { _ = bus.Close() }()

	var published []eventbus.DomainEvent
	_ = bus.Subscribe("device.deleted", func(_ context.Context, event eventbus.DomainEvent) error {
		published = append(published, event)
		return nil
	})

	uc := usecase.NewDefaultInventoryUseCase(repo, bus, slog.Default())

	created, _ := uc.CreateDevice(context.Background(), dto.CreateDeviceRequest{Hostname: "del-test", DeviceType: "server"})
	err := uc.SoftDeleteDevice(context.Background(), created.ID)
	if err != nil {
		t.Fatalf("unexpected error: %v", err)
	}

	time.Sleep(100 * time.Millisecond)

	if len(published) != 1 {
		t.Fatalf("expected 1 device.deleted event, got %d", len(published))
	}
}

func TestSoftDeleteDevice_InvalidUUID(t *testing.T) {
	uc := usecase.NewDefaultInventoryUseCase(newMockInventoryRepository(), nil, nil)
	err := uc.SoftDeleteDevice(context.Background(), "not-a-uuid")
	if !errors.Is(err, usecase.ErrInvalidUUID) {
		t.Errorf("expected ErrInvalidUUID, got %v", err)
	}
}

func TestSoftDeleteDevice_NotFound(t *testing.T) {
	uc := usecase.NewDefaultInventoryUseCase(newMockInventoryRepository(), nil, nil)
	err := uc.SoftDeleteDevice(context.Background(), uuid.New().String())
	if !errors.Is(err, repository.ErrDeviceNotFound) {
		t.Errorf("expected ErrDeviceNotFound, got %v", err)
	}
}

func TestSoftDeleteDevice_RepoError(t *testing.T) {
	repo := newMockInventoryRepository()
	uc := usecase.NewDefaultInventoryUseCase(repo, nil, nil)
	created, _ := uc.CreateDevice(context.Background(), dto.CreateDeviceRequest{Hostname: "err-del", DeviceType: "server"})
	repo.errToRet = errors.New("delete failed")
	err := uc.SoftDeleteDevice(context.Background(), created.ID)
	if err == nil {
		t.Fatal("expected error from repo")
	}
}

func TestGetDeviceByID_NotFound(t *testing.T) {
	uc := usecase.NewDefaultInventoryUseCase(newMockInventoryRepository(), nil, nil)
	_, err := uc.GetDeviceByID(context.Background(), uuid.New().String(), false)
	if !errors.Is(err, repository.ErrDeviceNotFound) {
		t.Errorf("expected ErrDeviceNotFound, got %v", err)
	}
}

func TestGetDeviceByID_RepoError(t *testing.T) {
	repo := newMockInventoryRepository()
	repo.errToRet = errors.New("db failure")
	uc := usecase.NewDefaultInventoryUseCase(repo, nil, nil)
	_, err := uc.GetDeviceByID(context.Background(), uuid.New().String(), false)
	if err == nil {
		t.Fatal("expected error from repo")
	}
}

func TestListDevices_RepoError(t *testing.T) {
	repo := newMockInventoryRepository()
	repo.errToRet = errors.New("db failure")
	uc := usecase.NewDefaultInventoryUseCase(repo, nil, nil)
	_, _, err := uc.ListDevices(context.Background(), "", "", 1, 10, false)
	if err == nil {
		t.Fatal("expected error from repo")
	}
}

func TestListDevices_DefaultsPagination(t *testing.T) {
	repo := newMockInventoryRepository()
	uc := usecase.NewDefaultInventoryUseCase(repo, nil, nil)

	_, _, err := uc.ListDevices(context.Background(), "", "", 0, 0, false)
	if err != nil {
		t.Fatalf("unexpected error: %v", err)
	}

	_, _, err = uc.ListDevices(context.Background(), "", "", -1, 200, false)
	if err != nil {
		t.Fatalf("unexpected error: %v", err)
	}
}

func TestUpdateDevice_InvalidUUID(t *testing.T) {
	uc := usecase.NewDefaultInventoryUseCase(newMockInventoryRepository(), nil, nil)
	name := "x"
	_, err := uc.UpdateDevice(context.Background(), "bad-uuid", dto.UpdateDeviceRequest{Hostname: &name})
	if !errors.Is(err, usecase.ErrInvalidUUID) {
		t.Errorf("expected ErrInvalidUUID, got %v", err)
	}
}

func TestUpdateDevice_NotFound(t *testing.T) {
	uc := usecase.NewDefaultInventoryUseCase(newMockInventoryRepository(), nil, nil)
	name := "x"
	_, err := uc.UpdateDevice(context.Background(), uuid.New().String(), dto.UpdateDeviceRequest{Hostname: &name})
	if !errors.Is(err, repository.ErrDeviceNotFound) {
		t.Errorf("expected ErrDeviceNotFound, got %v", err)
	}
}

func TestUpdateDevice_AllFieldsLocked(t *testing.T) {
	repo := newMockInventoryRepository()
	uc := usecase.NewDefaultInventoryUseCase(repo, nil, nil)

	created, _ := uc.CreateDevice(context.Background(), dto.CreateDeviceRequest{Hostname: "lock-all", DeviceType: "server"})

	hostname := "new-host"
	mfr := "Cisco"
	model := "Catalyst"
	devType := "switch"
	status := "offline"
	ip := "10.0.0.5"
	mac := "00:11:22:33:44:55"

	updated, err := uc.UpdateDevice(context.Background(), created.ID, dto.UpdateDeviceRequest{
		Hostname:     &hostname,
		Manufacturer: &mfr,
		Model:        &model,
		DeviceType:   &devType,
		Status:       &status,
		IPAddress:    &ip,
		MACAddress:   &mac,
	})
	if err != nil {
		t.Fatalf("unexpected error: %v", err)
	}

	expectedFields := map[string]bool{
		"hostname": true, "manufacturer": true, "model": true,
		"device_type": true, "ip_address": true, "mac_address": true,
	}
	for _, f := range updated.UserLockedFields {
		delete(expectedFields, f)
	}
	if len(expectedFields) > 0 {
		t.Errorf("missing locked fields: %v", expectedFields)
	}
}

func TestListStagingDevices_Success(t *testing.T) {
	repo := newMockInventoryRepository()
	uc := usecase.NewDefaultInventoryUseCase(repo, nil, nil)

	stID := uuid.New()
	repo.staging[stID] = &db.DeviceStaging{
		ID:         stID,
		Hostname:   "staged-dev",
		DeviceType: "router",
		Status:     "pending",
		CreatedAt:  pgtype.Timestamptz{Time: time.Now(), Valid: true},
	}

	items, total, err := uc.ListStagingDevices(context.Background(), "", 1, 50)
	if err != nil {
		t.Fatalf("unexpected error: %v", err)
	}
	if total != 1 {
		t.Errorf("expected total 1, got %d", total)
	}
	if items[0].Hostname != "staged-dev" {
		t.Errorf("expected hostname staged-dev, got %s", items[0].Hostname)
	}
}

func TestListStagingDevices_DefaultsPagination(t *testing.T) {
	repo := newMockInventoryRepository()
	uc := usecase.NewDefaultInventoryUseCase(repo, nil, nil)

	_, _, err := uc.ListStagingDevices(context.Background(), "", 0, 0)
	if err != nil {
		t.Fatalf("unexpected error: %v", err)
	}

	_, _, err = uc.ListStagingDevices(context.Background(), "", -1, 200)
	if err != nil {
		t.Fatalf("unexpected error: %v", err)
	}
}

func TestListStagingDevices_RepoError(t *testing.T) {
	repo := newMockInventoryRepository()
	repo.errToRet = errors.New("db failure")
	uc := usecase.NewDefaultInventoryUseCase(repo, nil, nil)
	_, _, err := uc.ListStagingDevices(context.Background(), "pending", 1, 50)
	if err == nil {
		t.Fatal("expected error from repo")
	}
}

func TestApproveStagingDevice_InvalidUUID(t *testing.T) {
	uc := usecase.NewDefaultInventoryUseCase(newMockInventoryRepository(), nil, nil)
	_, err := uc.ApproveStagingDevice(context.Background(), "bad")
	if !errors.Is(err, usecase.ErrInvalidUUID) {
		t.Errorf("expected ErrInvalidUUID, got %v", err)
	}
}

func TestApproveStagingDevice_NotFound(t *testing.T) {
	uc := usecase.NewDefaultInventoryUseCase(newMockInventoryRepository(), nil, nil)
	_, err := uc.ApproveStagingDevice(context.Background(), uuid.New().String())
	if !errors.Is(err, repository.ErrStagingDeviceNotFound) {
		t.Errorf("expected ErrStagingDeviceNotFound, got %v", err)
	}
}

func TestApproveStagingDevice_AlreadyApproved(t *testing.T) {
	repo := newMockInventoryRepository()
	uc := usecase.NewDefaultInventoryUseCase(repo, nil, nil)

	stID := uuid.New()
	repo.staging[stID] = &db.DeviceStaging{
		ID:       stID,
		Hostname: "already-done",
		Status:   "approved",
	}

	_, err := uc.ApproveStagingDevice(context.Background(), stID.String())
	if err == nil {
		t.Fatal("expected error for non-pending staging device")
	}
	if err.Error() != "staging device is already approved" {
		t.Errorf("unexpected error message: %s", err.Error())
	}
}

func TestApproveStagingDevice_WithEventBus(t *testing.T) {
	repo := newMockInventoryRepository()
	bus := eventbus.NewInMemoryEventBus(1, 10)
	defer func() { _ = bus.Close() }()

	var approved []eventbus.DomainEvent
	_ = bus.Subscribe("device.approved", func(_ context.Context, event eventbus.DomainEvent) error {
		approved = append(approved, event)
		return nil
	})

	uc := usecase.NewDefaultInventoryUseCase(repo, bus, slog.Default())

	stID := uuid.New()
	repo.staging[stID] = &db.DeviceStaging{
		ID:         stID,
		Hostname:   "evt-staged",
		DeviceType: "server",
		Status:     "pending",
	}

	_, err := uc.ApproveStagingDevice(context.Background(), stID.String())
	if err != nil {
		t.Fatalf("unexpected error: %v", err)
	}

	time.Sleep(100 * time.Millisecond)

	foundApproved := false
	for _, evt := range approved {
		if evt.EventType() == "device.approved" {
			foundApproved = true
		}
	}
	if !foundApproved {
		t.Error("expected device.approved event to be published")
	}
}

func TestDismissStagingDevice_InvalidUUID(t *testing.T) {
	uc := usecase.NewDefaultInventoryUseCase(newMockInventoryRepository(), nil, nil)
	err := uc.DismissStagingDevice(context.Background(), "bad")
	if !errors.Is(err, usecase.ErrInvalidUUID) {
		t.Errorf("expected ErrInvalidUUID, got %v", err)
	}
}

func TestDismissStagingDevice_NotFound(t *testing.T) {
	uc := usecase.NewDefaultInventoryUseCase(newMockInventoryRepository(), nil, nil)
	err := uc.DismissStagingDevice(context.Background(), uuid.New().String())
	if !errors.Is(err, repository.ErrStagingDeviceNotFound) {
		t.Errorf("expected ErrStagingDeviceNotFound, got %v", err)
	}
}

func TestDismissStagingDevice_WithEventBus(t *testing.T) {
	repo := newMockInventoryRepository()
	bus := eventbus.NewInMemoryEventBus(1, 10)
	defer func() { _ = bus.Close() }()

	var dismissed []eventbus.DomainEvent
	_ = bus.Subscribe("device.dismissed", func(_ context.Context, event eventbus.DomainEvent) error {
		dismissed = append(dismissed, event)
		return nil
	})

	uc := usecase.NewDefaultInventoryUseCase(repo, bus, slog.Default())

	stID := uuid.New()
	repo.staging[stID] = &db.DeviceStaging{
		ID:       stID,
		Hostname: "dismiss-evt",
		Status:   "pending",
	}

	err := uc.DismissStagingDevice(context.Background(), stID.String())
	if err != nil {
		t.Fatalf("unexpected error: %v", err)
	}

	time.Sleep(100 * time.Millisecond)

	if len(dismissed) != 1 {
		t.Fatalf("expected 1 device.dismissed event, got %d", len(dismissed))
	}
}

func TestCreateSubnet_WithAllOptionalFields(t *testing.T) {
	repo := newMockInventoryRepository()
	uc := usecase.NewDefaultInventoryUseCase(repo, nil, nil)

	vlanID := int32(42)
	req := dto.CreateSubnetRequest{
		Name:             "Full Subnet",
		CIDR:             "172.16.0.0/16",
		VLANID:           &vlanID,
		GatewayIP:        "172.16.0.1",
		Description:      "Test subnet with all fields",
		DiscoveryEnabled: true,
	}

	res, err := uc.CreateSubnet(context.Background(), req)
	if err != nil {
		t.Fatalf("unexpected error: %v", err)
	}
	if res.Name != "Full Subnet" {
		t.Errorf("expected name Full Subnet, got %s", res.Name)
	}
	if res.GatewayIP != "172.16.0.1" {
		t.Errorf("expected gateway 172.16.0.1, got %s", res.GatewayIP)
	}
	if res.Description != "Test subnet with all fields" {
		t.Errorf("expected description, got %s", res.Description)
	}
	if res.VLANID == nil || *res.VLANID != 42 {
		t.Errorf("expected VLAN 42, got %v", res.VLANID)
	}
}

func TestCreateSubnet_RepoError(t *testing.T) {
	repo := newMockInventoryRepository()
	repo.errToRet = errors.New("db failure")
	uc := usecase.NewDefaultInventoryUseCase(repo, nil, nil)

	_, err := uc.CreateSubnet(context.Background(), dto.CreateSubnetRequest{Name: "fail", CIDR: "10.0.0.0/24"})
	if err == nil {
		t.Fatal("expected error from repo")
	}
}

func TestListSubnets_WithVLAN(t *testing.T) {
	repo := newMockInventoryRepository()
	uc := usecase.NewDefaultInventoryUseCase(repo, nil, nil)

	subID := uuid.New()
	repo.subnets[subID] = &db.Subnet{
		ID:               subID,
		Name:             "VLAN Sub",
		Cidr:             netip.MustParsePrefix("10.0.0.0/24"),
		VlanID:           pgtype.Int4{Int32: 100, Valid: true},
		DiscoveryEnabled: true,
		CreatedAt:        pgtype.Timestamptz{Time: time.Now(), Valid: true},
	}

	subnets, err := uc.ListSubnets(context.Background())
	if err != nil {
		t.Fatalf("unexpected error: %v", err)
	}
	if len(subnets) != 1 {
		t.Fatalf("expected 1 subnet, got %d", len(subnets))
	}
	if subnets[0].VLANID == nil || *subnets[0].VLANID != 100 {
		t.Errorf("expected VLAN 100, got %v", subnets[0].VLANID)
	}
}

func TestListSubnets_RepoError(t *testing.T) {
	repo := newMockInventoryRepository()
	repo.errToRet = errors.New("db failure")
	uc := usecase.NewDefaultInventoryUseCase(repo, nil, nil)
	_, err := uc.ListSubnets(context.Background())
	if err == nil {
		t.Fatal("expected error from repo")
	}
}

func TestMapDeviceToResponse_WithOptionalFields(t *testing.T) {
	repo := newMockInventoryRepository()
	uc := usecase.NewDefaultInventoryUseCase(repo, nil, nil)

	ip := netip.MustParseAddr("192.168.1.10")
	mac, _ := net.ParseMAC("00:11:22:33:44:55")

	req := dto.CreateDeviceRequest{
		Hostname:     "full-device",
		DeviceType:   "router",
		IPAddress:    "192.168.1.10",
		MACAddress:   "00:11:22:33:44:55",
		Manufacturer: "Ubiquiti",
		Model:        "EdgeRouter",
		SerialNumber: "SN-999",
	}

	res, err := uc.CreateDevice(context.Background(), req)
	if err != nil {
		t.Fatalf("unexpected error: %v", err)
	}

	if res.IPAddress != ip.String() {
		t.Errorf("expected IP %s, got %s", ip.String(), res.IPAddress)
	}
	if res.MACAddress != mac.String() {
		t.Errorf("expected MAC %s, got %s", mac.String(), res.MACAddress)
	}
	if res.Manufacturer != "Ubiquiti" {
		t.Errorf("expected manufacturer Ubiquiti, got %s", res.Manufacturer)
	}
	if res.Model != "EdgeRouter" {
		t.Errorf("expected model EdgeRouter, got %s", res.Model)
	}
}
