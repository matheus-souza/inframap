package httputil

import (
	"net/http"
	"strings"
)

// CORS returns middleware that handles Cross-Origin Resource Sharing.
// When allowedOrigins is empty, it returns a no-op passthrough (production mode).
// When set (comma-separated), it validates the Origin header against the allowlist
// and sets appropriate CORS headers with credentials support.
func CORS(allowedOrigins string) func(http.Handler) http.Handler {
	if allowedOrigins == "" {
		return func(next http.Handler) http.Handler { return next }
	}

	origins := make(map[string]struct{})
	for _, o := range strings.Split(allowedOrigins, ",") {
		if trimmed := strings.TrimSpace(o); trimmed != "" {
			origins[trimmed] = struct{}{}
		}
	}

	return func(next http.Handler) http.Handler {
		return http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
			origin := r.Header.Get("Origin")

			if _, ok := origins[origin]; ok {
				w.Header().Set("Access-Control-Allow-Origin", origin)
				w.Header().Set("Access-Control-Allow-Credentials", "true")
				w.Header().Set("Vary", "Origin")
			}

			if r.Method == http.MethodOptions {
				if _, ok := origins[origin]; ok {
					w.Header().Set("Access-Control-Allow-Methods", "GET, POST, PUT, DELETE, OPTIONS")
					w.Header().Set("Access-Control-Allow-Headers", "Content-Type, Authorization, X-Request-ID, Last-Event-ID")
					w.Header().Set("Access-Control-Max-Age", "86400")
				}
				w.WriteHeader(http.StatusNoContent)
				return
			}

			next.ServeHTTP(w, r)
		})
	}
}
