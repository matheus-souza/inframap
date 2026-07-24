// Package configuration provides system state management and onboarding capabilities.
package configuration

import (
	"net/http"

	"github.com/matheussouza/inframap/modules/configuration/controller"
)

// RegisterRoutes registers configuration module endpoints to the router.
func RegisterRoutes(mux *http.ServeMux, ctrl *controller.SetupController) {
	mux.HandleFunc("GET /api/v1/setup/status", ctrl.GetStatus)
	mux.HandleFunc("POST /api/v1/setup/onboard", ctrl.Onboard)
}
