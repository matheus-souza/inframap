package spa_test

import (
	"errors"
	"io"
	"io/fs"
	"net/http"
	"net/http/httptest"
	"testing"
	"testing/fstest"
	"time"

	"github.com/matheussouza/inframap/internal/platform/spa"
)

type statErrorFS struct {
	err error
}

func (f statErrorFS) Open(name string) (fs.File, error) {
	if name == "index.html" {
		return fstest.MapFS{"index.html": {Data: []byte("<html>SPA</html>")}}.Open("index.html")
	}
	return nil, f.err
}

func (f statErrorFS) Stat(name string) (fs.FileInfo, error) {
	if name == "index.html" {
		return fileInfo{name: "index.html", size: 16}, nil
	}
	return nil, f.err
}

func (f statErrorFS) ReadFile(name string) ([]byte, error) {
	if name == "index.html" {
		return []byte("<html>SPA</html>"), nil
	}
	return nil, f.err
}

type fileInfo struct {
	name string
	size int64
}

func (fi fileInfo) Name() string      { return fi.name }
func (fi fileInfo) Size() int64       { return fi.size }
func (fi fileInfo) Mode() fs.FileMode { return 0o444 }
func (fi fileInfo) ModTime() time.Time { return time.Time{} }
func (fi fileInfo) IsDir() bool       { return false }
func (fi fileInfo) Sys() any          { return nil }

func testFS() fstest.MapFS {
	return fstest.MapFS{
		"index.html":    {Data: []byte("<html>SPA</html>")},
		"inframap.js":   {Data: []byte("console.log('app')")},
		"inframap.wasm": {Data: []byte("wasm-binary")},
		"style.css":     {Data: []byte("body{}")},
		"logo.svg":      {Data: []byte("<svg></svg>")},
		"manifest.json": {Data: []byte("{}")},
	}
}

func TestSPAHandler_ServesIndexHTML(t *testing.T) {
	handler := spa.NewSPAHandler(testFS())

	req := httptest.NewRequest(http.MethodGet, "/", nil)
	rec := httptest.NewRecorder()
	handler.ServeHTTP(rec, req)

	if rec.Code != http.StatusOK {
		t.Fatalf("expected 200, got %d", rec.Code)
	}
	body, _ := io.ReadAll(rec.Body)
	if string(body) != "<html>SPA</html>" {
		t.Fatalf("unexpected body: %s", body)
	}
}

func TestSPAHandler_ServesStaticFile(t *testing.T) {
	handler := spa.NewSPAHandler(testFS())

	req := httptest.NewRequest(http.MethodGet, "/inframap.js", nil)
	rec := httptest.NewRecorder()
	handler.ServeHTTP(rec, req)

	if rec.Code != http.StatusOK {
		t.Fatalf("expected 200, got %d", rec.Code)
	}
	body, _ := io.ReadAll(rec.Body)
	if string(body) != "console.log('app')" {
		t.Fatalf("unexpected body: %s", body)
	}
}

func TestSPAHandler_FallbackToIndexForUnknownPaths(t *testing.T) {
	handler := spa.NewSPAHandler(testFS())

	paths := []string{"/dashboard", "/devices/123", "/topology"}
	for _, p := range paths {
		req := httptest.NewRequest(http.MethodGet, p, nil)
		rec := httptest.NewRecorder()
		handler.ServeHTTP(rec, req)

		if rec.Code != http.StatusOK {
			t.Fatalf("path %s: expected 200, got %d", p, rec.Code)
		}
		body, _ := io.ReadAll(rec.Body)
		if string(body) != "<html>SPA</html>" {
			t.Fatalf("path %s: expected SPA fallback, got %s", p, body)
		}
	}
}

func TestSPAHandler_APIPathsReturn404(t *testing.T) {
	handler := spa.NewSPAHandler(testFS())

	req := httptest.NewRequest(http.MethodGet, "/api/v1/unknown", nil)
	rec := httptest.NewRecorder()
	handler.ServeHTTP(rec, req)

	if rec.Code != http.StatusNotFound {
		t.Fatalf("expected 404, got %d", rec.Code)
	}
}

func TestSPAHandler_ContentTypeWASM(t *testing.T) {
	handler := spa.NewSPAHandler(testFS())

	req := httptest.NewRequest(http.MethodGet, "/inframap.wasm", nil)
	rec := httptest.NewRecorder()
	handler.ServeHTTP(rec, req)

	if got := rec.Header().Get("Content-Type"); got != "application/wasm" {
		t.Fatalf("expected application/wasm, got %q", got)
	}
}

func TestSPAHandler_ContentTypeJS(t *testing.T) {
	handler := spa.NewSPAHandler(testFS())

	req := httptest.NewRequest(http.MethodGet, "/inframap.js", nil)
	rec := httptest.NewRecorder()
	handler.ServeHTTP(rec, req)

	if got := rec.Header().Get("Content-Type"); got != "application/javascript" {
		t.Fatalf("expected application/javascript, got %q", got)
	}
}

func TestSPAHandler_ContentTypeCSS(t *testing.T) {
	handler := spa.NewSPAHandler(testFS())

	req := httptest.NewRequest(http.MethodGet, "/style.css", nil)
	rec := httptest.NewRecorder()
	handler.ServeHTTP(rec, req)

	if got := rec.Header().Get("Content-Type"); got != "text/css" {
		t.Fatalf("expected text/css, got %q", got)
	}
}

func TestSPAHandler_ContentTypeSVG(t *testing.T) {
	handler := spa.NewSPAHandler(testFS())

	req := httptest.NewRequest(http.MethodGet, "/logo.svg", nil)
	rec := httptest.NewRecorder()
	handler.ServeHTTP(rec, req)

	if got := rec.Header().Get("Content-Type"); got != "image/svg+xml" {
		t.Fatalf("expected image/svg+xml, got %q", got)
	}
}

func TestSPAHandler_ContentTypeHTML(t *testing.T) {
	handler := spa.NewSPAHandler(testFS())

	req := httptest.NewRequest(http.MethodGet, "/", nil)
	rec := httptest.NewRecorder()
	handler.ServeHTTP(rec, req)

	if got := rec.Header().Get("Content-Type"); got != "text/html; charset=utf-8" {
		t.Fatalf("expected text/html; charset=utf-8, got %q", got)
	}
}

func TestSPAHandler_ContentTypeJSON(t *testing.T) {
	handler := spa.NewSPAHandler(testFS())

	req := httptest.NewRequest(http.MethodGet, "/manifest.json", nil)
	rec := httptest.NewRecorder()
	handler.ServeHTTP(rec, req)

	if got := rec.Header().Get("Content-Type"); got != "application/json" {
		t.Fatalf("expected application/json, got %q", got)
	}
}

func TestSPAHandler_CacheControlIndexNoCache(t *testing.T) {
	handler := spa.NewSPAHandler(testFS())

	req := httptest.NewRequest(http.MethodGet, "/", nil)
	rec := httptest.NewRecorder()
	handler.ServeHTTP(rec, req)

	expected := "no-cache, no-store, must-revalidate"
	if got := rec.Header().Get("Cache-Control"); got != expected {
		t.Fatalf("expected %q for index.html, got %q", expected, got)
	}
}

func TestSPAHandler_CacheControlScriptNoCache(t *testing.T) {
	handler := spa.NewSPAHandler(testFS())

	req := httptest.NewRequest(http.MethodGet, "/inframap.js", nil)
	rec := httptest.NewRecorder()
	handler.ServeHTTP(rec, req)

	expected := "no-cache, must-revalidate"
	if got := rec.Header().Get("Cache-Control"); got != expected {
		t.Fatalf("expected %q for inframap.js, got %q", expected, got)
	}
}

func TestSPAHandler_CacheControlStaticImmutable(t *testing.T) {
	handler := spa.NewSPAHandler(testFS())

	req := httptest.NewRequest(http.MethodGet, "/style.css", nil)
	rec := httptest.NewRecorder()
	handler.ServeHTTP(rec, req)

	expected := "public, max-age=31536000, immutable"
	if got := rec.Header().Get("Cache-Control"); got != expected {
		t.Fatalf("expected %q, got %q", expected, got)
	}
}

func TestSPAHandler_FallbackAlsoGetsCacheNoCache(t *testing.T) {
	handler := spa.NewSPAHandler(testFS())

	req := httptest.NewRequest(http.MethodGet, "/some/deep/route", nil)
	rec := httptest.NewRecorder()
	handler.ServeHTTP(rec, req)

	expected := "no-cache, no-store, must-revalidate"
	if got := rec.Header().Get("Cache-Control"); got != expected {
		t.Fatalf("expected %q for SPA fallback, got %q", expected, got)
	}
}

func TestSPAHandler_MissingStaticAssetReturns404(t *testing.T) {
	handler := spa.NewSPAHandler(testFS())

	paths := []string{"/missing.wasm", "/missing.js", "/missing.css", "/missing.json", "/missing.map"}
	for _, p := range paths {
		req := httptest.NewRequest(http.MethodGet, p, nil)
		rec := httptest.NewRecorder()
		handler.ServeHTTP(rec, req)

		if rec.Code != http.StatusNotFound {
			t.Fatalf("path %s: expected 404 for missing static asset, got %d", p, rec.Code)
		}
	}
}

func TestSPAHandler_WASMCacheRevalidation(t *testing.T) {
	handler := spa.NewSPAHandler(testFS())

	req := httptest.NewRequest(http.MethodGet, "/inframap.wasm", nil)
	rec := httptest.NewRecorder()
	handler.ServeHTTP(rec, req)

	expected := "no-cache, must-revalidate"
	if got := rec.Header().Get("Cache-Control"); got != expected {
		t.Fatalf("expected %q for WASM cache, got %q", expected, got)
	}
}

func TestSPAHandler_StatErrorReturns500(t *testing.T) {
	fsys := statErrorFS{err: errors.New("disk I/O error")}
	handler := spa.NewSPAHandler(fsys)

	req := httptest.NewRequest(http.MethodGet, "/app.wasm", nil)
	rec := httptest.NewRecorder()
	handler.ServeHTTP(rec, req)

	if rec.Code != http.StatusInternalServerError {
		t.Fatalf("expected 500 for non-ErrNotExist stat error, got %d", rec.Code)
	}
}

func TestSPAHandler_MissingIndexHTMLReturns404(t *testing.T) {
	emptyFS := fstest.MapFS{}
	handler := spa.NewSPAHandler(emptyFS)

	req := httptest.NewRequest(http.MethodGet, "/", nil)
	rec := httptest.NewRecorder()
	handler.ServeHTTP(rec, req)

	if rec.Code != http.StatusNotFound {
		t.Fatalf("expected 404 for missing index.html, got %d", rec.Code)
	}
}
