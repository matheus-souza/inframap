package controller_test

import (
	"bytes"
	"context"
	"encoding/json"
	"net/http"
	"net/http/httptest"
	"testing"
	"time"

	"github.com/matheussouza/inframap/modules/inventory/controller"
	"github.com/matheussouza/inframap/modules/inventory/dto"
	"github.com/matheussouza/inframap/modules/inventory/repository"
	"github.com/matheussouza/inframap/modules/inventory/usecase"
)

type mockInventoryUseCase struct {
	createDeviceResp  *dto.DeviceResponse
	createDeviceErr   error
	getDeviceResp     *dto.DeviceResponse
	getDeviceErr      error
	updateDeviceResp  *dto.DeviceResponse
	updateDeviceErr   error
	listDevicesResp   []dto.DeviceResponse
	listDevicesTotal  int64
	listDevicesErr    error
	deleteDeviceErr   error
	stagingResp       []dto.StagingDeviceResponse
	approveResp       *dto.DeviceResponse
	approveErr        error
	dismissErr        error
	subnetResp        *dto.SubnetResponse
	subnetErr         error
	listSubnetsResp   []dto.SubnetResponse
}

func (m *mockInventoryUseCase) CreateDevice(_ context.Context, _ dto.CreateDeviceRequest) (*dto.DeviceResponse, error) {
	if m.createDeviceErr != nil {
		return nil, m.createDeviceErr
	}
	return m.createDeviceResp, nil
}

func (m *mockInventoryUseCase) GetDeviceByID(_ context.Context, _ string, _ bool) (*dto.DeviceResponse, error) {
	if m.getDeviceErr != nil {
		return nil, m.getDeviceErr
	}
	return m.getDeviceResp, nil
}

func (m *mockInventoryUseCase) ListDevices(_ context.Context, _, _ string, _, _ int32, _ bool) ([]dto.DeviceResponse, int64, error) {
	if m.listDevicesErr != nil {
		return nil, 0, m.listDevicesErr
	}
	return m.listDevicesResp, m.listDevicesTotal, nil
}

func (m *mockInventoryUseCase) UpdateDevice(_ context.Context, _ string, _ dto.UpdateDeviceRequest) (*dto.DeviceResponse, error) {
	if m.updateDeviceErr != nil {
		return nil, m.updateDeviceErr
	}
	return m.updateDeviceResp, nil
}

func (m *mockInventoryUseCase) SoftDeleteDevice(_ context.Context, _ string) error {
	return m.deleteDeviceErr
}

func (m *mockInventoryUseCase) ListStagingDevices(_ context.Context, _ string, _, _ int32) ([]dto.StagingDeviceResponse, int64, error) {
	return m.stagingResp, int64(len(m.stagingResp)), nil
}

func (m *mockInventoryUseCase) ApproveStagingDevice(_ context.Context, _ string) (*dto.DeviceResponse, error) {
	if m.approveErr != nil {
		return nil, m.approveErr
	}
	return m.approveResp, nil
}

func (m *mockInventoryUseCase) DismissStagingDevice(_ context.Context, _ string) error {
	return m.dismissErr
}

func (m *mockInventoryUseCase) CreateSubnet(_ context.Context, _ dto.CreateSubnetRequest) (*dto.SubnetResponse, error) {
	if m.subnetErr != nil {
		return nil, m.subnetErr
	}
	return m.subnetResp, nil
}

func (m *mockInventoryUseCase) ListSubnets(_ context.Context) ([]dto.SubnetResponse, error) {
	return m.listSubnetsResp, nil
}

func TestInventoryController_Unit(t *testing.T) {
	mockUC := &mockInventoryUseCase{}
	ctrl := controller.NewInventoryController(mockUC)

	t.Run("CreateDevice Validation Failure", func(t *testing.T) {
		payload := dto.CreateDeviceRequest{Hostname: ""}
		body, _ := json.Marshal(payload)
		req := httptest.NewRequest(http.MethodPost, "/api/v1/devices", bytes.NewReader(body))
		w := httptest.NewRecorder()

		ctrl.CreateDevice(w, req)

		if w.Code != http.StatusBadRequest {
			t.Errorf("expected 400 Bad Request, got %d", w.Code)
		}
	})

	t.Run("CreateDevice Success", func(t *testing.T) {
		mockUC.createDeviceErr = nil
		mockUC.createDeviceResp = &dto.DeviceResponse{
			ID:         "dev-123",
			Hostname:   "switch-01",
			DeviceType: "switch",
			CreatedAt:  time.Now(),
		}

		payload := dto.CreateDeviceRequest{Hostname: "switch-01", DeviceType: "switch"}
		body, _ := json.Marshal(payload)
		req := httptest.NewRequest(http.MethodPost, "/api/v1/devices", bytes.NewReader(body))
		w := httptest.NewRecorder()

		ctrl.CreateDevice(w, req)

		if w.Code != http.StatusCreated {
			t.Errorf("expected 201 Created, got %d", w.Code)
		}
	})

	t.Run("ListDevices Success", func(t *testing.T) {
		mockUC.listDevicesResp = []dto.DeviceResponse{
			{ID: "dev-1", Hostname: "pve1"},
		}
		mockUC.listDevicesTotal = 1

		req := httptest.NewRequest(http.MethodGet, "/api/v1/devices?page=1&per_page=10", nil)
		w := httptest.NewRecorder()

		ctrl.ListDevices(w, req)

		if w.Code != http.StatusOK {
			t.Errorf("expected 200 OK, got %d", w.Code)
		}
	})

	t.Run("GetDeviceByID Invalid UUID", func(t *testing.T) {
		mockUC.getDeviceErr = usecase.ErrInvalidUUID
		req := httptest.NewRequest(http.MethodGet, "/api/v1/devices/invalid-uuid", nil)
		req.SetPathValue("id", "invalid-uuid")
		w := httptest.NewRecorder()

		ctrl.GetDeviceByID(w, req)

		if w.Code != http.StatusBadRequest {
			t.Errorf("expected 400 Bad Request, got %d", w.Code)
		}
	})

	t.Run("GetDeviceByID Success", func(t *testing.T) {
		mockUC.getDeviceErr = nil
		mockUC.getDeviceResp = &dto.DeviceResponse{ID: "0198a123-4567-7890-abcd-ef1234567890", Hostname: "pve-01"}

		req := httptest.NewRequest(http.MethodGet, "/api/v1/devices/0198a123-4567-7890-abcd-ef1234567890", nil)
		req.SetPathValue("id", "0198a123-4567-7890-abcd-ef1234567890")
		w := httptest.NewRecorder()

		ctrl.GetDeviceByID(w, req)

		if w.Code != http.StatusOK {
			t.Errorf("expected 200 OK, got %d", w.Code)
		}
	})

	t.Run("GetDeviceByID NotFound", func(t *testing.T) {
		mockUC.getDeviceErr = repository.ErrDeviceNotFound
		req := httptest.NewRequest(http.MethodGet, "/api/v1/devices/0198a123-0000-0000-0000-000000000000", nil)
		req.SetPathValue("id", "0198a123-0000-0000-0000-000000000000")
		w := httptest.NewRecorder()

		ctrl.GetDeviceByID(w, req)

		if w.Code != http.StatusNotFound {
			t.Errorf("expected 404 Not Found, got %d", w.Code)
		}
	})

	t.Run("DeleteDevice Success", func(t *testing.T) {
		mockUC.deleteDeviceErr = nil
		req := httptest.NewRequest(http.MethodDelete, "/api/v1/devices/0198a123-4567-7890-abcd-ef1234567890", nil)
		req.SetPathValue("id", "0198a123-4567-7890-abcd-ef1234567890")
		w := httptest.NewRecorder()

		ctrl.DeleteDevice(w, req)

		if w.Code != http.StatusOK {
			t.Errorf("expected 200 OK, got %d", w.Code)
		}
	})

	t.Run("ListStagingDevices Success", func(t *testing.T) {
		mockUC.stagingResp = []dto.StagingDeviceResponse{{ID: "st-1", Hostname: "staged-dev"}}
		req := httptest.NewRequest(http.MethodGet, "/api/v1/devices/staging", nil)
		w := httptest.NewRecorder()

		ctrl.ListStagingDevices(w, req)

		if w.Code != http.StatusOK {
			t.Errorf("expected 200 OK, got %d", w.Code)
		}
	})

	t.Run("ApproveStagingDevice Success", func(t *testing.T) {
		mockUC.approveErr = nil
		mockUC.approveResp = &dto.DeviceResponse{ID: "dev-approved", Hostname: "approved-host"}

		req := httptest.NewRequest(http.MethodPost, "/api/v1/devices/staging/st-123/approve", nil)
		req.SetPathValue("id", "st-123")
		w := httptest.NewRecorder()

		ctrl.ApproveStagingDevice(w, req)

		if w.Code != http.StatusOK {
			t.Errorf("expected 200 OK, got %d", w.Code)
		}
	})

	t.Run("DismissStagingDevice Success", func(t *testing.T) {
		mockUC.dismissErr = nil
		req := httptest.NewRequest(http.MethodPost, "/api/v1/devices/staging/st-123/dismiss", nil)
		req.SetPathValue("id", "st-123")
		w := httptest.NewRecorder()

		ctrl.DismissStagingDevice(w, req)

		if w.Code != http.StatusOK {
			t.Errorf("expected 200 OK, got %d", w.Code)
		}
	})

	t.Run("CreateSubnet Success", func(t *testing.T) {
		mockUC.subnetErr = nil
		mockUC.subnetResp = &dto.SubnetResponse{ID: "sub-1", Name: "LAN", CIDR: "192.168.1.0/24"}

		payload := dto.CreateSubnetRequest{Name: "LAN", CIDR: "192.168.1.0/24"}
		body, _ := json.Marshal(payload)
		req := httptest.NewRequest(http.MethodPost, "/api/v1/subnets", bytes.NewReader(body))
		w := httptest.NewRecorder()

		ctrl.CreateSubnet(w, req)

		if w.Code != http.StatusCreated {
			t.Errorf("expected 201 Created, got %d", w.Code)
		}
	})

	t.Run("ListSubnets Success", func(t *testing.T) {
		mockUC.listSubnetsResp = []dto.SubnetResponse{{ID: "sub-1", Name: "LAN", CIDR: "192.168.1.0/24"}}
		req := httptest.NewRequest(http.MethodGet, "/api/v1/subnets", nil)
		w := httptest.NewRecorder()

		ctrl.ListSubnets(w, req)

		if w.Code != http.StatusOK {
			t.Errorf("expected 200 OK, got %d", w.Code)
		}
	})
}
