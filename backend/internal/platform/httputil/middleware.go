package httputil

import (
	"context"
	"log/slog"
	"net/http"
	"strings"

	"github.com/google/uuid"
)

// RequestID middleware injects a UUIDv7 request ID into the context and response header.
func RequestID(next http.Handler) http.Handler {
	return http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		reqID := r.Header.Get(RequestIDHeader)
		if reqID == "" {
			u, err := uuid.NewV7()
			if err != nil {
				reqID = uuid.New().String()
			} else {
				reqID = u.String()
			}
		}

		w.Header().Set(RequestIDHeader, reqID)
		ctx := context.WithValue(r.Context(), RequestIDContextKey, reqID)
		next.ServeHTTP(w, r.WithContext(ctx))
	})
}

// SecurityHeaders middleware applies RFC-008 strict security headers.
func SecurityHeaders(next http.Handler) http.Handler {
	return http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		w.Header().Set("X-Frame-Options", "DENY")
		w.Header().Set("X-Content-Type-Options", "nosniff")
		w.Header().Set("Referrer-Policy", "strict-origin-when-cross-origin")
		next.ServeHTTP(w, r)
	})
}

// Recovery middleware handles panics and returns a 500 error envelope.
func Recovery(logger *slog.Logger) func(http.Handler) http.Handler {
	return func(next http.Handler) http.Handler {
		return http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
			defer func() {
				if rec := recover(); rec != nil {
					if logger != nil {
						logger.Error("HTTP handler panic recovered",
							slog.Any("panic", rec),
							slog.String("request_id", sanitizeLogInput(GetRequestID(r))),
							slog.String("path", sanitizeLogInput(r.URL.Path)),
						)
					}
					WriteError(w, r, http.StatusInternalServerError, "INTERNAL_ERROR", "An unexpected server error occurred", nil)
				}
			}()
			next.ServeHTTP(w, r)
		})
	}
}

func sanitizeLogInput(input string) string {
	clean := strings.ReplaceAll(input, "\n", "")
	clean = strings.ReplaceAll(clean, "\r", "")
	if len(clean) > 256 {
		return clean[:256]
	}
	return clean
}
