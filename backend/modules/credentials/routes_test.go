package credentials_test

import (
	"net/http"
	"testing"

	"github.com/matheussouza/inframap/modules/credentials"
	"github.com/matheussouza/inframap/modules/credentials/controller"
)

func TestRegisterRoutes(_ *testing.T) {
	mux := http.NewServeMux()
	ctrl := controller.NewCredentialsController(nil)

	credentials.RegisterRoutes(mux, ctrl)
}
