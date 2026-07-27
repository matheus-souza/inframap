package proxmox_test

import (
	"context"
	"encoding/json"
	"net/http"
	"net/http/httptest"
	"testing"

	"github.com/matheussouza/inframap/internal/platform/sdk"
	"github.com/matheussouza/inframap/modules/integrations/providers/proxmox"
)

func TestProxmoxProvider(t *testing.T) {
	provider := proxmox.NewProvider()

	if provider.ID() != "proxmox" {
		t.Errorf("expected ID proxmox, got %s", provider.ID())
	}
	meta := provider.Metadata()
	if meta.Name != "Proxmox VE" {
		t.Errorf("expected Proxmox VE, got %s", meta.Name)
	}
	schema := provider.ConfigSchema()
	if len(schema.Fields) == 0 {
		t.Error("expected non-empty config schema fields")
	}

	t.Run("HealthCheck Success & Failure", func(t *testing.T) {
		ts := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
			if r.URL.Path == "/api2/json/version" {
				w.WriteHeader(http.StatusOK)
				_, _ = w.Write([]byte(`{"data":{"version":"8.1.3"}}`))
				return
			}
			w.WriteHeader(http.StatusNotFound)
		}))
		defer ts.Close()

		validCfg := sdk.ProviderConfig{
			"api_url":      ts.URL,
			"token_id":     "root@pam!token",
			"token_secret": "uuid-secret-1234",
		}

		err := provider.HealthCheck(context.Background(), validCfg)
		if err != nil {
			t.Fatalf("expected nil error on HealthCheck, got %v", err)
		}

		invalidCfg := sdk.ProviderConfig{"api_url": "invalid-url"}
		if err := provider.HealthCheck(context.Background(), invalidCfg); err == nil {
			t.Error("expected error for invalid config")
		}
	})

	t.Run("Discover Success", func(t *testing.T) {
		ts := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
			if r.URL.Path == "/api2/json/nodes" {
				w.WriteHeader(http.StatusOK)
				_ = json.NewEncoder(w).Encode(map[string]interface{}{
					"data": []map[string]interface{}{
						{"node": "pve-01", "status": "online"},
					},
				})
				return
			}
			if r.URL.Path == "/api2/json/nodes/pve-01/qemu" {
				w.WriteHeader(http.StatusOK)
				_ = json.NewEncoder(w).Encode(map[string]interface{}{
					"data": []map[string]interface{}{
						{"vmid": 101, "name": "vm-ubuntu", "status": "running"},
					},
				})
				return
			}
			w.WriteHeader(http.StatusNotFound)
		}))
		defer ts.Close()

		cfg := sdk.ProviderConfig{
			"api_url":      ts.URL,
			"token_id":     "root@pam!token",
			"token_secret": "uuid-secret-1234",
		}

		devices, err := provider.Discover(context.Background(), cfg)
		if err != nil {
			t.Fatalf("expected nil error on Discover, got %v", err)
		}
		if len(devices) != 2 {
			t.Fatalf("expected 2 devices (host + vm), got %d", len(devices))
		}
		if devices[0].Hostname != "pve-01" || devices[1].Hostname != "vm-ubuntu" {
			t.Errorf("unexpected device hostnames: %v", devices)
		}

		errCfg := sdk.ProviderConfig{"api_url": "http://invalid-host-9999.local:8006", "token_id": "a", "token_secret": "b"}
		if _, err := provider.Discover(context.Background(), errCfg); err == nil {
			t.Error("expected error when Discover fails to connect")
		}
	})

	t.Run("Discover QEMU Fetch Failure", func(t *testing.T) {
		ts := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
			if r.URL.Path == "/api2/json/nodes" {
				w.WriteHeader(http.StatusOK)
				_ = json.NewEncoder(w).Encode(map[string]interface{}{
					"data": []map[string]interface{}{
						{"node": "pve-fail", "status": "online"},
					},
				})
				return
			}
			if r.URL.Path == "/api2/json/nodes/pve-fail/qemu" {
				w.WriteHeader(http.StatusInternalServerError)
				return
			}
			w.WriteHeader(http.StatusNotFound)
		}))
		defer ts.Close()

		cfg := sdk.ProviderConfig{"api_url": ts.URL, "token_id": "a", "token_secret": "b"}
		devices, err := provider.Discover(context.Background(), cfg)
		if err != nil {
			t.Fatalf("expected nil error, got %v", err)
		}
		if len(devices) != 1 {
			t.Fatalf("expected 1 device (host only, no VMs), got %d", len(devices))
		}
		if devices[0].Hostname != "pve-fail" {
			t.Errorf("expected hostname pve-fail, got %s", devices[0].Hostname)
		}
	})

	t.Run("Discover QEMU Invalid JSON", func(t *testing.T) {
		ts := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
			if r.URL.Path == "/api2/json/nodes" {
				w.WriteHeader(http.StatusOK)
				_ = json.NewEncoder(w).Encode(map[string]interface{}{
					"data": []map[string]interface{}{
						{"node": "pve-badjson", "status": "online"},
					},
				})
				return
			}
			if r.URL.Path == "/api2/json/nodes/pve-badjson/qemu" {
				w.WriteHeader(http.StatusOK)
				_, _ = w.Write([]byte("not-json"))
				return
			}
			w.WriteHeader(http.StatusNotFound)
		}))
		defer ts.Close()

		cfg := sdk.ProviderConfig{"api_url": ts.URL, "token_id": "a", "token_secret": "b"}
		devices, err := provider.Discover(context.Background(), cfg)
		if err != nil {
			t.Fatalf("expected nil error, got %v", err)
		}
		if len(devices) != 1 {
			t.Fatalf("expected 1 device (host only), got %d", len(devices))
		}
	})

	t.Run("HealthCheck Non-200 HTTP Status", func(t *testing.T) {
		ts := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, _ *http.Request) {
			w.WriteHeader(http.StatusUnauthorized)
		}))
		defer ts.Close()

		cfg := sdk.ProviderConfig{"api_url": ts.URL, "token_id": "a", "token_secret": "b"}
		if err := provider.HealthCheck(context.Background(), cfg); err == nil {
			t.Error("expected error when HealthCheck returns non-200")
		}
	})
}
