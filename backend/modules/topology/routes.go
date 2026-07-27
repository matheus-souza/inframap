// Package topology implements the Network Topology & Mapping Engine for InfraMap.
package topology

import (
	"net/http"

	"github.com/matheussouza/inframap/modules/topology/controller"
)

// RegisterRoutes registers all topology HTTP REST API endpoints on the provided ServeMux.
func RegisterRoutes(mux *http.ServeMux, ctrl *controller.TopologyController) {
	mux.HandleFunc("GET /api/v1/topology/graph", ctrl.GetGraph)
	mux.HandleFunc("GET /api/v1/topology/links", ctrl.ListLinks)
	mux.HandleFunc("GET /api/v1/topology/links/{id}", ctrl.GetLinkByID)
	mux.HandleFunc("POST /api/v1/topology/links", ctrl.CreateLink)
	mux.HandleFunc("DELETE /api/v1/topology/links/{id}", ctrl.DeleteLink)
}
