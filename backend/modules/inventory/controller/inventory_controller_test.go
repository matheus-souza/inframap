package controller_test

import (
	"bytes"
	"context"
	"encoding/json"
	"errors"
	"net/http"
	"net/http/httptest"
	"testing"
	"time"

	"github.com/matheussouza/inframap/modules/inventory/controller"
	"github.com/matheussouza/inframap/modules/inventory/dto"
	"github.com/matheussouza/inframap/modules/inventory/repository"
	"github.com/matheussouza/inframap/modules/inventory/usecase"
)

func strPtr(s string) *string { return &s }

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

	t.Run("UpdateDevice Success", func(t *testing.T) {
		mockUC.updateDeviceErr = nil
		mockUC.updateDeviceResp = &dto.DeviceResponse{ID: "dev-123", Hostname: "updated-host"}

		payload := dto.UpdateDeviceRequest{Hostname: strPtr("updated-host")}
		body, _ := json.Marshal(payload)
		req := httptest.NewRequest(http.MethodPut, "/api/v1/devices/dev-123", bytes.NewReader(body))
		req.SetPathValue("id", "dev-123")
		w := httptest.NewRecorder()

		ctrl.UpdateDevice(w, req)

		if w.Code != http.StatusOK {
			t.Errorf("expected 200 OK, got %d", w.Code)
		}
	})

	t.Run("UpdateDevice ErrInvalidInput", func(t *testing.T) {
		mockUC.updateDeviceErr = usecase.ErrInvalidInput
		payload := dto.UpdateDeviceRequest{IPAddress: strPtr("not-an-ip")}
		body, _ := json.Marshal(payload)
		req := httptest.NewRequest(http.MethodPut, "/api/v1/devices/dev-123", bytes.NewReader(body))
		req.SetPathValue("id", "dev-123")
		w := httptest.NewRecorder()

		ctrl.UpdateDevice(w, req)

		if w.Code != http.StatusBadRequest {
			t.Errorf("expected 400 Bad Request, got %d", w.Code)
		}
	})

	t.Run("UpdateDevice ErrInvalidUUID", func(t *testing.T) {
		mockUC.updateDeviceErr = usecase.ErrInvalidUUID
		payload := dto.UpdateDeviceRequest{Hostname: strPtr("h")}
		body, _ := json.Marshal(payload)
		req := httptest.NewRequest(http.MethodPut, "/api/v1/devices/bad-uuid", bytes.NewReader(body))
		req.SetPathValue("id", "bad-uuid")
		w := httptest.NewRecorder()

		ctrl.UpdateDevice(w, req)

		if w.Code != http.StatusBadRequest {
			t.Errorf("expected 400 Bad Request, got %d", w.Code)
		}
	})

	t.Run("UpdateDevice NotFound", func(t *testing.T) {
		mockUC.updateDeviceErr = repository.ErrDeviceNotFound
		payload := dto.UpdateDeviceRequest{Hostname: strPtr("h")}
		body, _ := json.Marshal(payload)
		req := httptest.NewRequest(http.MethodPut, "/api/v1/devices/0198a123-4567-7890-abcd-ef1234567890", bytes.NewReader(body))
		req.SetPathValue("id", "0198a123-4567-7890-abcd-ef1234567890")
		w := httptest.NewRecorder()

		ctrl.UpdateDevice(w, req)

		if w.Code != http.StatusNotFound {
			t.Errorf("expected 404 Not Found, got %d", w.Code)
		}
	})

	t.Run("UpdateDevice InternalError", func(t *testing.T) {
		mockUC.updateDeviceErr = errors.New("db error")
		payload := dto.UpdateDeviceRequest{Hostname: strPtr("h")}
		body, _ := json.Marshal(payload)
		req := httptest.NewRequest(http.MethodPut, "/api/v1/devices/dev-123", bytes.NewReader(body))
		req.SetPathValue("id", "dev-123")
		w := httptest.NewRecorder()

		ctrl.UpdateDevice(w, req)

		if w.Code != http.StatusInternalServerError {
			t.Errorf("expected 500 Internal Server Error, got %d", w.Code)
		}
	})

	t.Run("UpdateDevice InvalidJSON", func(t *testing.T) {
		req := httptest.NewRequest(http.MethodPut, "/api/v1/devices/dev-123", bytes.NewReader([]byte(`{invalid`)))
		req.SetPathValue("id", "dev-123")
		w := httptest.NewRecorder()

		ctrl.UpdateDevice(w, req)

		if w.Code != http.StatusBadRequest {
			t.Errorf("expected 400 Bad Request, got %d", w.Code)
		}
	})

	t.Run("ListDevices returns pagination envelope", func(t *testing.T) {
		mockUC.listDevicesResp = []dto.DeviceResponse{{ID: "dev-1", Hostname: "pve1"}}
		mockUC.listDevicesTotal = 42
		mockUC.listDevicesErr = nil

		req := httptest.NewRequest(http.MethodGet, "/api/v1/devices?page=2&per_page=10", nil)
		w := httptest.NewRecorder()

		ctrl.ListDevices(w, req)

		var body map[string]any
		if err := json.NewDecoder(w.Body).Decode(&body); err != nil {
			t.Fatalf("failed to decode response: %v", err)
		}
		data, ok := body["data"].(map[string]any)
		if !ok {
			t.Fatal("expected data envelope")
		}
		if data["total"] != float64(42) {
			t.Errorf("expected total 42, got %v", data["total"])
		}
		if data["page"] != float64(2) {
			t.Errorf("expected page 2, got %v", data["page"])
		}
		if data["per_page"] != float64(10) {
			t.Errorf("expected per_page 10, got %v", data["per_page"])
		}
	})

	t.Run("ListStagingDevices returns pagination envelope", func(t *testing.T) {
		mockUC.stagingResp = []dto.StagingDeviceResponse{{ID: "st-1", Hostname: "staged"}}
		req := httptest.NewRequest(http.MethodGet, "/api/v1/devices/staging?page=1&per_page=25", nil)
		w := httptest.NewRecorder()

		ctrl.ListStagingDevices(w, req)

		var body map[string]any
		if err := json.NewDecoder(w.Body).Decode(&body); err != nil {
			t.Fatalf("failed to decode response: %v", err)
		}
		data, ok := body["data"].(map[string]any)
		if !ok {
			t.Fatal("expected data envelope")
		}
		if data["page"] != float64(1) {
			t.Errorf("expected page 1, got %v", data["page"])
		}
	})

	t.Run("CreateSubnet ValidationFailure", func(t *testing.T) {
		payload := dto.CreateSubnetRequest{Name: "", CIDR: ""}
		body, _ := json.Marshal(payload)
		req := httptest.NewRequest(http.MethodPost, "/api/v1/subnets", bytes.NewReader(body))
		w := httptest.NewRecorder()

		ctrl.CreateSubnet(w, req)

		if w.Code != http.StatusBadRequest {
			t.Errorf("expected 400 Bad Request, got %d", w.Code)
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
