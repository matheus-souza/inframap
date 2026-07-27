package docker_test

import (
	"context"
	"encoding/json"
	"net/http"
	"net/http/httptest"
	"testing"

	"github.com/matheussouza/inframap/internal/platform/sdk"
	"github.com/matheussouza/inframap/modules/integrations/providers/docker"
)

func TestDockerProvider(t *testing.T) {
	provider := docker.NewProvider()

	if provider.ID() != "docker" {
		t.Errorf("expected ID docker, got %s", provider.ID())
	}
	meta := provider.Metadata()
	if meta.Name != "Docker Engine" {
		t.Errorf("expected Docker Engine, got %s", meta.Name)
	}
	schema := provider.ConfigSchema()
	if len(schema.Fields) == 0 {
		t.Error("expected non-empty config schema fields")
	}

	t.Run("HealthCheck Success & Failure", func(t *testing.T) {
		ts := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
			if r.URL.Path == "/_ping" {
				w.WriteHeader(http.StatusOK)
				_, _ = w.Write([]byte("OK"))
				return
			}
			w.WriteHeader(http.StatusNotFound)
		}))
		defer ts.Close()

		validCfg := sdk.ProviderConfig{"api_url": ts.URL}
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
			if r.URL.Path == "/info" {
				w.WriteHeader(http.StatusOK)
				_ = json.NewEncoder(w).Encode(map[string]interface{}{
					"Name":            "docker-host-01",
					"OSType":          "linux",
					"OperatingSystem": "Ubuntu 22.04 LTS",
				})
				return
			}
			if r.URL.Path == "/containers/json" {
				w.WriteHeader(http.StatusOK)
				_ = json.NewEncoder(w).Encode([]map[string]interface{}{
					{
						"Id":     "c998877665544332211",
						"Names":  []string{"/nginx-app"},
						"Image":  "nginx:alpine",
						"State":  "running",
						"Status": "Up 2 hours",
					},
				})
				return
			}
			w.WriteHeader(http.StatusNotFound)
		}))
		defer ts.Close()

		cfg := sdk.ProviderConfig{"api_url": ts.URL}

		devices, err := provider.Discover(context.Background(), cfg)
		if err != nil {
			t.Fatalf("expected nil error on Discover, got %v", err)
		}
		if len(devices) != 2 {
			t.Fatalf("expected 2 devices (host + container), got %d", len(devices))
		}
		if devices[0].Hostname != "docker-host-01" || devices[1].Hostname != "nginx-app" {
			t.Errorf("unexpected device hostnames: %v", devices)
		}

		errCfg := sdk.ProviderConfig{"api_url": "http://invalid-docker-host.local:2375"}
		if _, err := provider.Discover(context.Background(), errCfg); err == nil {
			t.Error("expected error when Discover fails to connect")
		}
	})

	t.Run("Discover Info Failure and Short Container ID", func(t *testing.T) {
		ts := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
			if r.URL.Path == "/info" {
				w.WriteHeader(http.StatusInternalServerError)
				return
			}
			if r.URL.Path == "/containers/json" {
				w.WriteHeader(http.StatusOK)
				_ = json.NewEncoder(w).Encode([]map[string]interface{}{
					{
						"Id":    "abc",
						"Names": []string{},
						"Image": "busybox",
						"State": "running",
					},
				})
				return
			}
			w.WriteHeader(http.StatusNotFound)
		}))
		defer ts.Close()

		cfg := sdk.ProviderConfig{"api_url": ts.URL}
		devices, err := provider.Discover(context.Background(), cfg)
		if err != nil {
			t.Fatalf("expected nil error, got %v", err)
		}
		if len(devices) != 1 {
			t.Fatalf("expected 1 device (container only, no host), got %d", len(devices))
		}
		if devices[0].Hostname != "abc" {
			t.Errorf("expected short ID as hostname, got %s", devices[0].Hostname)
		}
	})

	t.Run("Discover Info Invalid JSON", func(t *testing.T) {
		ts := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
			if r.URL.Path == "/info" {
				w.WriteHeader(http.StatusOK)
				_, _ = w.Write([]byte("not-json"))
				return
			}
			if r.URL.Path == "/containers/json" {
				w.WriteHeader(http.StatusOK)
				_, _ = w.Write([]byte("[]"))
				return
			}
			w.WriteHeader(http.StatusNotFound)
		}))
		defer ts.Close()

		cfg := sdk.ProviderConfig{"api_url": ts.URL}
		devices, err := provider.Discover(context.Background(), cfg)
		if err != nil {
			t.Fatalf("expected nil error, got %v", err)
		}
		if len(devices) != 0 {
			t.Errorf("expected 0 devices when /info returns invalid JSON, got %d", len(devices))
		}
	})

	t.Run("HealthCheck Non-200 HTTP Status", func(t *testing.T) {
		ts := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, _ *http.Request) {
			w.WriteHeader(http.StatusInternalServerError)
		}))
		defer ts.Close()

		cfg := sdk.ProviderConfig{"api_url": ts.URL}
		if err := provider.HealthCheck(context.Background(), cfg); err == nil {
			t.Error("expected error when HealthCheck returns non-200")
		}
	})
}
