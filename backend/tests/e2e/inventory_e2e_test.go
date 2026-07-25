package e2e

import (
	"bytes"
	"encoding/json"
	"net/http"
	"testing"

	"github.com/matheussouza/inframap/internal/platform/httputil"
	configdto "github.com/matheussouza/inframap/modules/configuration/dto"
	identitydto "github.com/matheussouza/inframap/modules/identity/dto"
	invdto "github.com/matheussouza/inframap/modules/inventory/dto"
)

func TestE2E_InventoryFlow(t *testing.T) {
	app, ts := setupTestApp(t)
	defer app.Close()
	defer ts.Close()

	client := ts.Client()

	// 1. Ensure System Onboarded & Login as Admin
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

	// 2. Create Device
	var deviceID string
	t.Run("Step 2: Create Device", func(t *testing.T) {
		createPayload := invdto.CreateDeviceRequest{
			Hostname:     "pve-node-01.home.arpa",
			IPAddress:    "192.168.1.50",
			MACAddress:   "bc:24:11:22:33:44",
			Manufacturer: "Dell Inc.",
			Model:        "PowerEdge R740",
			DeviceType:   "hypervisor",
		}
		body, _ := json.Marshal(createPayload)

		req, _ := http.NewRequest(http.MethodPost, ts.URL+"/api/v1/devices", bytes.NewReader(body))
		req.Header.Set("Authorization", "Bearer "+adminToken)
		req.Header.Set("Content-Type", "application/json")

		resp, err := client.Do(req)
		if err != nil {
			t.Fatalf("create device request failed: %v", err)
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
			t.Fatal("expected data map in create device response")
		}
		id, ok := dataMap["id"].(string)
		if !ok || id == "" {
			t.Fatal("expected non-empty id string in create device response")
		}
		deviceID = id
	})

	// 3. List Devices with Offset Pagination
	t.Run("Step 3: List Devices", func(t *testing.T) {
		req, _ := http.NewRequest(http.MethodGet, ts.URL+"/api/v1/devices?page=1&per_page=10", nil)
		req.Header.Set("Authorization", "Bearer "+adminToken)

		resp, err := client.Do(req)
		if err != nil {
			t.Fatalf("list devices request failed: %v", err)
		}
		defer func() { _ = resp.Body.Close() }()

		if resp.StatusCode != http.StatusOK {
			t.Fatalf("expected 200 OK, got %d", resp.StatusCode)
		}
	})

	// 4. Update Device with User-Lock
	t.Run("Step 4: Update Device (Appends User-Lock)", func(t *testing.T) {
		newName := "pve-primary.home.arpa"
		updatePayload := invdto.UpdateDeviceRequest{
			Hostname: &newName,
		}
		body, _ := json.Marshal(updatePayload)

		req, _ := http.NewRequest(http.MethodPut, ts.URL+"/api/v1/devices/"+deviceID, bytes.NewReader(body))
		req.Header.Set("Authorization", "Bearer "+adminToken)
		req.Header.Set("Content-Type", "application/json")

		resp, err := client.Do(req)
		if err != nil {
			t.Fatalf("update device request failed: %v", err)
		}
		defer func() { _ = resp.Body.Close() }()

		if resp.StatusCode != http.StatusOK {
			t.Fatalf("expected 200 OK, got %d", resp.StatusCode)
		}
	})

	// 5. Soft-Delete Device
	t.Run("Step 5: Soft-Delete Device", func(t *testing.T) {
		req, _ := http.NewRequest(http.MethodDelete, ts.URL+"/api/v1/devices/"+deviceID, nil)
		req.Header.Set("Authorization", "Bearer "+adminToken)

		resp, err := client.Do(req)
		if err != nil {
			t.Fatalf("delete device request failed: %v", err)
		}
		defer func() { _ = resp.Body.Close() }()

		if resp.StatusCode != http.StatusOK {
			t.Fatalf("expected 200 OK on soft-delete, got %d", resp.StatusCode)
		}
	})
}
