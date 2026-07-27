// Package integrations implements external integration provider management for InfraMap.
package integrations

import (
	"net/http"

	"github.com/matheussouza/inframap/modules/integrations/controller"
)

// RegisterRoutes registers REST routes for the Integrations module.
func RegisterRoutes(mux *http.ServeMux, ctrl *controller.IntegrationsController) {
	mux.HandleFunc("GET /api/v1/integrations/providers", ctrl.ListProviders)
	mux.HandleFunc("POST /api/v1/integrations/providers/{id}/health", ctrl.TestProviderHealth)
}
