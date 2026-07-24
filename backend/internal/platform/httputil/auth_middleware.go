package httputil

import (
	"context"
	"net/http"
	"strings"
)

// UserContextKey is the context key for storing authenticated User.
const UserContextKey ContextKey = "authenticated_user"

// PermissionsContextKey is the context key for storing authenticated user permissions.
const PermissionsContextKey ContextKey = "user_permissions"

// SessionValidator defines the contract for validating a session token and returning session details.
type SessionValidator interface {
	ValidateSession(ctx context.Context, token string) (userID string, permissions []string, err error)
}

// AuthMiddleware protects non-public HTTP endpoints and injects authenticated user details into request context.
func AuthMiddleware(validator SessionValidator) func(http.Handler) http.Handler {
	return func(next http.Handler) http.Handler {
		return http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
			path := r.URL.Path

			// Bypass public endpoints
			if isPublicRoute(path) {
				next.ServeHTTP(w, r)
				return
			}

			// Extract token from cookie or Authorization header
			token := extractTokenFromRequest(r)
			if token == "" {
				WriteError(w, r, http.StatusUnauthorized, "UNAUTHENTICATED", "Missing authentication token", nil)
				return
			}

			if validator == nil {
				next.ServeHTTP(w, r)
				return
			}

			userID, perms, err := validator.ValidateSession(r.Context(), token)
			if err != nil {
				WriteError(w, r, http.StatusUnauthorized, "UNAUTHENTICATED", "Invalid or expired session token", nil)
				return
			}

			ctx := context.WithValue(r.Context(), UserContextKey, userID)
			ctx = context.WithValue(ctx, PermissionsContextKey, perms)

			next.ServeHTTP(w, r.WithContext(ctx))
		})
	}
}

func isPublicRoute(path string) bool {
	publicPrefixes := []string{
		"/api/v1/health",
		"/api/v1/setup/",
		"/api/v1/auth/login",
	}

	for _, prefix := range publicPrefixes {
		if strings.HasPrefix(path, prefix) {
			return true
		}
	}
	return false
}

func extractTokenFromRequest(r *http.Request) string {
	if cookie, err := r.Cookie("inframap_session"); err == nil && cookie.Value != "" {
		return cookie.Value
	}

	authHeader := r.Header.Get("Authorization")
	if strings.HasPrefix(authHeader, "Bearer ") {
		return strings.TrimPrefix(authHeader, "Bearer ")
	}

	return ""
}
