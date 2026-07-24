// Package identity provides authentication, RBAC, and user management capabilities.
package identity

import (
	"net/http"

	"github.com/matheussouza/inframap/modules/identity/controller"
)

// RegisterRoutes registers identity module endpoints to the HTTP router.
func RegisterRoutes(mux *http.ServeMux, ctrl *controller.IdentityController) {
	mux.HandleFunc("POST /api/v1/auth/login", ctrl.Login)
	mux.HandleFunc("POST /api/v1/auth/logout", ctrl.Logout)
	mux.HandleFunc("GET /api/v1/auth/me", ctrl.GetMe)
}
