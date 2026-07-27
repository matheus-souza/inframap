// Package realtime provides real-time HTTP event streaming handlers and routing for InfraMap.
package realtime

import (
	"net/http"

	"github.com/matheussouza/inframap/modules/realtime/controller"
)

// RegisterRoutes registers REST & SSE routes for the Realtime module.
func RegisterRoutes(mux *http.ServeMux, ctrl *controller.SSEController) {
	mux.HandleFunc("GET /api/v1/events/stream", ctrl.StreamEvents)
}
