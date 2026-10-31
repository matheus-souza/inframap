// Package dns registers HTTP routes for DNS records.
package dns

import (
	"net/http"

	"github.com/matheussouza/inframap/modules/dns/controller"
)

// RegisterRoutes registers DNS module endpoints to the HTTP router.
func RegisterRoutes(mux *http.ServeMux, ctrl *controller.DNSController) {
	mux.HandleFunc("GET /api/v1/dns-records", ctrl.ListDNSRecords)
	mux.HandleFunc("POST /api/v1/dns-records", ctrl.CreateDNSRecord)
	mux.HandleFunc("GET /api/v1/dns-records/{id}", ctrl.GetDNSRecordByID)
	mux.HandleFunc("PUT /api/v1/dns-records/{id}", ctrl.UpdateDNSRecord)
	mux.HandleFunc("DELETE /api/v1/dns-records/{id}", ctrl.DeleteDNSRecord)
	mux.HandleFunc("GET /api/v1/devices/{id}/dns-records", ctrl.ListDNSRecordsByDevice)
}
