// Package inventory registers HTTP routes for devices, staging, and subnets.
package inventory

import (
	"net/http"

	"github.com/matheussouza/inframap/modules/inventory/controller"
)

// RegisterRoutes registers inventory module endpoints to the HTTP router.
func RegisterRoutes(mux *http.ServeMux, ctrl *controller.InventoryController) {
	mux.HandleFunc("GET /api/v1/devices", ctrl.ListDevices)
	mux.HandleFunc("POST /api/v1/devices", ctrl.CreateDevice)
	mux.HandleFunc("GET /api/v1/devices/{id}", ctrl.GetDeviceByID)
	mux.HandleFunc("PUT /api/v1/devices/{id}", ctrl.UpdateDevice)
	mux.HandleFunc("DELETE /api/v1/devices/{id}", ctrl.DeleteDevice)

	mux.HandleFunc("GET /api/v1/devices/staging", ctrl.ListStagingDevices)
	mux.HandleFunc("POST /api/v1/devices/staging/{id}/approve", ctrl.ApproveStagingDevice)
	mux.HandleFunc("POST /api/v1/devices/staging/{id}/dismiss", ctrl.DismissStagingDevice)

	mux.HandleFunc("GET /api/v1/subnets", ctrl.ListSubnets)
	mux.HandleFunc("POST /api/v1/subnets", ctrl.CreateSubnet)
}
