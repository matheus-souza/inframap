package e2e

import (
	"fmt"
	"net/http"
	"testing"

	"github.com/google/uuid"
)

func impactSources(t *testing.T, env map[string]any) []map[string]any {
	t.Helper()
	data, _ := env["data"].(map[string]any)
	impact, _ := data["impact"].(map[string]any)
	raw, ok := impact["discovery_sources"].([]any)
	if !ok {
		t.Fatalf("expected a discovery_sources array in %v", data)
	}
	sources := make([]map[string]any, len(raw))
	for i, item := range raw {
		sources[i], _ = item.(map[string]any)
	}
	return sources
}

// TestE2E_SubnetTargetLinks exercises RFC-026 §6 and §7 through the wired application: source
// responses name the Subnet behind a linked target, the Subnet's deletion impact lists the
// active sources linked to it, and deleting the Subnet leaves those sources unchanged.
func TestE2E_SubnetTargetLinks(t *testing.T) {
	app, ts := setupTestApp(t)
	defer app.Close()
	defer ts.Close()
	token := loginAdmin(t, ts)

	// The database outlives a test run, so blocks and names are randomized.
	seed := uuid.New()
	second := 1 + int(seed[0])%250
	third := int(seed[1]) % 250
	subnetCIDR := fmt.Sprintf("10.%d.%d.0/24", second, third)
	freeCIDR := fmt.Sprintf("10.%d.%d.0/24", second, third+1)
	suffix := fmt.Sprintf("%d-%d-%s", second, third, seed.String()[:8])
	subnetName := "Linked lab " + suffix

	status, env := apiCall(t, ts, token, http.MethodPost, "/api/v1/subnets", map[string]any{
		"name": subnetName,
		"cidr": subnetCIDR,
	})
	if status != http.StatusCreated {
		t.Fatalf("expected 201 creating the subnet, got %d: %v", status, env)
	}
	subnetID, _ := env["data"].(map[string]any)["id"].(string)

	createSource := func(name string, targets []map[string]any) string {
		t.Helper()
		status, env := apiCall(t, ts, token, http.MethodPost, "/api/v1/discovery/sources", map[string]any{
			"name":   name + " " + suffix,
			"type":   "icmp_sweep",
			"config": map[string]any{"targets": targets},
		})
		if status != http.StatusCreated {
			t.Fatalf("expected 201 creating %s, got %d: %v", name, status, env)
		}
		id, _ := env["data"].(map[string]any)["id"].(string)
		return id
	}

	linkedID := createSource("Linked sweep", []map[string]any{
		{"cidr": subnetCIDR, "subnet_id": subnetID},
		{"cidr": freeCIDR},
	})
	createSource("Free sweep", []map[string]any{{"cidr": freeCIDR}})
	removedID := createSource("Removed linked sweep", []map[string]any{{"cidr": subnetCIDR, "subnet_id": subnetID}})
	if status, env := apiCall(t, ts, token, http.MethodDelete, "/api/v1/discovery/sources/"+removedID, nil); status != http.StatusOK {
		t.Fatalf("expected 200 deleting a source, got %d: %v", status, env)
	}

	t.Run("source response names the linked subnet", func(t *testing.T) {
		status, env := apiCall(t, ts, token, http.MethodGet, "/api/v1/discovery/sources/"+linkedID, nil)
		if status != http.StatusOK {
			t.Fatalf("expected 200, got %d", status)
		}
		targets := responseTargets(t, env)
		if len(targets) != 2 {
			t.Fatalf("expected 2 targets, got %v", targets)
		}
		if targets[0]["subnet_name"] != subnetName || targets[0]["subnet_deleted"] != false {
			t.Errorf("expected the linked target named %q, got %v", subnetName, targets[0])
		}
		if value, present := targets[1]["subnet_name"]; !present || value != nil || targets[1]["subnet_deleted"] != false {
			t.Errorf("expected a free-form target with subnet_name null, got %v", targets[1])
		}
	})

	t.Run("deletion impact lists only the active linked source", func(t *testing.T) {
		status, env := apiCall(t, ts, token, http.MethodGet, "/api/v1/subnets/"+subnetID+"/deletion-impact", nil)
		if status != http.StatusOK {
			t.Fatalf("expected 200, got %d: %v", status, env)
		}
		sources := impactSources(t, env)
		if len(sources) != 1 || sources[0]["id"] != linkedID || sources[0]["name"] != "Linked sweep "+suffix {
			t.Fatalf("expected only the linked source, got %v", sources)
		}
	})

	t.Run("deleting the subnet keeps the source target and flags it", func(t *testing.T) {
		status, env := apiCall(t, ts, token, http.MethodDelete, "/api/v1/subnets/"+subnetID, nil)
		if status != http.StatusOK {
			t.Fatalf("expected 200 deleting the subnet, got %d: %v", status, env)
		}
		if sources := impactSources(t, env); len(sources) != 1 || sources[0]["id"] != linkedID {
			t.Errorf("expected the delete response to report the linked source, got %v", sources)
		}

		// Read by id: listing would also decrypt sources other tests stored with their own key.
		status, env = apiCall(t, ts, token, http.MethodGet, "/api/v1/discovery/sources/"+linkedID, nil)
		if status != http.StatusOK {
			t.Fatalf("expected the linked source to remain, got %d: %v", status, env)
		}
		targets := responseTargets(t, env)
		if len(targets) != 2 || targets[0]["subnet_id"] != subnetID || targets[0]["cidr"] != subnetCIDR {
			t.Fatalf("expected the source to keep its linked target, got %v", targets)
		}
		if targets[0]["subnet_deleted"] != true || targets[0]["subnet_name"] != subnetName {
			t.Errorf("expected the target flagged deleted and still named, got %v", targets[0])
		}
	})
}
