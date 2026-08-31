package discovery_test

import (
	"net/http"
	"testing"

	"github.com/matheussouza/inframap/modules/discovery"
	"github.com/matheussouza/inframap/modules/discovery/controller"
)

func TestRegisterRoutes(t *testing.T) {
	mux := http.NewServeMux()
	ctrl := controller.NewDiscoveryController(nil)

	discovery.RegisterRoutes(mux, ctrl)

	testCases := []struct {
		method  string
		pattern string
	}{
		{"GET", "/api/v1/discovery/sources"},
		{"POST", "/api/v1/discovery/sources"},
		{"GET", "/api/v1/discovery/sources/11111111-1111-1111-1111-111111111111"},
		{"DELETE", "/api/v1/discovery/sources/11111111-1111-1111-1111-111111111111"},
		{"POST", "/api/v1/discovery/sources/11111111-1111-1111-1111-111111111111/run"},
		{"GET", "/api/v1/discovery/sources/11111111-1111-1111-1111-111111111111/runs"},
		{"POST", "/api/v1/discovery/scan"},
		{"GET", "/api/v1/discovery/devices/11111111-1111-1111-1111-111111111111/records"},
	}

	for _, tc := range testCases {
		req, err := http.NewRequest(tc.method, tc.pattern, nil)
		if err != nil {
			t.Fatalf("failed to create request for %s %s: %v", tc.method, tc.pattern, err)
		}
		handler, pattern := mux.Handler(req)
		if handler == nil || pattern == "" {
			t.Errorf("expected route %s %s to be registered, got empty pattern", tc.method, tc.pattern)
		}
	}
}
