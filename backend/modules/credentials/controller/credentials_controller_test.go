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
	"github.com/jackc/pgx/v5/pgtype"
	"github.com/matheussouza/inframap/internal/platform/db"
	"github.com/matheussouza/inframap/internal/platform/eventbus"
	"github.com/matheussouza/inframap/modules/credentials/controller"
	"github.com/matheussouza/inframap/modules/credentials/repository"
	"github.com/matheussouza/inframap/modules/credentials/usecase"
)

type mockRepo struct {
	items       map[uuid.UUID]db.Credential
	secrets     map[uuid.UUID]string
	shouldError bool
}

func newMockRepo() *mockRepo {
	return &mockRepo{
		items:   make(map[uuid.UUID]db.Credential),
		secrets: make(map[uuid.UUID]string),
	}
}

func (m *mockRepo) Create(_ context.Context, cred *db.Credential, secret string) (*db.Credential, error) {
	if m.shouldError {
		return nil, errors.New("db failure")
	}
	m.items[cred.ID] = *cred
	m.secrets[cred.ID] = secret
	return cred, nil
}

func (m *mockRepo) GetByID(_ context.Context, id uuid.UUID) (*db.Credential, string, error) {
	if m.shouldError {
		return nil, "", errors.New("db failure")
	}
	c, ok := m.items[id]
	if !ok {
		return nil, "", repository.ErrNotFound
	}
	return &c, m.secrets[id], nil
}

func (m *mockRepo) List(_ context.Context, _, _ int32) ([]db.Credential, int64, error) {
	if m.shouldError {
		return nil, 0, errors.New("db failure")
	}
	res := make([]db.Credential, 0, len(m.items))
	for _, v := range m.items {
		res = append(res, v)
	}
	return res, int64(len(m.items)), nil
}

func (m *mockRepo) Delete(_ context.Context, id uuid.UUID) error {
	if m.shouldError {
		return errors.New("db failure")
	}
	if _, ok := m.items[id]; !ok {
		return repository.ErrNotFound
	}
	delete(m.items, id)
	delete(m.secrets, id)
	return nil
}

func TestCredentialsController_Unit(t *testing.T) {
	repo := newMockRepo()
	bus := eventbus.NewInMemoryEventBus(1, 10)
	defer func() {
		_ = bus.Close()
	}()

	uc, _ := usecase.NewCredentialsUseCase(repo, bus)
	ctrl := controller.NewCredentialsController(uc)

	mux := http.NewServeMux()
	mux.HandleFunc("GET /api/v1/credentials", ctrl.ListCredentials)
	mux.HandleFunc("POST /api/v1/credentials", ctrl.CreateCredential)
	mux.HandleFunc("GET /api/v1/credentials/{id}", ctrl.GetCredentialByID)
	mux.HandleFunc("DELETE /api/v1/credentials/{id}", ctrl.DeleteCredential)

	t.Run("CreateCredential Success & Internal Error", func(t *testing.T) {
		body := map[string]string{
			"name":        "Test Token",
			"type":        "api_token",
			"secret_data": "secret-abc",
		}
		jsonBytes, _ := json.Marshal(body)

		req := httptest.NewRequest(http.MethodPost, "/api/v1/credentials", bytes.NewReader(jsonBytes))
		rec := httptest.NewRecorder()

		mux.ServeHTTP(rec, req)

		if rec.Code != http.StatusCreated {
			t.Errorf("expected status 201 Created, got %d", rec.Code)
		}

		// Internal Error
		repo.shouldError = true
		reqErr := httptest.NewRequest(http.MethodPost, "/api/v1/credentials", bytes.NewReader(jsonBytes))
		recErr := httptest.NewRecorder()
		mux.ServeHTTP(recErr, reqErr)
		if recErr.Code != http.StatusInternalServerError {
			t.Errorf("expected status 500 Internal Server Error, got %d", recErr.Code)
		}
		repo.shouldError = false
	})

	t.Run("CreateCredential Payload Too Large", func(t *testing.T) {
		largeSecret := string(bytes.Repeat([]byte("A"), 2<<20))
		body, _ := json.Marshal(map[string]string{
			"name": "big", "type": "api_token", "secret_data": largeSecret,
		})
		req := httptest.NewRequest(http.MethodPost, "/api/v1/credentials", bytes.NewReader(body))
		rec := httptest.NewRecorder()
		mux.ServeHTTP(rec, req)
		if rec.Code != http.StatusRequestEntityTooLarge {
			t.Errorf("expected status 413, got %d", rec.Code)
		}
	})

	t.Run("CreateCredential Invalid JSON & Validation Error", func(t *testing.T) {
		req := httptest.NewRequest(http.MethodPost, "/api/v1/credentials", bytes.NewReader([]byte("invalid-json")))
		rec := httptest.NewRecorder()

		mux.ServeHTTP(rec, req)

		if rec.Code != http.StatusBadRequest {
			t.Errorf("expected status 400 Bad Request, got %d", rec.Code)
		}

		body := map[string]string{"name": ""}
		jsonBytes, _ := json.Marshal(body)

		reqVal := httptest.NewRequest(http.MethodPost, "/api/v1/credentials", bytes.NewReader(jsonBytes))
		recVal := httptest.NewRecorder()

		mux.ServeHTTP(recVal, reqVal)

		if recVal.Code != http.StatusBadRequest {
			t.Errorf("expected status 400 Bad Request, got %d", recVal.Code)
		}
	})

	t.Run("ListCredentials Success & Internal Error", func(t *testing.T) {
		req := httptest.NewRequest(http.MethodGet, "/api/v1/credentials?page=1&per_page=10", nil)
		rec := httptest.NewRecorder()

		mux.ServeHTTP(rec, req)

		if rec.Code != http.StatusOK {
			t.Errorf("expected status 200 OK, got %d", rec.Code)
		}

		repo.shouldError = true
		reqErr := httptest.NewRequest(http.MethodGet, "/api/v1/credentials", nil)
		recErr := httptest.NewRecorder()
		mux.ServeHTTP(recErr, reqErr)
		if recErr.Code != http.StatusInternalServerError {
			t.Errorf("expected status 500 Internal Server Error, got %d", recErr.Code)
		}
		repo.shouldError = false
	})

	t.Run("GetCredentialByID Success, NotFound, InvalidUUID & Internal Error", func(t *testing.T) {
		// Create item
		id := uuid.New()
		_, _ = repo.Create(context.Background(), &db.Credential{
			ID:          id,
			Name:        "Direct",
			Type:        "ssh_key",
			Description: pgtype.Text{String: "desc", Valid: true},
		}, "secret-data")

		// 1. Success
		req := httptest.NewRequest(http.MethodGet, "/api/v1/credentials/"+id.String(), nil)
		rec := httptest.NewRecorder()
		mux.ServeHTTP(rec, req)
		if rec.Code != http.StatusOK {
			t.Errorf("expected status 200 OK, got %d", rec.Code)
		}

		// 2. NotFound
		reqNF := httptest.NewRequest(http.MethodGet, "/api/v1/credentials/"+uuid.New().String(), nil)
		recNF := httptest.NewRecorder()
		mux.ServeHTTP(recNF, reqNF)
		if recNF.Code != http.StatusNotFound {
			t.Errorf("expected status 404 Not Found, got %d", recNF.Code)
		}

		// 3. Invalid UUID
		reqInv := httptest.NewRequest(http.MethodGet, "/api/v1/credentials/not-a-uuid", nil)
		recInv := httptest.NewRecorder()
		mux.ServeHTTP(recInv, reqInv)
		if recInv.Code != http.StatusBadRequest {
			t.Errorf("expected status 400 Bad Request, got %d", recInv.Code)
		}

		// 4. Internal Error
		repo.shouldError = true
		reqErr := httptest.NewRequest(http.MethodGet, "/api/v1/credentials/"+id.String(), nil)
		recErr := httptest.NewRecorder()
		mux.ServeHTTP(recErr, reqErr)
		if recErr.Code != http.StatusInternalServerError {
			t.Errorf("expected status 500 Internal Server Error, got %d", recErr.Code)
		}
		repo.shouldError = false
	})

	t.Run("DeleteCredential Success, NotFound, InvalidUUID & Internal Error", func(t *testing.T) {
		id := uuid.New()
		_, _ = repo.Create(context.Background(), &db.Credential{ID: id, Name: "Del", Type: "snmp_v2c"}, "sec")

		// 1. Internal Error
		repo.shouldError = true
		reqErr := httptest.NewRequest(http.MethodDelete, "/api/v1/credentials/"+id.String(), nil)
		recErr := httptest.NewRecorder()
		mux.ServeHTTP(recErr, reqErr)
		if recErr.Code != http.StatusInternalServerError {
			t.Errorf("expected status 500 Internal Server Error, got %d", recErr.Code)
		}
		repo.shouldError = false

		// 2. Success
		req := httptest.NewRequest(http.MethodDelete, "/api/v1/credentials/"+id.String(), nil)
		rec := httptest.NewRecorder()
		mux.ServeHTTP(rec, req)
		if rec.Code != http.StatusNoContent {
			t.Errorf("expected status 204 StatusNoContent, got %d", rec.Code)
		}

		// 3. NotFound
		reqNF := httptest.NewRequest(http.MethodDelete, "/api/v1/credentials/"+id.String(), nil)
		recNF := httptest.NewRecorder()
		mux.ServeHTTP(recNF, reqNF)
		if recNF.Code != http.StatusNotFound {
			t.Errorf("expected status 404 Not Found, got %d", recNF.Code)
		}

		// 4. Invalid UUID
		reqInv := httptest.NewRequest(http.MethodDelete, "/api/v1/credentials/bad-id", nil)
		recInv := httptest.NewRecorder()
		mux.ServeHTTP(recInv, reqInv)
		if recInv.Code != http.StatusBadRequest {
			t.Errorf("expected status 400 Bad Request, got %d", recInv.Code)
		}
	})
}
