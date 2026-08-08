// Package discovery registers HTTP endpoints for the Discovery Engine module.
package discovery

import (
	"net/http"

	"github.com/matheussouza/inframap/modules/discovery/controller"
)

// RegisterRoutes registers discovery module endpoints on the HTTP ServeMux.
func RegisterRoutes(mux *http.ServeMux, ctrl *controller.DiscoveryController) {
	mux.HandleFunc("GET /api/v1/discovery/sources", ctrl.ListSources)
	mux.HandleFunc("POST /api/v1/discovery/sources", ctrl.CreateSource)
	mux.HandleFunc("GET /api/v1/discovery/sources/{id}", ctrl.GetSourceByID)
	mux.HandleFunc("POST /api/v1/discovery/sources/{id}/run", ctrl.TriggerRun)
	mux.HandleFunc("POST /api/v1/discovery/scan", ctrl.TriggerScan)
	mux.HandleFunc("GET /api/v1/discovery/devices/{id}/records", ctrl.ListRecordsByDevice)
}

