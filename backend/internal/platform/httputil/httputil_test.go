package httputil_test

import (
	"bytes"
	"encoding/json"
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
		r.AddCookie(&http.Cookie{Name: httputil.SessionCookieName, Value: "cookie_token"})
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
		r.AddCookie(&http.Cookie{Name: httputil.SessionCookieName, Value: "cookie_val"})
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
