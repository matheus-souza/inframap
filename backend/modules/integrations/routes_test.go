package integrations_test

import (
	"net/http"
	"net/http/httptest"
	"testing"

	"github.com/matheussouza/inframap/modules/integrations"
	integrationsctrl "github.com/matheussouza/inframap/modules/integrations/controller"
	"github.com/matheussouza/inframap/modules/integrations/registry"
)

func TestRegisterRoutes(t *testing.T) {
	reg := registry.NewRegistry()
	ctrl := integrationsctrl.NewIntegrationsController(reg)
	mux := http.NewServeMux()

	integrations.RegisterRoutes(mux, ctrl)

	ts := httptest.NewServer(mux)
	defer ts.Close()

	req, _ := http.NewRequest(http.MethodGet, ts.URL+"/api/v1/integrations/providers", nil)
	resp, err := http.DefaultClient.Do(req)
	if err != nil {
		t.Fatalf("failed to make request: %v", err)
	}
	defer func() {
		_ = resp.Body.Close()
	}()

	if resp.StatusCode != http.StatusOK {
		t.Errorf("expected 200 OK, got %d", resp.StatusCode)
	}
}
