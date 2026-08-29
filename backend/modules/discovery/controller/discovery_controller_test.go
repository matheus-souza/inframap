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
	failDeleteSource        bool
	failListRecordsByDevice bool
}


func (m *mockDiscoveryUseCase) CreateSource(_ context.Context, req *dto.CreateDiscoverySourceRequest) (*dto.DiscoverySourceResponse, error) {
	if m.failCreateSource {
		return nil, errors.New("internal create error")
	}
	req.Normalize()
	if err := req.Validate(); err != nil {
		return nil, usecase.ErrInvalidInput
	}
	cols := make([]dto.CollectorResponse, len(req.Collectors))
	for i, c := range req.Collectors {
		cols[i] = dto.CollectorResponse{
			ID:            uuid.New(),
			CollectorType: c.Type,
			Enabled:       true,
		}
	}
	resp := &dto.DiscoverySourceResponse{
		ID:         uuid.New(),
		Name:       req.Name,
		Type:       req.Type,
		Collectors: cols,
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
	for _, col := range req.Collectors {
		if !dto.ValidDiscoveryTypes[col] {
			return nil, usecase.ErrInvalidInput
		}
	}
	var cols []dto.CollectorRunDetail
	if len(req.Collectors) > 0 {
		for _, col := range req.Collectors {
			cols = append(cols, dto.CollectorRunDetail{
				CollectorType: col,
				Status:        "success",
				DevicesFound:  1,
				DurationMs:    10,
			})
		}
	} else {
		cols = []dto.CollectorRunDetail{
			{CollectorType: "icmp", Status: "success", DevicesFound: 2, DurationMs: 15},
			{CollectorType: "arp", Status: "success", DevicesFound: 2, DurationMs: 5},
		}
	}
	return &dto.ScanResultResponse{
		CIDR:           req.CIDR,
		TotalCollected: 4,
		TotalValid:     3,
		Collectors:     cols,
	}, nil
}

func (m *mockDiscoveryUseCase) IngestNormalizedDevice(_ context.Context, _ uuid.UUID, _ *dto.NormalizedDeviceDTO) (*dto.DiscoveryRecordResponse, error) {

	return nil, nil
}

func (m *mockDiscoveryUseCase) DeleteSource(_ context.Context, idStr string) error {
	if m.failDeleteSource {
		return errors.New("internal delete error")
	}
	id, err := uuid.Parse(idStr)
	if err != nil {
		return usecase.ErrInvalidUUID
	}
	for i, s := range m.sources {
		if s.ID == id {
			m.sources = append(m.sources[:i], m.sources[i+1:]...)
			return nil
		}
	}
	return repository.ErrSourceNotFound
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
	mux.HandleFunc("DELETE /api/v1/discovery/sources/{id}", ctrl.DeleteSource)
	mux.HandleFunc("GET /api/v1/discovery/devices/{id}/records", ctrl.ListRecordsByDevice)


	// Pre-seed a source
	src, _ := uc.CreateSource(context.Background(), &dto.CreateDiscoverySourceRequest{
		Name: "Seed Proxmox",
		Type: "proxmox",
	})

	t.Run("CreateSource Legacy Format Success", func(t *testing.T) {
		body := map[string]interface{}{
			"name": "UniFi AP Controller",
			"type": "unifi",
		}
		jsonBytes, _ := json.Marshal(body)
		req := httptest.NewRequest("POST", "/api/v1/discovery/sources", bytes.NewReader(jsonBytes))
		rec := httptest.NewRecorder()

		mux.ServeHTTP(rec, req)

		if rec.Code != http.StatusCreated {
			t.Fatalf("expected 201 Created, got %d", rec.Code)
		}

		var env struct {
			Data dto.DiscoverySourceResponse `json:"data"`
		}
		if err := json.NewDecoder(rec.Body).Decode(&env); err != nil {
			t.Fatalf("failed to decode response: %v", err)
		}
		created := env.Data
		if created.Name != "UniFi AP Controller" || created.Type != "unifi" {
			t.Errorf("unexpected name or type: %s, %s", created.Name, created.Type)
		}
		if len(created.Collectors) != 1 || created.Collectors[0].CollectorType != "unifi" {
			t.Errorf("expected 1 collector of type unifi, got %+v", created.Collectors)
		}
	})

	t.Run("CreateSource Multi-Collector Plan Success", func(t *testing.T) {
		body := map[string]interface{}{
			"name": "Local Network Plan",
			"collectors": []map[string]interface{}{
				{"type": "icmp_sweep"},
				{"type": "arp_sweep"},
			},
		}
		jsonBytes, _ := json.Marshal(body)
		req := httptest.NewRequest("POST", "/api/v1/discovery/sources", bytes.NewReader(jsonBytes))
		rec := httptest.NewRecorder()

		mux.ServeHTTP(rec, req)

		if rec.Code != http.StatusCreated {
			t.Fatalf("expected 201 Created, got %d", rec.Code)
		}

		var env struct {
			Data dto.DiscoverySourceResponse `json:"data"`
		}
		if err := json.NewDecoder(rec.Body).Decode(&env); err != nil {
			t.Fatalf("failed to decode response: %v", err)
		}
		created := env.Data
		if created.Name != "Local Network Plan" {
			t.Errorf("unexpected name: %s", created.Name)
		}
		if len(created.Collectors) != 2 {
			t.Fatalf("expected 2 collectors, got %d", len(created.Collectors))
		}
		if created.Collectors[0].CollectorType != "icmp_sweep" || created.Collectors[1].CollectorType != "arp_sweep" {
			t.Errorf("unexpected collectors: %+v", created.Collectors)
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

	t.Run("DeleteSource Success", func(t *testing.T) {
		deleteID := src.ID.String()
		req := httptest.NewRequest("DELETE", "/api/v1/discovery/sources/"+deleteID, nil)
		rec := httptest.NewRecorder()

		mux.ServeHTTP(rec, req)

		if rec.Code != http.StatusOK {
			t.Errorf("expected 200 OK, got %d", rec.Code)
		}
	})

	t.Run("DeleteSource Invalid UUID", func(t *testing.T) {
		req := httptest.NewRequest("DELETE", "/api/v1/discovery/sources/invalid-uuid", nil)
		rec := httptest.NewRecorder()

		mux.ServeHTTP(rec, req)

		if rec.Code != http.StatusBadRequest {
			t.Errorf("expected 400 Bad Request, got %d", rec.Code)
		}
	})

	t.Run("DeleteSource NotFound", func(t *testing.T) {
		req := httptest.NewRequest("DELETE", "/api/v1/discovery/sources/"+uuid.New().String(), nil)
		rec := httptest.NewRecorder()

		mux.ServeHTTP(rec, req)

		if rec.Code != http.StatusNotFound {
			t.Errorf("expected 404 Not Found, got %d", rec.Code)
		}
	})

	t.Run("DeleteSource Internal Error", func(t *testing.T) {
		uc.failDeleteSource = true
		req := httptest.NewRequest("DELETE", "/api/v1/discovery/sources/"+uuid.New().String(), nil)
		rec := httptest.NewRecorder()

		mux.ServeHTTP(rec, req)

		if rec.Code != http.StatusInternalServerError {
			t.Errorf("expected 500 Internal Server Error, got %d", rec.Code)
		}
		uc.failDeleteSource = false
	})

	t.Run("TriggerScan Success", func(t *testing.T) {
		body := map[string]interface{}{"cidr": "192.168.1.0/24"}
		jsonBytes, _ := json.Marshal(body)
		req := httptest.NewRequest("POST", "/api/v1/discovery/scan", bytes.NewReader(jsonBytes))
		rec := httptest.NewRecorder()

		mux.ServeHTTP(rec, req)

		if rec.Code != http.StatusOK {
			t.Fatalf("expected 200 OK, got %d", rec.Code)
		}

		var env map[string]interface{}
		if err := json.NewDecoder(rec.Body).Decode(&env); err != nil {
			t.Fatalf("failed to decode response: %v", err)
		}
		data, ok := env["data"].(map[string]interface{})
		if !ok {
			t.Fatal("expected data map in response envelope")
		}
		if cidr, _ := data["cidr"].(string); cidr != "192.168.1.0/24" {
			t.Errorf("expected cidr '192.168.1.0/24', got %q", cidr)
		}
		if tc, _ := data["total_collected"].(float64); int(tc) != 4 {
			t.Errorf("expected total_collected 4, got %d", int(tc))
		}
		if tv, _ := data["total_valid"].(float64); int(tv) != 3 {
			t.Errorf("expected total_valid 3, got %d", int(tv))
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

	t.Run("TriggerScan Success With Selective Collectors", func(t *testing.T) {
		body := map[string]interface{}{
			"cidr":       "192.168.1.0/24",
			"collectors": []string{"icmp_sweep", "arp_sweep"},
		}
		jsonBytes, _ := json.Marshal(body)
		req := httptest.NewRequest("POST", "/api/v1/discovery/scan", bytes.NewReader(jsonBytes))
		rec := httptest.NewRecorder()

		mux.ServeHTTP(rec, req)

		if rec.Code != http.StatusOK {
			t.Fatalf("expected 200 OK, got %d", rec.Code)
		}

		var env map[string]interface{}
		if err := json.NewDecoder(rec.Body).Decode(&env); err != nil {
			t.Fatalf("failed to decode response: %v", err)
		}
		data, ok := env["data"].(map[string]interface{})
		if !ok {
			t.Fatal("expected data map in response envelope")
		}
		if cidr, _ := data["cidr"].(string); cidr != "192.168.1.0/24" {
			t.Errorf("expected cidr '192.168.1.0/24', got %q", cidr)
		}
		cols, ok := data["collectors"].([]interface{})
		if !ok || len(cols) != 2 {
			t.Fatalf("expected 2 collector details in response, got %v", data["collectors"])
		}
	})

	t.Run("TriggerScan Invalid Collector Name", func(t *testing.T) {
		body := map[string]interface{}{
			"cidr":       "192.168.1.0/24",
			"collectors": []string{"unsupported_collector_type"},
		}
		jsonBytes, _ := json.Marshal(body)
		req := httptest.NewRequest("POST", "/api/v1/discovery/scan", bytes.NewReader(jsonBytes))
		rec := httptest.NewRecorder()

		mux.ServeHTTP(rec, req)

		if rec.Code != http.StatusBadRequest {
			t.Errorf("expected 400 Bad Request for invalid collector name, got %d", rec.Code)
		}

		var resp map[string]interface{}
		if err := json.NewDecoder(rec.Body).Decode(&resp); err != nil {
			t.Fatalf("failed to decode response: %v", err)
		}
		errMap, ok := resp["error"].(map[string]interface{})
		if !ok {
			t.Fatalf("expected error map in response, got %v", resp["error"])
		}
		if code, _ := errMap["code"].(string); code != "INVALID_INPUT" {
			t.Errorf("expected error code 'INVALID_INPUT', got %q", code)
		}
	})
}

