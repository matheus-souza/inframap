package e2e

import (
	"bytes"
	"encoding/json"
	"fmt"
	"net/http"
	"net/http/httptest"
	"testing"

	"github.com/google/uuid"
	"github.com/matheussouza/inframap/internal/platform/httputil"
	configdto "github.com/matheussouza/inframap/modules/configuration/dto"
	identitydto "github.com/matheussouza/inframap/modules/identity/dto"
)

// loginAdmin onboards the admin account when the database is fresh and returns its token.
func loginAdmin(t *testing.T, ts *httptest.Server) string {
	t.Helper()
	client := ts.Client()
	const password = "correct-horse-battery-staple-passphrase"

	body, _ := json.Marshal(configdto.OnboardRequest{
		AdminUsername: "admin",
		AdminEmail:    "admin@example.com",
		AdminPassword: password,
		AdminFullName: "System Administrator",
	})
	if resp, err := client.Post(ts.URL+"/api/v1/setup/onboard", "application/json", bytes.NewReader(body)); err == nil {
		_ = resp.Body.Close()
	}

	body, _ = json.Marshal(identitydto.LoginRequest{Username: "admin", Password: password})
	resp, err := client.Post(ts.URL+"/api/v1/auth/login", "application/json", bytes.NewReader(body))
	if err != nil {
		t.Fatalf("login failed: %v", err)
	}
	defer func() { _ = resp.Body.Close() }()
	var env httputil.SuccessEnvelope
	if err := json.NewDecoder(resp.Body).Decode(&env); err != nil {
		t.Fatalf("failed to decode login response: %v", err)
	}
	token, _ := env.Data.(map[string]any)["token"].(string)
	if token == "" {
		t.Fatal("expected a session token")
	}
	return token
}

// apiCall sends an authenticated JSON request and decodes the response envelope.
func apiCall(t *testing.T, ts *httptest.Server, token, method, path string, payload any) (int, map[string]any) {
	t.Helper()
	var body bytes.Buffer
	if payload != nil {
		_ = json.NewEncoder(&body).Encode(payload)
	}
	req, _ := http.NewRequest(method, ts.URL+path, &body)
	req.Header.Set("Authorization", "Bearer "+token)
	req.Header.Set("Content-Type", "application/json")
	resp, err := ts.Client().Do(req)
	if err != nil {
		t.Fatalf("%s %s failed: %v", method, path, err)
	}
	defer func() { _ = resp.Body.Close() }()
	var env map[string]any
	_ = json.NewDecoder(resp.Body).Decode(&env)
	return resp.StatusCode, env
}

func errorCode(env map[string]any) string {
	errBody, _ := env["error"].(map[string]any)
	code, _ := errBody["code"].(string)
	return code
}

func responseTargets(t *testing.T, env map[string]any) []map[string]any {
	t.Helper()
	data, _ := env["data"].(map[string]any)
	raw, ok := data["targets"].([]any)
	if !ok {
		t.Fatalf("expected a targets array in %v", data)
	}
	targets := make([]map[string]any, len(raw))
	for i, item := range raw {
		targets[i], _ = item.(map[string]any)
	}
	return targets
}

// TestE2E_DiscoverySourceTargets exercises RFC-026 §1, §2 and §7 against PostgreSQL: the
// encrypted targets payload round-trips, linked subnets are checked, and rule violations
// come back as 422 with their error code.
func TestE2E_DiscoverySourceTargets(t *testing.T) {
	app, ts := setupTestApp(t)
	defer app.Close()
	defer ts.Close()
	token := loginAdmin(t, ts)

	// The database outlives a test run, so the blocks are randomized to avoid colliding
	// with subnets left by earlier runs.
	seed := uuid.New()
	second := 1 + int(seed[0])%250
	third := int(seed[1]) % 250
	subnetCIDR := fmt.Sprintf("10.%d.%d.0/24", second, third)
	freeCIDR := fmt.Sprintf("10.%d.%d.0/24", second, third+1)
	// Source names are unique, so they carry the same randomized suffix.
	name := func(base string) string { return fmt.Sprintf("%s %d-%d", base, second, third) }

	status, env := apiCall(t, ts, token, http.MethodPost, "/api/v1/subnets", map[string]any{
		"name": "Targets lab " + subnetCIDR,
		"cidr": subnetCIDR,
	})
	if status != http.StatusCreated {
		t.Fatalf("expected 201 creating the subnet, got %d: %v", status, env)
	}
	subnetID, _ := env["data"].(map[string]any)["id"].(string)

	var sourceID string
	t.Run("create with a linked and a free-form target", func(t *testing.T) {
		status, env := apiCall(t, ts, token, http.MethodPost, "/api/v1/discovery/sources", map[string]any{
			"name": name("Multi-target sweep"),
			"type": "icmp_sweep",
			"config": map[string]any{"targets": []map[string]any{
				{"cidr": subnetCIDR, "subnet_id": subnetID},
				{"cidr": freeCIDR},
			}},
		})
		if status != http.StatusCreated {
			t.Fatalf("expected 201, got %d: %v", status, env)
		}
		sourceID, _ = env["data"].(map[string]any)["id"].(string)
		targets := responseTargets(t, env)
		if len(targets) != 2 || targets[0]["subnet_id"] != subnetID || targets[1]["subnet_id"] != nil {
			t.Fatalf("unexpected targets: %v", targets)
		}
		if cidr := env["data"].(map[string]any)["config_cidr"]; cidr != subnetCIDR {
			t.Errorf("expected config_cidr %s, got %v", subnetCIDR, cidr)
		}
	})

	t.Run("targets read back from the encrypted config", func(t *testing.T) {
		status, env := apiCall(t, ts, token, http.MethodGet, "/api/v1/discovery/sources/"+sourceID, nil)
		if status != http.StatusOK {
			t.Fatalf("expected 200, got %d", status)
		}
		if targets := responseTargets(t, env); len(targets) != 2 || targets[1]["cidr"] != freeCIDR {
			t.Fatalf("unexpected targets: %v", targets)
		}
	})

	t.Run("an update without targets keeps them", func(t *testing.T) {
		status, env := apiCall(t, ts, token, http.MethodPut, "/api/v1/discovery/sources/"+sourceID, map[string]any{
			"name":       name("Multi-target sweep renamed"),
			"type":       "icmp_sweep",
			"collectors": []map[string]any{{"type": "icmp_sweep"}},
		})
		if status != http.StatusOK {
			t.Fatalf("expected 200, got %d: %v", status, env)
		}
		if targets := responseTargets(t, env); len(targets) != 2 {
			t.Fatalf("expected the two targets to be kept, got %v", targets)
		}
	})

	t.Run("legacy config.cidr is still accepted", func(t *testing.T) {
		status, env := apiCall(t, ts, token, http.MethodPost, "/api/v1/discovery/sources", map[string]any{
			"name":   name("Legacy sweep"),
			"type":   "icmp_sweep",
			"config": map[string]any{"cidr": "127.0.0.1/32"},
		})
		if status != http.StatusCreated {
			t.Fatalf("expected 201, got %d: %v", status, env)
		}
		if targets := responseTargets(t, env); len(targets) != 1 || targets[0]["cidr"] != "127.0.0.1/32" {
			t.Fatalf("unexpected targets: %v", targets)
		}
	})

	t.Run("a target with host bits is stored as its masked prefix", func(t *testing.T) {
		hostCIDR := fmt.Sprintf("10.%d.%d.5/24", second, third+2)
		maskedCIDR := fmt.Sprintf("10.%d.%d.0/24", second, third+2)
		status, env := apiCall(t, ts, token, http.MethodPost, "/api/v1/discovery/sources", map[string]any{
			"name":   name("Host bits sweep"),
			"type":   "icmp_sweep",
			"config": map[string]any{"targets": []map[string]any{{"cidr": hostCIDR}}},
		})
		if status != http.StatusCreated {
			t.Fatalf("expected 201, got %d: %v", status, env)
		}
		if targets := responseTargets(t, env); len(targets) != 1 || targets[0]["cidr"] != maskedCIDR {
			t.Fatalf("expected the masked target %s, got %v", maskedCIDR, targets)
		}
		if cidr := env["data"].(map[string]any)["config_cidr"]; cidr != maskedCIDR {
			t.Errorf("expected config_cidr %s, got %v", maskedCIDR, cidr)
		}

		id, _ := env["data"].(map[string]any)["id"].(string)
		status, env = apiCall(t, ts, token, http.MethodGet, "/api/v1/discovery/sources/"+id, nil)
		if status != http.StatusOK {
			t.Fatalf("expected 200, got %d", status)
		}
		if targets := responseTargets(t, env); len(targets) != 1 || targets[0]["cidr"] != maskedCIDR {
			t.Fatalf("expected the masked target to be stored, got %v", targets)
		}
	})

	t.Run("provider-only plan needs no target", func(t *testing.T) {
		status, env := apiCall(t, ts, token, http.MethodPost, "/api/v1/discovery/sources", map[string]any{
			"name":       name("Providers only"),
			"collectors": []map[string]any{{"type": "proxmox"}},
		})
		if status != http.StatusCreated {
			t.Fatalf("expected 201, got %d: %v", status, env)
		}
		if targets := responseTargets(t, env); len(targets) != 0 {
			t.Fatalf("expected no targets, got %v", targets)
		}
	})

	rejections := []struct {
		name   string
		config map[string]any
		want   string
	}{
		{name: "sweep without targets", config: nil, want: "error_targets_required"},
		{name: "partial overlap", config: map[string]any{"targets": []map[string]any{
			{"cidr": "10.0.0.0/23"}, {"cidr": "10.0.1.0/24"},
		}}, want: "error_targets_overlap"},
		{name: "unknown subnet", config: map[string]any{"targets": []map[string]any{
			{"cidr": "192.0.2.0/24", "subnet_id": "00000000-0000-0000-0000-000000000001"},
		}}, want: "error_target_subnet_not_found"},
	}
	for _, tc := range rejections {
		t.Run("rejects "+tc.name, func(t *testing.T) {
			status, env := apiCall(t, ts, token, http.MethodPost, "/api/v1/discovery/sources", map[string]any{
				"name":   name("Rejected"),
				"type":   "icmp_sweep",
				"config": tc.config,
			})
			if status != http.StatusUnprocessableEntity || errorCode(env) != tc.want {
				t.Fatalf("expected 422 %s, got %d %v", tc.want, status, env)
			}
		})
	}

	t.Run("a soft-deleted subnet cannot be linked", func(t *testing.T) {
		if status, env := apiCall(t, ts, token, http.MethodDelete, "/api/v1/subnets/"+subnetID, nil); status != http.StatusOK && status != http.StatusNoContent {
			t.Fatalf("expected the subnet to be deleted, got %d: %v", status, env)
		}
		status, env := apiCall(t, ts, token, http.MethodPost, "/api/v1/discovery/sources", map[string]any{
			"name": name("Linked to a deleted subnet"),
			"type": "icmp_sweep",
			"config": map[string]any{"targets": []map[string]any{
				{"cidr": subnetCIDR, "subnet_id": subnetID},
			}},
		})
		if status != http.StatusUnprocessableEntity || errorCode(env) != "error_target_subnet_not_found" {
			t.Fatalf("expected 422 error_target_subnet_not_found, got %d %v", status, env)
		}
	})
}
