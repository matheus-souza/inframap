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
	"github.com/matheussouza/inframap/internal/platform/eventbus"
	inventoryUC "github.com/matheussouza/inframap/modules/inventory/usecase"
	"github.com/matheussouza/inframap/modules/topology/controller"
	"github.com/matheussouza/inframap/modules/topology/dto"
	topoRepo "github.com/matheussouza/inframap/modules/topology/repository"
)

type mockTopoUseCase struct {
	links []*dto.TopologyLinkResponse

	failCreate     bool
	failGet        bool
	failList       bool
	failDelete     bool
	failGetGraph   bool
}

func (m *mockTopoUseCase) CreateLink(_ context.Context, req *dto.CreateTopologyLinkRequest) (*dto.TopologyLinkResponse, error) {
	if m.failCreate {
		return nil, errors.New("internal create error")
	}
	if req.SourceDeviceID == uuid.Nil || req.TargetDeviceID == uuid.Nil {
		return nil, inventoryUC.ErrInvalidInput
	}
	resp := &dto.TopologyLinkResponse{
		ID:             uuid.New(),
		SourceDeviceID: req.SourceDeviceID,
		TargetDeviceID: req.TargetDeviceID,
		LinkType:       req.LinkType,
	}
	m.links = append(m.links, resp)
	return resp, nil
}

func (m *mockTopoUseCase) GetLinkByID(_ context.Context, idStr string) (*dto.TopologyLinkResponse, error) {
	if m.failGet {
		return nil, errors.New("internal get error")
	}
	id, err := uuid.Parse(idStr)
	if err != nil {
		return nil, inventoryUC.ErrInvalidUUID
	}
	for _, l := range m.links {
		if l.ID == id {
			return l, nil
		}
	}
	return nil, topoRepo.ErrLinkNotFound
}

func (m *mockTopoUseCase) ListLinks(_ context.Context, _, sourceIDStr, _ string, _, _ int32) ([]*dto.TopologyLinkResponse, error) {
	if m.failList {
		return nil, errors.New("internal list error")
	}
	if sourceIDStr == "invalid-uuid" {
		return nil, inventoryUC.ErrInvalidUUID
	}
	return m.links, nil
}

func (m *mockTopoUseCase) DeleteLink(_ context.Context, idStr string) error {
	if m.failDelete {
		return errors.New("internal delete error")
	}
	if idStr == "00000000-0000-0000-0000-000000000000" {
		return topoRepo.ErrLinkNotFound
	}
	if _, err := uuid.Parse(idStr); err != nil {
		return inventoryUC.ErrInvalidUUID
	}
	return nil
}

func (m *mockTopoUseCase) GetGraph(_ context.Context) (*dto.TopologyGraphResponse, error) {
	if m.failGetGraph {
		return nil, errors.New("internal graph error")
	}
	return &dto.TopologyGraphResponse{
		Nodes: []dto.DeviceNode{},
		Edges: []dto.LinkEdge{},
	}, nil
}

func (m *mockTopoUseCase) HandleDeviceEvent(_ context.Context, _ eventbus.DomainEvent) error {
	return nil
}

func TestTopologyController_Unit(t *testing.T) {
	uc := &mockTopoUseCase{}
	ctrl := controller.NewTopologyController(uc)

	mux := http.NewServeMux()
	mux.HandleFunc("POST /api/v1/topology/links", ctrl.CreateLink)
	mux.HandleFunc("GET /api/v1/topology/links", ctrl.ListLinks)
	mux.HandleFunc("GET /api/v1/topology/links/{id}", ctrl.GetLinkByID)
	mux.HandleFunc("DELETE /api/v1/topology/links/{id}", ctrl.DeleteLink)
	mux.HandleFunc("GET /api/v1/topology/graph", ctrl.GetGraph)

	dev1 := uuid.New()
	dev2 := uuid.New()

	// Pre-seed a link
	l, _ := uc.CreateLink(context.Background(), &dto.CreateTopologyLinkRequest{
		SourceDeviceID: dev1,
		TargetDeviceID: dev2,
		LinkType:       dto.LinkTypeManual,
	})

	t.Run("CreateLink Success", func(t *testing.T) {
		body := map[string]interface{}{
			"source_device_id": dev1,
			"target_device_id": dev2,
			"link_type":        "manual",
		}
		jsonBytes, _ := json.Marshal(body)
		req := httptest.NewRequest("POST", "/api/v1/topology/links", bytes.NewReader(jsonBytes))
		rec := httptest.NewRecorder()

		mux.ServeHTTP(rec, req)

		if rec.Code != http.StatusCreated {
			t.Errorf("expected 201 Created, got %d", rec.Code)
		}
	})

	t.Run("CreateLink Invalid JSON", func(t *testing.T) {
		req := httptest.NewRequest("POST", "/api/v1/topology/links", bytes.NewReader([]byte("{invalid-json")))
		rec := httptest.NewRecorder()

		mux.ServeHTTP(rec, req)

		if rec.Code != http.StatusBadRequest {
			t.Errorf("expected 400 Bad Request, got %d", rec.Code)
		}
	})

	t.Run("CreateLink Validation Error", func(t *testing.T) {
		body := map[string]interface{}{
			"source_device_id": uuid.Nil,
			"target_device_id": dev2,
		}
		jsonBytes, _ := json.Marshal(body)
		req := httptest.NewRequest("POST", "/api/v1/topology/links", bytes.NewReader(jsonBytes))
		rec := httptest.NewRecorder()

		mux.ServeHTTP(rec, req)

		if rec.Code != http.StatusBadRequest {
			t.Errorf("expected 400 Bad Request, got %d", rec.Code)
		}
	})

	t.Run("CreateLink Internal Error", func(t *testing.T) {
		uc.failCreate = true
		body := map[string]interface{}{
			"source_device_id": dev1,
			"target_device_id": dev2,
		}
		jsonBytes, _ := json.Marshal(body)
		req := httptest.NewRequest("POST", "/api/v1/topology/links", bytes.NewReader(jsonBytes))
		rec := httptest.NewRecorder()

		mux.ServeHTTP(rec, req)

		if rec.Code != http.StatusInternalServerError {
			t.Errorf("expected 500 Internal Server Error, got %d", rec.Code)
		}
		uc.failCreate = false
	})

	t.Run("GetLinkByID Success", func(t *testing.T) {
		req := httptest.NewRequest("GET", "/api/v1/topology/links/"+l.ID.String(), nil)
		rec := httptest.NewRecorder()

		mux.ServeHTTP(rec, req)

		if rec.Code != http.StatusOK {
			t.Errorf("expected 200 OK, got %d", rec.Code)
		}
	})

	t.Run("GetLinkByID Invalid UUID", func(t *testing.T) {
		req := httptest.NewRequest("GET", "/api/v1/topology/links/invalid-uuid", nil)
		rec := httptest.NewRecorder()

		mux.ServeHTTP(rec, req)

		if rec.Code != http.StatusBadRequest {
			t.Errorf("expected 400 Bad Request, got %d", rec.Code)
		}
	})

	t.Run("GetLinkByID NotFound", func(t *testing.T) {
		req := httptest.NewRequest("GET", "/api/v1/topology/links/"+uuid.New().String(), nil)
		rec := httptest.NewRecorder()

		mux.ServeHTTP(rec, req)

		if rec.Code != http.StatusNotFound {
			t.Errorf("expected 404 Not Found, got %d", rec.Code)
		}
	})

	t.Run("GetLinkByID Internal Error", func(t *testing.T) {
		uc.failGet = true
		req := httptest.NewRequest("GET", "/api/v1/topology/links/"+l.ID.String(), nil)
		rec := httptest.NewRecorder()

		mux.ServeHTTP(rec, req)

		if rec.Code != http.StatusInternalServerError {
			t.Errorf("expected 500 Internal Server Error, got %d", rec.Code)
		}
		uc.failGet = false
	})

	t.Run("ListLinks Success & Invalid UUID & Internal Error", func(t *testing.T) {
		req := httptest.NewRequest("GET", "/api/v1/topology/links", nil)
		rec := httptest.NewRecorder()

		mux.ServeHTTP(rec, req)

		if rec.Code != http.StatusOK {
			t.Errorf("expected 200 OK, got %d", rec.Code)
		}

		reqInvalid := httptest.NewRequest("GET", "/api/v1/topology/links?source_device_id=invalid-uuid", nil)
		recInvalid := httptest.NewRecorder()
		mux.ServeHTTP(recInvalid, reqInvalid)

		if recInvalid.Code != http.StatusBadRequest {
			t.Errorf("expected 400 Bad Request, got %d", recInvalid.Code)
		}

		uc.failList = true
		reqErr := httptest.NewRequest("GET", "/api/v1/topology/links", nil)
		recErr := httptest.NewRecorder()
		mux.ServeHTTP(recErr, reqErr)

		if recErr.Code != http.StatusInternalServerError {
			t.Errorf("expected 500 Internal Server Error, got %d", recErr.Code)
		}
		uc.failList = false
	})

	t.Run("DeleteLink Success & Invalid UUID & Internal Error", func(t *testing.T) {
		req := httptest.NewRequest("DELETE", "/api/v1/topology/links/"+l.ID.String(), nil)
		rec := httptest.NewRecorder()

		mux.ServeHTTP(rec, req)

		if rec.Code != http.StatusNoContent {
			t.Errorf("expected 244 No Content, got %d", rec.Code)
		}

		reqInvalid := httptest.NewRequest("DELETE", "/api/v1/topology/links/invalid-uuid", nil)
		recInvalid := httptest.NewRecorder()
		mux.ServeHTTP(recInvalid, reqInvalid)

		if recInvalid.Code != http.StatusBadRequest {
			t.Errorf("expected 400 Bad Request, got %d", recInvalid.Code)
		}

		reqNotFound := httptest.NewRequest("DELETE", "/api/v1/topology/links/00000000-0000-0000-0000-000000000000", nil)
		recNotFound := httptest.NewRecorder()
		mux.ServeHTTP(recNotFound, reqNotFound)

		if recNotFound.Code != http.StatusNotFound {
			t.Errorf("expected 404 Not Found, got %d", recNotFound.Code)
		}

		uc.failDelete = true
		reqErr := httptest.NewRequest("DELETE", "/api/v1/topology/links/"+uuid.New().String(), nil)
		recErr := httptest.NewRecorder()
		mux.ServeHTTP(recErr, reqErr)

		if recErr.Code != http.StatusInternalServerError {
			t.Errorf("expected 500 Internal Server Error, got %d", recErr.Code)
		}
		uc.failDelete = false
	})

	t.Run("GetGraph Success & Internal Error", func(t *testing.T) {
		req := httptest.NewRequest("GET", "/api/v1/topology/graph", nil)
		rec := httptest.NewRecorder()

		mux.ServeHTTP(rec, req)

		if rec.Code != http.StatusOK {
			t.Errorf("expected 200 OK, got %d", rec.Code)
		}

		uc.failGetGraph = true
		reqErr := httptest.NewRequest("GET", "/api/v1/topology/graph", nil)
		recErr := httptest.NewRecorder()
		mux.ServeHTTP(recErr, reqErr)

		if recErr.Code != http.StatusInternalServerError {
			t.Errorf("expected 500 Internal Server Error, got %d", recErr.Code)
		}
		uc.failGetGraph = false
	})
}
