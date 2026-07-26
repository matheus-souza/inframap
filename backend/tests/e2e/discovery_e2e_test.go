package e2e

import (
	"bytes"
	"encoding/json"
	"net/http"
	"testing"

	"github.com/matheussouza/inframap/internal/platform/httputil"
	configdto "github.com/matheussouza/inframap/modules/configuration/dto"
	discdto "github.com/matheussouza/inframap/modules/discovery/dto"
	identitydto "github.com/matheussouza/inframap/modules/identity/dto"
)

func TestE2E_DiscoveryFlow(t *testing.T) {
	app, ts := setupTestApp(t)
	defer app.Close()
	defer ts.Close()

	client := ts.Client()

	var adminToken string
	t.Run("Step 1: Onboard and Login Admin", func(t *testing.T) {
		onboardPayload := configdto.OnboardRequest{
			AdminUsername:    "admin",
			AdminEmail:       "admin@example.com",
			AdminPassword:    "correct-horse-battery-staple-passphrase",
			AdminFullName:    "System Administrator",
			TelemetryEnabled: false,
		}
		body, _ := json.Marshal(onboardPayload)
		resp, err := client.Post(ts.URL+"/api/v1/setup/onboard", "application/json", bytes.NewReader(body))
		if err == nil {
			_ = resp.Body.Close()
		}

		loginPayload := identitydto.LoginRequest{
			Username: "admin",
			Password: "correct-horse-battery-staple-passphrase",
		}
		body, _ = json.Marshal(loginPayload)
		resp, err = client.Post(ts.URL+"/api/v1/auth/login", "application/json", bytes.NewReader(body))
		if err != nil {
			t.Fatalf("login failed: %v", err)
		}
		defer func() { _ = resp.Body.Close() }()

		if resp.StatusCode != http.StatusOK {
			t.Fatalf("expected 200 OK on login, got %d", resp.StatusCode)
		}

		var env httputil.SuccessEnvelope
		if err := json.NewDecoder(resp.Body).Decode(&env); err != nil {
			t.Fatalf("failed to decode login response: %v", err)
		}
		dataMap, ok := env.Data.(map[string]any)
		if !ok {
			t.Fatal("expected data map in login response")
		}
		tok, ok := dataMap["token"].(string)
		if !ok || tok == "" {
			t.Fatal("expected non-empty token string in login response")
		}
		adminToken = tok
	})

	var sourceID string
	t.Run("Step 2: Create Discovery Source", func(t *testing.T) {
		createPayload := discdto.CreateDiscoverySourceRequest{
			Name: "Cluster Proxmox VE",
			Type: "proxmox",
		}
		body, _ := json.Marshal(createPayload)

		req, _ := http.NewRequest(http.MethodPost, ts.URL+"/api/v1/discovery/sources", bytes.NewReader(body))
		req.Header.Set("Authorization", "Bearer "+adminToken)
		req.Header.Set("Content-Type", "application/json")

		resp, err := client.Do(req)
		if err != nil {
			t.Fatalf("create discovery source request failed: %v", err)
		}
		defer func() { _ = resp.Body.Close() }()

		if resp.StatusCode != http.StatusCreated {
			t.Fatalf("expected 201 Created, got %d", resp.StatusCode)
		}

		var env httputil.SuccessEnvelope
		if err := json.NewDecoder(resp.Body).Decode(&env); err != nil {
			t.Fatalf("failed to decode response: %v", err)
		}

		dataMap, ok := env.Data.(map[string]any)
		if !ok {
			t.Fatal("expected data map in create discovery source response")
		}
		id, ok := dataMap["id"].(string)
		if !ok || id == "" {
			t.Fatal("expected non-empty id string in create discovery source response")
		}
		sourceID = id
	})

	t.Run("Step 3: List Discovery Sources", func(t *testing.T) {
		req, _ := http.NewRequest(http.MethodGet, ts.URL+"/api/v1/discovery/sources", nil)
		req.Header.Set("Authorization", "Bearer "+adminToken)

		resp, err := client.Do(req)
		if err != nil {
			t.Fatalf("list discovery sources request failed: %v", err)
		}
		defer func() { _ = resp.Body.Close() }()

		if resp.StatusCode != http.StatusOK {
			t.Fatalf("expected 200 OK, got %d", resp.StatusCode)
		}
	})

	t.Run("Step 4: Trigger Discovery Source Run", func(t *testing.T) {
		req, _ := http.NewRequest(http.MethodPost, ts.URL+"/api/v1/discovery/sources/"+sourceID+"/run", nil)
		req.Header.Set("Authorization", "Bearer "+adminToken)

		resp, err := client.Do(req)
		if err != nil {
			t.Fatalf("trigger discovery run request failed: %v", err)
		}
		defer func() { _ = resp.Body.Close() }()

		if resp.StatusCode != http.StatusOK {
			t.Fatalf("expected 200 OK on trigger run, got %d", resp.StatusCode)
		}
	})
}
