package httputil_test

import (
	"bytes"
	"context"
	"encoding/json"
	"errors"
	"io"
	"net/http"
	"net/http/httptest"
	"testing"

	"github.com/matheussouza/inframap/internal/platform/httputil"
	"github.com/matheussouza/inframap/internal/platform/logger"
)

func TestWriteJSON(t *testing.T) {
	w := httptest.NewRecorder()
	r := httptest.NewRequest(http.MethodGet, "/test", nil)

	httputil.WriteJSON(w, r, http.StatusOK, map[string]string{"status": "ok"})

	if w.Code != http.StatusOK {
		t.Errorf("expected status 200, got %d", w.Code)
	}

	var resp httputil.SuccessEnvelope
	if err := json.NewDecoder(w.Body).Decode(&resp); err != nil {
		t.Fatalf("failed to decode response: %v", err)
	}

	if resp.Meta.RequestID == "" {
		t.Error("expected non-empty request_id in meta")
	}
}

func TestWriteError(t *testing.T) {
	w := httptest.NewRecorder()
	r := httptest.NewRequest(http.MethodGet, "/test", nil)

	httputil.WriteError(w, r, http.StatusBadRequest, "VALIDATION_FAILED", "Invalid field", []httputil.FieldError{
		{Field: "username", Issue: "required"},
	})

	if w.Code != http.StatusBadRequest {
		t.Errorf("expected status 400, got %d", w.Code)
	}

	var resp httputil.ErrorEnvelope
	if err := json.NewDecoder(w.Body).Decode(&resp); err != nil {
		t.Fatalf("failed to decode error response: %v", err)
	}

	if resp.Error.Code != "VALIDATION_FAILED" {
		t.Errorf("expected error code VALIDATION_FAILED, got %s", resp.Error.Code)
	}
}

func TestMiddlewares(t *testing.T) {
	log := logger.New()

	handler := http.HandlerFunc(func(w http.ResponseWriter, _ *http.Request) {
		w.WriteHeader(http.StatusOK)
	})

	stack := httputil.RequestID(httputil.SecurityHeaders(httputil.Recovery(log)(handler)))

	w := httptest.NewRecorder()
	r := httptest.NewRequest(http.MethodGet, "/", nil)

	stack.ServeHTTP(w, r)

	if w.Header().Get("X-Request-ID") == "" {
		t.Error("expected X-Request-ID header to be set")
	}
	if w.Header().Get("X-Frame-Options") != "DENY" {
		t.Error("expected X-Frame-Options DENY")
	}
}

func TestLimitBody(t *testing.T) {
	handler := http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		_, err := io.ReadAll(r.Body)
		if err != nil {
			w.WriteHeader(http.StatusRequestEntityTooLarge)
			return
		}
		w.WriteHeader(http.StatusOK)
	})

	stack := httputil.LimitBody(handler)

	t.Run("small body passes", func(t *testing.T) {
		body := bytes.NewReader([]byte(`{"ok": true}`))
		w := httptest.NewRecorder()
		r := httptest.NewRequest(http.MethodPost, "/", body)
		stack.ServeHTTP(w, r)
		if w.Code != http.StatusOK {
			t.Errorf("expected 200, got %d", w.Code)
		}
	})

	t.Run("oversized body rejected", func(t *testing.T) {
		oversized := make([]byte, httputil.MaxBodySize+1)
		w := httptest.NewRecorder()
		r := httptest.NewRequest(http.MethodPost, "/", bytes.NewReader(oversized))
		stack.ServeHTTP(w, r)
		if w.Code != http.StatusRequestEntityTooLarge {
			t.Errorf("expected 413, got %d", w.Code)
		}
	})
}

func TestExtractToken(t *testing.T) {
	t.Run("nil request returns empty", func(t *testing.T) {
		if tok := httputil.ExtractToken(nil); tok != "" {
			t.Errorf("expected empty token for nil request, got %q", tok)
		}
	})

	t.Run("from cookie", func(t *testing.T) {
		r := httptest.NewRequest(http.MethodGet, "/", nil)
		r.AddCookie(&http.Cookie{Name: httputil.SessionCookieName, Value: "cookie_token", HttpOnly: true, Secure: true})
		if tok := httputil.ExtractToken(r); tok != "cookie_token" {
			t.Errorf("expected cookie_token, got %q", tok)
		}
	})

	t.Run("from bearer header", func(t *testing.T) {
		r := httptest.NewRequest(http.MethodGet, "/", nil)
		r.Header.Set("Authorization", "Bearer header_token")
		if tok := httputil.ExtractToken(r); tok != "header_token" {
			t.Errorf("expected header_token, got %q", tok)
		}
	})

	t.Run("cookie takes precedence over header", func(t *testing.T) {
		r := httptest.NewRequest(http.MethodGet, "/", nil)
		r.AddCookie(&http.Cookie{Name: httputil.SessionCookieName, Value: "cookie_val", HttpOnly: true, Secure: true})
		r.Header.Set("Authorization", "Bearer header_val")
		if tok := httputil.ExtractToken(r); tok != "cookie_val" {
			t.Errorf("expected cookie_val, got %q", tok)
		}
	})

	t.Run("no token returns empty", func(t *testing.T) {
		r := httptest.NewRequest(http.MethodGet, "/", nil)
		if tok := httputil.ExtractToken(r); tok != "" {
			t.Errorf("expected empty token, got %q", tok)
		}
	})
}

type mockValidator struct {
	userID string
	perms  []string
	err    error
}

func (m *mockValidator) ValidateSession(_ context.Context, _ string) (string, []string, error) {
	return m.userID, m.perms, m.err
}

func TestAuthMiddleware(t *testing.T) {
	handler := http.HandlerFunc(func(w http.ResponseWriter, _ *http.Request) {
		w.WriteHeader(http.StatusOK)
	})

	stack := httputil.AuthMiddleware(nil)(handler)

	t.Run("public route bypasses auth", func(t *testing.T) {
		for _, path := range []string{"/api/v1/health", "/api/v1/setup/status", "/api/v1/setup/onboard", "/api/v1/auth/login", "/", "/index.html", "/topology", "/dashboard"} {
			w := httptest.NewRecorder()
			r := httptest.NewRequest(http.MethodGet, path, nil)
			stack.ServeHTTP(w, r)
			if w.Code != http.StatusOK {
				t.Errorf("path %s: expected 200, got %d", path, w.Code)
			}
		}
	})

	t.Run("private route without token returns 401", func(t *testing.T) {
		w := httptest.NewRecorder()
		r := httptest.NewRequest(http.MethodGet, "/api/v1/devices", nil)
		stack.ServeHTTP(w, r)
		if w.Code != http.StatusUnauthorized {
			t.Errorf("expected 401, got %d", w.Code)
		}
	})

	t.Run("private route with token and nil validator passes through", func(t *testing.T) {
		w := httptest.NewRecorder()
		r := httptest.NewRequest(http.MethodGet, "/api/v1/devices", nil)
		r.Header.Set("Authorization", "Bearer some_token")
		stack.ServeHTTP(w, r)
		if w.Code != http.StatusOK {
			t.Errorf("expected 200 with nil validator, got %d", w.Code)
		}
	})

	t.Run("prefix match does not bypass", func(t *testing.T) {
		w := httptest.NewRecorder()
		r := httptest.NewRequest(http.MethodGet, "/api/v1/setup/status/extra", nil)
		stack.ServeHTTP(w, r)
		if w.Code != http.StatusUnauthorized {
			t.Errorf("expected 401 for prefix match bypass attempt, got %d", w.Code)
		}
	})

	t.Run("valid token with validator sets context", func(t *testing.T) {
		v := &mockValidator{userID: "user-123", perms: []string{"admin"}}
		contextHandler := http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
			uid, ok := r.Context().Value(httputil.UserContextKey).(string)
			if !ok || uid != "user-123" {
				w.WriteHeader(http.StatusInternalServerError)
				return
			}
			w.WriteHeader(http.StatusOK)
		})
		authStack := httputil.AuthMiddleware(v)(contextHandler)

		w := httptest.NewRecorder()
		r := httptest.NewRequest(http.MethodGet, "/api/v1/devices", nil)
		r.Header.Set("Authorization", "Bearer valid_token")
		authStack.ServeHTTP(w, r)

		if w.Code != http.StatusOK {
			t.Errorf("expected 200 with valid session, got %d", w.Code)
		}
	})

	t.Run("invalid token with validator returns 401", func(t *testing.T) {
		v := &mockValidator{err: errors.New("expired")}
		authStack := httputil.AuthMiddleware(v)(handler)

		w := httptest.NewRecorder()
		r := httptest.NewRequest(http.MethodGet, "/api/v1/devices", nil)
		r.Header.Set("Authorization", "Bearer expired_token")
		authStack.ServeHTTP(w, r)

		if w.Code != http.StatusUnauthorized {
			t.Errorf("expected 401 for expired session, got %d", w.Code)
		}
	})
}

func TestRecoveryMiddleware(t *testing.T) {
	panicHandler := http.HandlerFunc(func(_ http.ResponseWriter, _ *http.Request) {
		panic("simulated panic")
	})

	stack := httputil.RequestID(httputil.Recovery(nil)(panicHandler))

	w := httptest.NewRecorder()
	r := httptest.NewRequest(http.MethodGet, "/", nil)

	stack.ServeHTTP(w, r)

	if w.Code != http.StatusInternalServerError {
		t.Errorf("expected status 500 on panic recovery, got %d", w.Code)
	}
}
