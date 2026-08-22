// Package spa serves embedded frontend static files with SPA fallback routing.
package spa

import (
	"errors"
	"io/fs"
	"net/http"
	"path/filepath"
	"strings"
)

// NewSPAHandler returns an http.Handler that serves static files from the
// given embedded filesystem. Requests that don't match a file and don't
// target the API are served index.html for client-side SPA routing.
func NewSPAHandler(fsys fs.FS) http.Handler {
	fileServer := http.FileServer(http.FS(fsys))

	return http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		cleaned := filepath.Clean("/" + strings.Trim(r.URL.Path, "/"))
		urlPath := strings.TrimPrefix(filepath.ToSlash(cleaned), "/")

		if strings.HasPrefix(r.URL.Path, "/api/") {
			http.NotFound(w, r)
			return
		}

		if urlPath == "" {
			urlPath = "index.html"
		}

		_, err := fs.Stat(fsys, urlPath)
		if err != nil {
			if isStaticAsset(urlPath) {
				if errors.Is(err, fs.ErrNotExist) {
					http.NotFound(w, r)
				} else {
					http.Error(w, http.StatusText(http.StatusInternalServerError), http.StatusInternalServerError)
				}
				return
			}
			urlPath = "index.html"
		}

		setCacheHeaders(w, urlPath)
		setContentType(w, urlPath)

		if urlPath == "index.html" {
			content, readErr := fs.ReadFile(fsys, "index.html")
			if readErr != nil {
				http.NotFound(w, r)
				return
			}
			_, _ = w.Write(content)
			return
		}

		fileServer.ServeHTTP(w, r)
	})
}

func setCacheHeaders(w http.ResponseWriter, filePath string) {
	if filePath == "index.html" {
		w.Header().Set("Cache-Control", "no-cache, no-store, must-revalidate")
		return
	}
	ext := filepath.Ext(filePath)
	if ext == ".wasm" || ext == ".js" || ext == ".json" {
		w.Header().Set("Cache-Control", "no-cache, must-revalidate")
		return
	}
	w.Header().Set("Cache-Control", "public, max-age=31536000, immutable")
}

func isStaticAsset(filePath string) bool {
	switch filepath.Ext(filePath) {
	case ".wasm", ".js", ".css", ".json", ".map",
		".svg", ".png", ".jpg", ".ico", ".woff", ".woff2", ".ttf":
		return true
	}
	return false
}

func setContentType(w http.ResponseWriter, filePath string) {
	ext := filepath.Ext(filePath)
	switch ext {
	case ".wasm":
		w.Header().Set("Content-Type", "application/wasm")
	case ".js":
		w.Header().Set("Content-Type", "application/javascript")
	case ".html":
		w.Header().Set("Content-Type", "text/html; charset=utf-8")
	case ".css":
		w.Header().Set("Content-Type", "text/css")
	case ".svg":
		w.Header().Set("Content-Type", "image/svg+xml")
	case ".json":
		w.Header().Set("Content-Type", "application/json")
	}
}
