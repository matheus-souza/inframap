package discovery_test

import (
	"net/http"
	"testing"

	"github.com/matheussouza/inframap/modules/discovery"
	"github.com/matheussouza/inframap/modules/discovery/controller"
)

func TestRegisterRoutes(_ *testing.T) {
	mux := http.NewServeMux()
	ctrl := controller.NewDiscoveryController(nil)

	discovery.RegisterRoutes(mux, ctrl)
}
