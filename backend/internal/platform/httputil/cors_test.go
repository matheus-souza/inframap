package httputil_test

import (
	"net/http"
	"net/http/httptest"
	"testing"

	"github.com/matheussouza/inframap/internal/platform/httputil"
)

func dummyHandler() http.Handler {
	return http.HandlerFunc(func(w http.ResponseWriter, _ *http.Request) {
		w.WriteHeader(http.StatusOK)
	})
}

func TestCORS_EmptyOriginsIsNoOp(t *testing.T) {
	handler := httputil.CORS("")(dummyHandler())

	req := httptest.NewRequest(http.MethodGet, "/api/v1/health", nil)
	req.Header.Set("Origin", "http://localhost:3000")
	rec := httptest.NewRecorder()

	handler.ServeHTTP(rec, req)

	if rec.Code != http.StatusOK {
		t.Fatalf("expected 200, got %d", rec.Code)
	}
	if rec.Header().Get("Access-Control-Allow-Origin") != "" {
		t.Fatal("expected no CORS headers when origins are empty")
	}
}

func TestCORS_AllowedOriginSetsHeaders(t *testing.T) {
	handler := httputil.CORS("http://localhost:3000,http://localhost:8080")(dummyHandler())

	req := httptest.NewRequest(http.MethodGet, "/api/v1/devices", nil)
	req.Header.Set("Origin", "http://localhost:3000")
	rec := httptest.NewRecorder()

	handler.ServeHTTP(rec, req)

	if rec.Code != http.StatusOK {
		t.Fatalf("expected 200, got %d", rec.Code)
	}
	if got := rec.Header().Get("Access-Control-Allow-Origin"); got != "http://localhost:3000" {
		t.Fatalf("expected origin http://localhost:3000, got %q", got)
	}
	if got := rec.Header().Get("Access-Control-Allow-Credentials"); got != "true" {
		t.Fatalf("expected credentials true, got %q", got)
	}
	if got := rec.Header().Get("Vary"); got != "Origin" {
		t.Fatalf("expected Vary: Origin, got %q", got)
	}
}

func TestCORS_DisallowedOriginNoHeaders(t *testing.T) {
	handler := httputil.CORS("http://localhost:3000")(dummyHandler())

	req := httptest.NewRequest(http.MethodGet, "/api/v1/devices", nil)
	req.Header.Set("Origin", "http://evil.example.com")
	rec := httptest.NewRecorder()

	handler.ServeHTTP(rec, req)

	if rec.Code != http.StatusOK {
		t.Fatalf("expected 200, got %d", rec.Code)
	}
	if rec.Header().Get("Access-Control-Allow-Origin") != "" {
		t.Fatal("expected no CORS headers for disallowed origin")
	}
}

func TestCORS_PreflightReturns204(t *testing.T) {
	handler := httputil.CORS("http://localhost:3000")(dummyHandler())

	req := httptest.NewRequest(http.MethodOptions, "/api/v1/devices", nil)
	req.Header.Set("Origin", "http://localhost:3000")
	rec := httptest.NewRecorder()

	handler.ServeHTTP(rec, req)

	if rec.Code != http.StatusNoContent {
		t.Fatalf("expected 204, got %d", rec.Code)
	}
	if got := rec.Header().Get("Access-Control-Allow-Methods"); got == "" {
		t.Fatal("expected Allow-Methods header on preflight")
	}
	if got := rec.Header().Get("Access-Control-Allow-Headers"); got == "" {
		t.Fatal("expected Allow-Headers header on preflight")
	}
	if got := rec.Header().Get("Access-Control-Max-Age"); got != "86400" {
		t.Fatalf("expected Max-Age 86400, got %q", got)
	}
}

func TestCORS_PreflightDisallowedOriginReturns204WithoutCORSHeaders(t *testing.T) {
	handler := httputil.CORS("http://localhost:3000")(dummyHandler())

	req := httptest.NewRequest(http.MethodOptions, "/api/v1/devices", nil)
	req.Header.Set("Origin", "http://evil.example.com")
	rec := httptest.NewRecorder()

	handler.ServeHTTP(rec, req)

	if rec.Code != http.StatusNoContent {
		t.Fatalf("expected 204, got %d", rec.Code)
	}
	if rec.Header().Get("Access-Control-Allow-Origin") != "" {
		t.Fatal("expected no Allow-Origin for disallowed origin preflight")
	}
	if rec.Header().Get("Access-Control-Allow-Methods") != "" {
		t.Fatal("expected no Allow-Methods for disallowed origin preflight")
	}
}

func TestCORS_NoOriginHeaderPassesThrough(t *testing.T) {
	handler := httputil.CORS("http://localhost:3000")(dummyHandler())

	req := httptest.NewRequest(http.MethodGet, "/api/v1/health", nil)
	rec := httptest.NewRecorder()

	handler.ServeHTTP(rec, req)

	if rec.Code != http.StatusOK {
		t.Fatalf("expected 200, got %d", rec.Code)
	}
	if rec.Header().Get("Access-Control-Allow-Origin") != "" {
		t.Fatal("expected no CORS headers when no Origin is sent")
	}
}

func TestCORS_WhitespaceInOriginListIsTrimmed(t *testing.T) {
	handler := httputil.CORS("  http://localhost:3000 , http://localhost:8080  ")(dummyHandler())

	req := httptest.NewRequest(http.MethodGet, "/api/v1/devices", nil)
	req.Header.Set("Origin", "http://localhost:8080")
	rec := httptest.NewRecorder()

	handler.ServeHTTP(rec, req)

	if got := rec.Header().Get("Access-Control-Allow-Origin"); got != "http://localhost:8080" {
		t.Fatalf("expected origin http://localhost:8080, got %q", got)
	}
}

func TestCORS_PreflightDoesNotCallNextHandler(t *testing.T) {
	called := false
	next := http.HandlerFunc(func(_ http.ResponseWriter, _ *http.Request) {
		called = true
	})

	handler := httputil.CORS("http://localhost:3000")(next)

	req := httptest.NewRequest(http.MethodOptions, "/api/v1/devices", nil)
	req.Header.Set("Origin", "http://localhost:3000")
	rec := httptest.NewRecorder()

	handler.ServeHTTP(rec, req)

	if called {
		t.Fatal("expected preflight to short-circuit without calling next handler")
	}
}
