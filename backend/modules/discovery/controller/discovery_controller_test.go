package controller_test

import (
	"bytes"
	"context"
	"encoding/json"
	"errors"
	"net/http"
	"net/http/httptest"
	"testing"

	"github.com/google/uuid"
	"github.com/matheussouza/inframap/modules/discovery/controller"
	"github.com/matheussouza/inframap/modules/discovery/dto"
	"github.com/matheussouza/inframap/modules/discovery/repository"
	"github.com/matheussouza/inframap/modules/discovery/usecase"
)

type mockDiscoveryUseCase struct {
	sources []*dto.DiscoverySourceResponse
	records []*dto.DiscoveryRecordResponse

	failCreateSource        bool
	failGetSource           bool
	failListSources         bool
	failTriggerRun          bool
	failTriggerScan         bool
	failListRecordsByDevice bool
}


func (m *mockDiscoveryUseCase) CreateSource(_ context.Context, req *dto.CreateDiscoverySourceRequest) (*dto.DiscoverySourceResponse, error) {
	if m.failCreateSource {
		return nil, errors.New("internal create error")
	}
	if req.Name == "" {
		return nil, usecase.ErrInvalidInput
	}
	resp := &dto.DiscoverySourceResponse{
		ID:         uuid.New(),
		Name:       req.Name,
		Type:       req.Type,
		LastStatus: "idle",
	}
	m.sources = append(m.sources, resp)
	return resp, nil
}

func (m *mockDiscoveryUseCase) GetSourceByID(_ context.Context, idStr string) (*dto.DiscoverySourceResponse, error) {
	if m.failGetSource {
		return nil, errors.New("internal get error")
	}
	id, err := uuid.Parse(idStr)
	if err != nil {
		return nil, usecase.ErrInvalidUUID
	}
	for _, s := range m.sources {
		if s.ID == id {
			return s, nil
		}
	}
	return nil, repository.ErrSourceNotFound
}

func (m *mockDiscoveryUseCase) ListSources(_ context.Context) ([]*dto.DiscoverySourceResponse, error) {
	if m.failListSources {
		return nil, errors.New("internal list error")
	}
	return m.sources, nil
}

func (m *mockDiscoveryUseCase) TriggerRun(_ context.Context, idStr string) (*dto.DiscoverySourceResponse, error) {
	if m.failTriggerRun {
		return nil, errors.New("internal trigger error")
	}
	s, err := m.GetSourceByID(context.Background(), idStr)
	if err != nil {
		return nil, err
	}
	s.LastStatus = "idle"
	return s, nil
}

func (m *mockDiscoveryUseCase) TriggerScan(_ context.Context, req *dto.TriggerScanRequest) (*dto.ScanResultResponse, error) {

	if m.failTriggerScan {
		return nil, errors.New("internal scan error")
	}
	if req == nil || req.CIDR == "" {
		return nil, usecase.ErrInvalidInput
	}
	return &dto.ScanResultResponse{
		CIDR:           req.CIDR,
		TotalCollected: 4,
		TotalValid:     3,
	}, nil
}

func (m *mockDiscoveryUseCase) IngestNormalizedDevice(_ context.Context, _ uuid.UUID, _ *dto.NormalizedDeviceDTO) (*dto.DiscoveryRecordResponse, error) {

	return nil, nil
}

func (m *mockDiscoveryUseCase) ListRecordsByDevice(_ context.Context, idStr string) ([]*dto.DiscoveryRecordResponse, error) {
	if m.failListRecordsByDevice {
		return nil, errors.New("internal list records error")
	}
	if _, err := uuid.Parse(idStr); err != nil {
		return nil, usecase.ErrInvalidUUID
	}
	return m.records, nil
}

func TestDiscoveryController_Unit(t *testing.T) {
	uc := &mockDiscoveryUseCase{}
	ctrl := controller.NewDiscoveryController(uc)

	mux := http.NewServeMux()
	mux.HandleFunc("POST /api/v1/discovery/sources", ctrl.CreateSource)
	mux.HandleFunc("GET /api/v1/discovery/sources", ctrl.ListSources)
	mux.HandleFunc("GET /api/v1/discovery/sources/{id}", ctrl.GetSourceByID)
	mux.HandleFunc("POST /api/v1/discovery/sources/{id}/run", ctrl.TriggerRun)
	mux.HandleFunc("POST /api/v1/discovery/scan", ctrl.TriggerScan)
	mux.HandleFunc("GET /api/v1/discovery/devices/{id}/records", ctrl.ListRecordsByDevice)


	// Pre-seed a source
	src, _ := uc.CreateSource(context.Background(), &dto.CreateDiscoverySourceRequest{
		Name: "Seed Proxmox",
		Type: "proxmox",
	})

	t.Run("CreateSource Success", func(t *testing.T) {
		body := map[string]interface{}{
			"name": "UniFi AP Controller",
			"type": "unifi",
		}
		jsonBytes, _ := json.Marshal(body)
		req := httptest.NewRequest("POST", "/api/v1/discovery/sources", bytes.NewReader(jsonBytes))
		rec := httptest.NewRecorder()

		mux.ServeHTTP(rec, req)

		if rec.Code != http.StatusCreated {
			t.Errorf("expected 201 Created, got %d", rec.Code)
		}
	})

	t.Run("CreateSource Invalid JSON", func(t *testing.T) {
		req := httptest.NewRequest("POST", "/api/v1/discovery/sources", bytes.NewReader([]byte("{invalid-json")))
		rec := httptest.NewRecorder()

		mux.ServeHTTP(rec, req)

		if rec.Code != http.StatusBadRequest {
			t.Errorf("expected 400 Bad Request, got %d", rec.Code)
		}
	})

	t.Run("CreateSource Invalid Input", func(t *testing.T) {
		body := map[string]interface{}{"name": ""}
		jsonBytes, _ := json.Marshal(body)
		req := httptest.NewRequest("POST", "/api/v1/discovery/sources", bytes.NewReader(jsonBytes))
		rec := httptest.NewRecorder()

		mux.ServeHTTP(rec, req)

		if rec.Code != http.StatusBadRequest {
			t.Errorf("expected 400 Bad Request, got %d", rec.Code)
		}
	})

	t.Run("CreateSource Internal Error", func(t *testing.T) {
		uc.failCreateSource = true
		body := map[string]interface{}{"name": "Valid Name", "type": "proxmox"}
		jsonBytes, _ := json.Marshal(body)
		req := httptest.NewRequest("POST", "/api/v1/discovery/sources", bytes.NewReader(jsonBytes))
		rec := httptest.NewRecorder()

		mux.ServeHTTP(rec, req)

		if rec.Code != http.StatusInternalServerError {
			t.Errorf("expected 500 Internal Server Error, got %d", rec.Code)
		}
		uc.failCreateSource = false
	})

	t.Run("GetSourceByID Success", func(t *testing.T) {
		req := httptest.NewRequest("GET", "/api/v1/discovery/sources/"+src.ID.String(), nil)
		rec := httptest.NewRecorder()

		mux.ServeHTTP(rec, req)

		if rec.Code != http.StatusOK {
			t.Errorf("expected 200 OK, got %d", rec.Code)
		}
	})

	t.Run("GetSourceByID Invalid UUID", func(t *testing.T) {
		req := httptest.NewRequest("GET", "/api/v1/discovery/sources/invalid-uuid", nil)
		rec := httptest.NewRecorder()

		mux.ServeHTTP(rec, req)

		if rec.Code != http.StatusBadRequest {
			t.Errorf("expected 400 Bad Request, got %d", rec.Code)
		}
	})

	t.Run("GetSourceByID NotFound", func(t *testing.T) {
		req := httptest.NewRequest("GET", "/api/v1/discovery/sources/"+uuid.New().String(), nil)
		rec := httptest.NewRecorder()

		mux.ServeHTTP(rec, req)

		if rec.Code != http.StatusNotFound {
			t.Errorf("expected 404 Not Found, got %d", rec.Code)
		}
	})

	t.Run("GetSourceByID Internal Error", func(t *testing.T) {
		uc.failGetSource = true
		req := httptest.NewRequest("GET", "/api/v1/discovery/sources/"+src.ID.String(), nil)
		rec := httptest.NewRecorder()

		mux.ServeHTTP(rec, req)

		if rec.Code != http.StatusInternalServerError {
			t.Errorf("expected 500 Internal Server Error, got %d", rec.Code)
		}
		uc.failGetSource = false
	})

	t.Run("ListSources Success", func(t *testing.T) {
		req := httptest.NewRequest("GET", "/api/v1/discovery/sources", nil)
		rec := httptest.NewRecorder()

		mux.ServeHTTP(rec, req)

		if rec.Code != http.StatusOK {
			t.Errorf("expected 200 OK, got %d", rec.Code)
		}
	})

	t.Run("ListSources Internal Error", func(t *testing.T) {
		uc.failListSources = true
		req := httptest.NewRequest("GET", "/api/v1/discovery/sources", nil)
		rec := httptest.NewRecorder()

		mux.ServeHTTP(rec, req)

		if rec.Code != http.StatusInternalServerError {
			t.Errorf("expected 500 Internal Server Error, got %d", rec.Code)
		}
		uc.failListSources = false
	})

	t.Run("TriggerRun Success", func(t *testing.T) {
		req := httptest.NewRequest("POST", "/api/v1/discovery/sources/"+src.ID.String()+"/run", nil)
		rec := httptest.NewRecorder()

		mux.ServeHTTP(rec, req)

		if rec.Code != http.StatusOK {
			t.Errorf("expected 200 OK, got %d", rec.Code)
		}
	})

	t.Run("TriggerRun Invalid UUID", func(t *testing.T) {
		req := httptest.NewRequest("POST", "/api/v1/discovery/sources/invalid-uuid/run", nil)
		rec := httptest.NewRecorder()

		mux.ServeHTTP(rec, req)

		if rec.Code != http.StatusBadRequest {
			t.Errorf("expected 400 Bad Request, got %d", rec.Code)
		}
	})

	t.Run("TriggerRun NotFound", func(t *testing.T) {
		req := httptest.NewRequest("POST", "/api/v1/discovery/sources/"+uuid.New().String()+"/run", nil)
		rec := httptest.NewRecorder()

		mux.ServeHTTP(rec, req)

		if rec.Code != http.StatusNotFound {
			t.Errorf("expected 404 Not Found, got %d", rec.Code)
		}
	})

	t.Run("TriggerRun Internal Error", func(t *testing.T) {
		uc.failTriggerRun = true
		req := httptest.NewRequest("POST", "/api/v1/discovery/sources/"+src.ID.String()+"/run", nil)
		rec := httptest.NewRecorder()

		mux.ServeHTTP(rec, req)

		if rec.Code != http.StatusInternalServerError {
			t.Errorf("expected 500 Internal Server Error, got %d", rec.Code)
		}
		uc.failTriggerRun = false
	})

	t.Run("ListRecordsByDevice Success", func(t *testing.T) {
		req := httptest.NewRequest("GET", "/api/v1/discovery/devices/"+uuid.New().String()+"/records", nil)
		rec := httptest.NewRecorder()

		mux.ServeHTTP(rec, req)

		if rec.Code != http.StatusOK {
			t.Errorf("expected 200 OK, got %d", rec.Code)
		}
	})

	t.Run("ListRecordsByDevice Invalid UUID", func(t *testing.T) {
		req := httptest.NewRequest("GET", "/api/v1/discovery/devices/invalid-uuid/records", nil)
		rec := httptest.NewRecorder()

		mux.ServeHTTP(rec, req)

		if rec.Code != http.StatusBadRequest {
			t.Errorf("expected 400 Bad Request, got %d", rec.Code)
		}
	})

	t.Run("ListRecordsByDevice Internal Error", func(t *testing.T) {
		uc.failListRecordsByDevice = true
		req := httptest.NewRequest("GET", "/api/v1/discovery/devices/"+uuid.New().String()+"/records", nil)
		rec := httptest.NewRecorder()

		mux.ServeHTTP(rec, req)

		if rec.Code != http.StatusInternalServerError {
			t.Errorf("expected 500 Internal Server Error, got %d", rec.Code)
		}
		uc.failListRecordsByDevice = false
	})

	t.Run("TriggerScan Success", func(t *testing.T) {
		body := map[string]interface{}{"cidr": "192.168.1.0/24"}
		jsonBytes, _ := json.Marshal(body)
		req := httptest.NewRequest("POST", "/api/v1/discovery/scan", bytes.NewReader(jsonBytes))
		rec := httptest.NewRecorder()

		mux.ServeHTTP(rec, req)

		if rec.Code != http.StatusOK {
			t.Errorf("expected 200 OK, got %d", rec.Code)
		}
	})

	t.Run("TriggerScan Invalid JSON", func(t *testing.T) {
		req := httptest.NewRequest("POST", "/api/v1/discovery/scan", bytes.NewReader([]byte("invalid json")))
		rec := httptest.NewRecorder()

		mux.ServeHTTP(rec, req)

		if rec.Code != http.StatusBadRequest {
			t.Errorf("expected 400 Bad Request, got %d", rec.Code)
		}
	})

	t.Run("TriggerScan Invalid Input", func(t *testing.T) {
		body := map[string]interface{}{"cidr": ""}
		jsonBytes, _ := json.Marshal(body)
		req := httptest.NewRequest("POST", "/api/v1/discovery/scan", bytes.NewReader(jsonBytes))
		rec := httptest.NewRecorder()

		mux.ServeHTTP(rec, req)

		if rec.Code != http.StatusBadRequest {
			t.Errorf("expected 400 Bad Request, got %d", rec.Code)
		}
	})

	t.Run("TriggerScan Internal Error", func(t *testing.T) {
		uc.failTriggerScan = true
		body := map[string]interface{}{"cidr": "192.168.1.0/24"}
		jsonBytes, _ := json.Marshal(body)
		req := httptest.NewRequest("POST", "/api/v1/discovery/scan", bytes.NewReader(jsonBytes))
		rec := httptest.NewRecorder()

		mux.ServeHTTP(rec, req)

		if rec.Code != http.StatusInternalServerError {
			t.Errorf("expected 500 Internal Server Error, got %d", rec.Code)
		}
		uc.failTriggerScan = false
	})
}

