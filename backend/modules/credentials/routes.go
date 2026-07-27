// Package credentials defines route registration for the Credentials module.
package credentials

import (
	"net/http"

	"github.com/matheussouza/inframap/modules/credentials/controller"
)

// RegisterRoutes registers all Credentials module HTTP REST endpoints onto the provided Mux.
func RegisterRoutes(mux *http.ServeMux, ctrl *controller.CredentialsController) {
	mux.HandleFunc("GET /api/v1/credentials", ctrl.ListCredentials)
	mux.HandleFunc("POST /api/v1/credentials", ctrl.CreateCredential)
	mux.HandleFunc("GET /api/v1/credentials/{id}", ctrl.GetCredentialByID)
	mux.HandleFunc("DELETE /api/v1/credentials/{id}", ctrl.DeleteCredential)
}
