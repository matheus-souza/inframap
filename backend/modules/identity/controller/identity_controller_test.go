package controller_test

import (
	"bytes"
	"context"
	"crypto/tls"
	"encoding/json"
	"errors"
	"net/http"
	"net/http/httptest"
	"testing"
	"time"

	"github.com/matheussouza/inframap/modules/identity/controller"
	"github.com/matheussouza/inframap/modules/identity/dto"
	"github.com/matheussouza/inframap/modules/identity/usecase"
)

type mockIdentityUseCase struct {
	loginResp *dto.LoginResponse
	loginErr  error
	logoutErr error
	meResp    *dto.UserMeResponse
	meErr     error
}

func (m *mockIdentityUseCase) Login(_ context.Context, _ dto.LoginRequest, _, _ string) (*dto.LoginResponse, error) {
	if m.loginErr != nil {
		return nil, m.loginErr
	}
	return m.loginResp, nil
}

func (m *mockIdentityUseCase) Logout(_ context.Context, _ string) error {
	return m.logoutErr
}

func (m *mockIdentityUseCase) GetMe(_ context.Context, _ string) (*dto.UserMeResponse, error) {
	if m.meErr != nil {
		return nil, m.meErr
	}
	return m.meResp, nil
}

func TestIdentityController_Unit(t *testing.T) {
	mockUC := &mockIdentityUseCase{}
	ctrl := controller.NewIdentityController(mockUC)

	t.Run("Login Invalid JSON Payload", func(t *testing.T) {
		req := httptest.NewRequest(http.MethodPost, "/api/v1/auth/login", bytes.NewReader([]byte("{invalid-json")))
		w := httptest.NewRecorder()

		ctrl.Login(w, req)

		if w.Code != http.StatusBadRequest {
			t.Errorf("expected 400 Bad Request, got %d", w.Code)
		}
	})

	t.Run("Login Validation Failed", func(t *testing.T) {
		payload := dto.LoginRequest{Username: "", Password: ""}
		body, _ := json.Marshal(payload)
		req := httptest.NewRequest(http.MethodPost, "/api/v1/auth/login", bytes.NewReader(body))
		w := httptest.NewRecorder()

		ctrl.Login(w, req)

		if w.Code != http.StatusBadRequest {
			t.Errorf("expected 400 Bad Request, got %d", w.Code)
		}
	})

	t.Run("Login Rate Limited", func(t *testing.T) {
		mockUC.loginErr = usecase.ErrAccountLocked
		payload := dto.LoginRequest{Username: "admin", Password: "passwordsuper123"}
		body, _ := json.Marshal(payload)
		req := httptest.NewRequest(http.MethodPost, "/api/v1/auth/login", bytes.NewReader(body))
		w := httptest.NewRecorder()

		ctrl.Login(w, req)

		if w.Code != http.StatusTooManyRequests {
			t.Errorf("expected 429 Too Many Requests, got %d", w.Code)
		}
	})

	t.Run("Login Success over HTTP sets non-Secure cookie", func(t *testing.T) {
		mockUC.loginErr = nil
		mockUC.loginResp = &dto.LoginResponse{
			Token:       "ims_testtoken",
			UserID:      "user-1",
			Username:    "admin",
			Permissions: []string{"admin"},
			ExpiresAt:   time.Now().Add(30 * time.Minute),
		}

		payload := dto.LoginRequest{Username: "admin", Password: "passwordsuper123"}
		body, _ := json.Marshal(payload)
		req := httptest.NewRequest(http.MethodPost, "/api/v1/auth/login", bytes.NewReader(body))
		w := httptest.NewRecorder()

		ctrl.Login(w, req)

		if w.Code != http.StatusOK {
			t.Errorf("expected 200 OK, got %d", w.Code)
		}

		resp := w.Result()
		defer func() { _ = resp.Body.Close() }()
		cookies := resp.Cookies()
		found := false
		for _, c := range cookies {
			if c.Name == controller.SessionCookieName {
				found = true
				if c.Value != "ims_testtoken" {
					t.Errorf("expected cookie value ims_testtoken, got %s", c.Value)
				}
				if !c.HttpOnly {
					t.Error("expected HttpOnly cookie flag")
				}
				if c.Secure {
					t.Error("expected non-Secure cookie for plain HTTP request")
				}
			}
		}
		if !found {
			t.Error("expected inframap_session cookie in response")
		}
	})

	t.Run("Login Success via HTTPS proxy sets Secure cookie", func(t *testing.T) {
		mockUC.loginErr = nil
		mockUC.loginResp = &dto.LoginResponse{
			Token:       "ims_testtoken_https",
			UserID:      "user-1",
			Username:    "admin",
			Permissions: []string{"admin"},
			ExpiresAt:   time.Now().Add(30 * time.Minute),
		}

		payload := dto.LoginRequest{Username: "admin", Password: "passwordsuper123"}
		body, _ := json.Marshal(payload)
		req := httptest.NewRequest(http.MethodPost, "/api/v1/auth/login", bytes.NewReader(body))
		req.Header.Set("X-Forwarded-Proto", "https")
		w := httptest.NewRecorder()

		ctrl.Login(w, req)

		if w.Code != http.StatusOK {
			t.Errorf("expected 200 OK, got %d", w.Code)
		}

		resp := w.Result()
		defer func() { _ = resp.Body.Close() }()
		for _, c := range resp.Cookies() {
			if c.Name == controller.SessionCookieName {
				if !c.Secure {
					t.Error("expected Secure cookie for HTTPS request")
				}
				return
			}
		}
		t.Error("expected inframap_session cookie in response")
	})

	t.Run("Login Success via direct TLS sets Secure cookie", func(t *testing.T) {
		mockUC.loginErr = nil
		mockUC.loginResp = &dto.LoginResponse{
			Token:       "ims_testtoken_tls",
			UserID:      "user-1",
			Username:    "admin",
			Permissions: []string{"admin"},
			ExpiresAt:   time.Now().Add(30 * time.Minute),
		}

		payload := dto.LoginRequest{Username: "admin", Password: "passwordsuper123"}
		body, _ := json.Marshal(payload)
		req := httptest.NewRequest(http.MethodPost, "/api/v1/auth/login", bytes.NewReader(body))
		req.TLS = &tls.ConnectionState{}
		w := httptest.NewRecorder()

		ctrl.Login(w, req)

		if w.Code != http.StatusOK {
			t.Errorf("expected 200 OK, got %d", w.Code)
		}

		resp := w.Result()
		defer func() { _ = resp.Body.Close() }()
		for _, c := range resp.Cookies() {
			if c.Name == controller.SessionCookieName {
				if !c.Secure {
					t.Error("expected Secure cookie for direct TLS request")
				}
				return
			}
		}
		t.Error("expected inframap_session cookie in response")
	})

	t.Run("Logout over HTTP sets non-Secure cookie", func(t *testing.T) {
		mockUC.logoutErr = nil
		req := httptest.NewRequest(http.MethodPost, "/api/v1/auth/logout", nil)
		req.Header.Set("Authorization", "Bearer ims_tokenclear")
		w := httptest.NewRecorder()

		ctrl.Logout(w, req)

		if w.Code != http.StatusOK {
			t.Errorf("expected 200 OK, got %d", w.Code)
		}

		resp := w.Result()
		defer func() { _ = resp.Body.Close() }()
		for _, c := range resp.Cookies() {
			if c.Name == controller.SessionCookieName {
				if c.Secure {
					t.Error("expected non-Secure cookie for plain HTTP logout")
				}
				if c.MaxAge != -1 {
					t.Errorf("expected MaxAge -1 on logout cookie, got %d", c.MaxAge)
				}
				return
			}
		}
		t.Error("expected inframap_session cookie in response")
	})

	t.Run("Logout via HTTPS proxy sets Secure cookie", func(t *testing.T) {
		mockUC.logoutErr = nil
		req := httptest.NewRequest(http.MethodPost, "/api/v1/auth/logout", nil)
		req.Header.Set("Authorization", "Bearer ims_tokenclear")
		req.Header.Set("X-Forwarded-Proto", "https")
		w := httptest.NewRecorder()

		ctrl.Logout(w, req)

		if w.Code != http.StatusOK {
			t.Errorf("expected 200 OK, got %d", w.Code)
		}

		resp := w.Result()
		defer func() { _ = resp.Body.Close() }()
		for _, c := range resp.Cookies() {
			if c.Name == controller.SessionCookieName {
				if !c.Secure {
					t.Error("expected Secure cookie for HTTPS proxy logout")
				}
				return
			}
		}
		t.Error("expected inframap_session cookie in response")
	})

	t.Run("Logout Failure Returns 500", func(t *testing.T) {
		mockUC.logoutErr = errors.New("db error")
		req := httptest.NewRequest(http.MethodPost, "/api/v1/auth/logout", nil)
		w := httptest.NewRecorder()

		ctrl.Logout(w, req)

		if w.Code != http.StatusInternalServerError {
			t.Errorf("expected 500 Internal Server Error, got %d", w.Code)
		}
	})

	t.Run("Logout via direct TLS sets Secure cookie", func(t *testing.T) {
		mockUC.logoutErr = nil
		req := httptest.NewRequest(http.MethodPost, "/api/v1/auth/logout", nil)
		req.Header.Set("Authorization", "Bearer ims_tokenclear")
		req.TLS = &tls.ConnectionState{}
		w := httptest.NewRecorder()

		ctrl.Logout(w, req)

		if w.Code != http.StatusOK {
			t.Errorf("expected 200 OK, got %d", w.Code)
		}

		resp := w.Result()
		defer func() { _ = resp.Body.Close() }()
		for _, c := range resp.Cookies() {
			if c.Name == controller.SessionCookieName {
				if !c.Secure {
					t.Error("expected Secure cookie for direct TLS logout")
				}
				if c.MaxAge != -1 {
					t.Errorf("expected MaxAge -1 on logout cookie, got %d", c.MaxAge)
				}
				return
			}
		}
		t.Error("expected inframap_session cookie in response")
	})

	t.Run("GetMe Missing Token Returns 401", func(t *testing.T) {
		req := httptest.NewRequest(http.MethodGet, "/api/v1/auth/me", nil)
		w := httptest.NewRecorder()

		ctrl.GetMe(w, req)

		if w.Code != http.StatusUnauthorized {
			t.Errorf("expected 401 Unauthorized, got %d", w.Code)
		}
	})

	t.Run("GetMe Success", func(t *testing.T) {
		mockUC.meErr = nil
		mockUC.meResp = &dto.UserMeResponse{
			ID:          "user-123",
			Username:    "admin",
			Email:       "admin@example.com",
			IsActive:    true,
			Permissions: []string{"admin"},
		}

		req := httptest.NewRequest(http.MethodGet, "/api/v1/auth/me", nil)
		req.Header.Set("Authorization", "Bearer ims_tokenme")
		w := httptest.NewRecorder()

		ctrl.GetMe(w, req)

		if w.Code != http.StatusOK {
			t.Errorf("expected 200 OK, got %d", w.Code)
		}
	})
}
