package docker_test

import (
	"context"
	"crypto/rand"
	"crypto/rsa"
	"crypto/tls"
	"crypto/x509"
	"crypto/x509/pkix"
	"encoding/json"
	"encoding/pem"
	"fmt"
	"math/big"
	"net"
	"net/http"
	"net/http/httptest"
	"os"
	"path/filepath"
	"testing"
	"time"

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
		t.Fatal("expected non-empty config schema fields")
	}

	fieldKeys := make(map[string]bool)
	for _, f := range schema.Fields {
		fieldKeys[f.Key] = true
	}
	for _, expectedKey := range []string{"socket_path", "tcp_url", "tls_cert", "tls_key", "tls_ca", "verify_ssl"} {
		if !fieldKeys[expectedKey] {
			t.Errorf("expected config schema to contain key %q", expectedKey)
		}
	}

	t.Run("HealthCheck Success & Fallback & Failure", func(t *testing.T) {
		t.Run("Success via _ping", func(t *testing.T) {
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
		})

		t.Run("Success via /info fallback when _ping is 404", func(t *testing.T) {
			ts := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
				if r.URL.Path == "/_ping" {
					w.WriteHeader(http.StatusNotFound)
					return
				}
				if r.URL.Path == "/info" {
					w.WriteHeader(http.StatusOK)
					_ = json.NewEncoder(w).Encode(map[string]interface{}{"ID": "test-id", "Name": "node-01"})
					return
				}
				w.WriteHeader(http.StatusNotFound)
			}))
			defer ts.Close()

			cfg := sdk.ProviderConfig{"tcp_url": ts.URL}
			if err := provider.HealthCheck(context.Background(), cfg); err != nil {
				t.Fatalf("expected fallback to /info to succeed, got %v", err)
			}
		})

		t.Run("Failure when both _ping and /info fail", func(t *testing.T) {
			ts := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, _ *http.Request) {
				w.WriteHeader(http.StatusInternalServerError)
			}))
			defer ts.Close()

			cfg := sdk.ProviderConfig{"tcp_url": ts.URL}
			if err := provider.HealthCheck(context.Background(), cfg); err == nil {
				t.Error("expected error when health check endpoints return non-200")
			}
		})

		t.Run("Failure for invalid config format", func(t *testing.T) {
			invalidCfg := sdk.ProviderConfig{"tcp_url": "ftp://invalid-url"}
			if err := provider.HealthCheck(context.Background(), invalidCfg); err == nil {
				t.Error("expected error for invalid config")
			}
		})
	})

	t.Run("Discover Success with Host and Containers", func(t *testing.T) {
		ts := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
			if r.URL.Path == "/info" {
				w.WriteHeader(http.StatusOK)
				_ = json.NewEncoder(w).Encode(map[string]interface{}{
					"ID":                "engine-daemon-uuid-12345",
					"Name":              "docker-node-prod-01",
					"OSType":            "linux",
					"OperatingSystem":   "Ubuntu 24.04 LTS",
					"Architecture":      "x86_64",
					"ServerVersion":     "26.1.4",
					"NCPU":              8,
					"MemTotal":          16777216000,
					"Containers":        3,
					"ContainersRunning": 2,
					"ContainersPaused":  0,
					"ContainersStopped": 1,
					"Images":            12,
				})
				return
			}
			if r.URL.Path == "/containers/json" {
				if r.URL.Query().Get("all") != "true" {
					w.WriteHeader(http.StatusBadRequest)
					return
				}
				w.WriteHeader(http.StatusOK)
				_ = json.NewEncoder(w).Encode([]map[string]interface{}{
					{
						"Id":      "c111111111112222222233333333444444445555555566666666777777778888",
						"Names":   []string{"/web-proxy", "/nginx-alias"},
						"Image":   "nginx:1.25-alpine",
						"ImageID": "sha256:bb1234567890abcdef1234567890abcdef",
						"Command": "nginx -g 'daemon off;'",
						"Created": 1725100000,
						"State":   "running",
						"Status":  "Up 5 hours",
						"Ports": []map[string]interface{}{
							{
								"IP":          "0.0.0.0",
								"PrivatePort": 80,
								"PublicPort":  8080,
								"Type":        "tcp",
							},
							{
								"PrivatePort": 443,
								"Type":        "tcp",
							},
						},
						"Labels": map[string]string{
							"com.docker.compose.project": "webstack",
						},
						"NetworkSettings": map[string]interface{}{
							"Networks": map[string]interface{}{
								"bridge": map[string]interface{}{
									"NetworkID":   "net-bridge-01",
									"Gateway":     "172.17.0.1",
									"IPAddress":   "172.17.0.2",
									"IPPrefixLen": 16,
									"MacAddress":  "02:42:ac:11:00:02",
								},
								"custom_overlay": map[string]interface{}{
									"NetworkID":   "net-overlay-02",
									"Gateway":     "10.0.1.1",
									"IPAddress":   "10.0.1.5",
									"IPPrefixLen": 24,
									"MacAddress":  "02:42:0a:00:01:05",
								},
							},
						},
					},
					{
						"Id":      "c222222222223333333344444444555555556666666677777777888888889999",
						"Names":   []string{"/db-postgres"},
						"Image":   "registry.local:5000/data/postgres:16.1@sha256:deadbeef123456",
						"ImageID": "sha256:deadbeef123456",
						"Command": "postgres",
						"Created": 1725000000,
						"State":   "exited",
						"Status":  "Exited (0) 2 hours ago",
						"Ports":   []map[string]interface{}{},
						"NetworkSettings": map[string]interface{}{
							"Networks": map[string]interface{}{
								"backend_net": map[string]interface{}{
									"NetworkID":   "net-backend-03",
									"IPAddress":   "192.168.99.10",
									"IPPrefixLen": 24,
									"MacAddress":  "02:42:c0:a8:63:0a",
								},
							},
						},
					},
					{
						"Id":      "c333333333334444444455555555666666667777777788888888999999990000",
						"Names":   []string{},
						"Image":   "redis",
						"ImageID": "",
						"Command": "redis-server",
						"Created": 1724900000,
						"State":   "paused",
						"Status":  "Paused",
						"Ports":   []map[string]interface{}{},
						"NetworkSettings": map[string]interface{}{
							"Networks": map[string]interface{}{},
						},
					},
				})
				return
			}
			w.WriteHeader(http.StatusNotFound)
		}))
		defer ts.Close()

		cfg := sdk.ProviderConfig{
			"tcp_url": ts.URL,
			"scope":   "lab-cluster",
		}

		devices, err := provider.Discover(context.Background(), cfg)
		if err != nil {
			t.Fatalf("expected nil error on Discover, got %v", err)
		}
		if len(devices) != 4 {
			t.Fatalf("expected 4 devices (1 host + 3 containers), got %d", len(devices))
		}

		// A multi-homed container must elect the same primary address on every run:
		// Go randomizes map iteration, so an unordered election would make the device
		// change identity between discovery cycles.
		for i := 0; i < 25; i++ {
			repeat, err := provider.Discover(context.Background(), cfg)
			if err != nil {
				t.Fatalf("expected nil error on repeated Discover, got %v", err)
			}
			if repeat[1].IPAddress != devices[1].IPAddress || repeat[1].MACAddress != devices[1].MACAddress {
				t.Fatalf(
					"primary address is not deterministic: got %s / %s, want %s / %s",
					repeat[1].IPAddress, repeat[1].MACAddress, devices[1].IPAddress, devices[1].MACAddress,
				)
			}
		}

		// Verify Host Device
		hostDev := devices[0]
		if hostDev.Hostname != "docker-node-prod-01" {
			t.Errorf("expected hostname docker-node-prod-01, got %s", hostDev.Hostname)
		}
		if hostDev.DeviceType != "server" {
			t.Errorf("expected device type server, got %s", hostDev.DeviceType)
		}
		if hostDev.Vendor != "Docker" {
			t.Errorf("expected vendor Docker, got %s", hostDev.Vendor)
		}
		if hostDev.OSName != "linux" || hostDev.OSVersion != "Ubuntu 24.04 LTS" {
			t.Errorf("unexpected host OS info: %s / %s", hostDev.OSName, hostDev.OSVersion)
		}

		if hostDev.ProviderRef == nil || hostDev.ProviderRef.Key() != "docker:lab-cluster:engine:engine-daemon-uuid-12345" {
			t.Errorf("unexpected host provider_ref: %v", hostDev.ProviderRef)
		}
		if hostDev.ParentProviderRef != nil {
			t.Errorf("expected engine host to have no parent, got %v", hostDev.ParentProviderRef)
		}

		hostDocker, ok := hostDev.Metadata["docker"].(map[string]interface{})
		if !ok || hostDocker["is_host"] != true || hostDocker["server_version"] != "26.1.4" {
			t.Errorf("unexpected host docker metadata: %v", hostDocker)
		}

		// Verify Container 1 (Running with ports, multi-net, labels)
		c1 := devices[1]
		if c1.Hostname != "web-proxy" {
			t.Errorf("expected hostname web-proxy, got %s", c1.Hostname)
		}
		if c1.DeviceType != "container" {
			t.Errorf("expected device type container, got %s", c1.DeviceType)
		}
		if c1.IPAddress != "172.17.0.2" {
			t.Errorf("expected primary IP 172.17.0.2, got %s", c1.IPAddress)
		}
		if c1.MACAddress != "02:42:ac:11:00:02" {
			t.Errorf("expected primary MAC 02:42:ac:11:00:02, got %s", c1.MACAddress)
		}

		wantC1Ref := "docker:lab-cluster:container:c111111111112222222233333333444444445555555566666666777777778888"
		if c1.ProviderRef == nil || c1.ProviderRef.Key() != wantC1Ref {
			t.Errorf("unexpected container 1 provider_ref: %v", c1.ProviderRef)
		}
		if c1.ParentProviderRef == nil || c1.ParentProviderRef.Key() != "docker:lab-cluster:engine:engine-daemon-uuid-12345" {
			t.Errorf("unexpected container 1 parent_provider_ref: %v", c1.ParentProviderRef)
		}

		c1Docker, ok := c1.Metadata["docker"].(map[string]interface{})
		if !ok {
			t.Fatalf("expected docker namespace in metadata")
		}
		if c1Docker["power_state"] != "running" {
			t.Errorf("expected power_state running, got %v", c1Docker["power_state"])
		}
		if c1Docker["image_repo"] != "nginx" || c1Docker["image_tag"] != "1.25-alpine" {
			t.Errorf("unexpected image parsed: repo=%v, tag=%v", c1Docker["image_repo"], c1Docker["image_tag"])
		}

		portMaps, ok := c1Docker["port_mappings"].([]map[string]interface{})
		if !ok || len(portMaps) != 2 {
			t.Fatalf("expected 2 port mappings, got %v", c1Docker["port_mappings"])
		}
		if portMaps[0]["container_port"] != 80 || portMaps[0]["host_port"] != 8080 {
			t.Errorf("unexpected port mapping 0: %v", portMaps[0])
		}

		// Verify Container 2 (Exited, custom registry image, digest)
		c2 := devices[2]
		if c2.Hostname != "db-postgres" {
			t.Errorf("expected hostname db-postgres, got %s", c2.Hostname)
		}
		c2Docker := c2.Metadata["docker"].(map[string]interface{})
		if c2Docker["power_state"] != "exited" {
			t.Errorf("expected power_state exited, got %v", c2Docker["power_state"])
		}
		if c2Docker["image_repo"] != "registry.local:5000/data/postgres" || c2Docker["image_tag"] != "16.1" {
			t.Errorf("unexpected image repo/tag: %v / %v", c2Docker["image_repo"], c2Docker["image_tag"])
		}
		if c2Docker["image_digest"] != "sha256:deadbeef123456" {
			t.Errorf("unexpected image digest: %v", c2Docker["image_digest"])
		}

		// Verify Container 3 (Paused, unnamed -> short ID, default tag)
		c3 := devices[3]
		if c3.Hostname != "c33333333333" {
			t.Errorf("expected short ID hostname c33333333333, got %s", c3.Hostname)
		}
		c3Docker := c3.Metadata["docker"].(map[string]interface{})
		if c3Docker["power_state"] != "paused" {
			t.Errorf("expected power_state paused, got %v", c3Docker["power_state"])
		}
		if c3Docker["image_repo"] != "redis" || c3Docker["image_tag"] != "latest" {
			t.Errorf("unexpected image repo/tag for c3: %v / %v", c3Docker["image_repo"], c3Docker["image_tag"])
		}
	})

	t.Run("Discover Info Failure Isolates Error and Falls Back", func(t *testing.T) {
		ts := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
			if r.URL.Path == "/info" {
				w.WriteHeader(http.StatusInternalServerError)
				return
			}
			if r.URL.Path == "/containers/json" {
				w.WriteHeader(http.StatusOK)
				_ = json.NewEncoder(w).Encode([]map[string]interface{}{
					{
						"Id":    "abc1234567890",
						"Names": []string{"/app"},
						"Image": "busybox",
						"State": "created",
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
			t.Fatalf("expected 1 device (container only, host omitted on failure), got %d", len(devices))
		}
		if devices[0].Hostname != "app" {
			t.Errorf("expected hostname app, got %s", devices[0].Hostname)
		}

		cDocker := devices[0].Metadata["docker"].(map[string]interface{})
		if cDocker["power_state"] != "stopped" {
			t.Errorf("expected power_state stopped for created container, got %v", cDocker["power_state"])
		}
	})

	t.Run("Discover Containers Request Failure", func(t *testing.T) {
		ts := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
			if r.URL.Path == "/info" {
				w.WriteHeader(http.StatusOK)
				_ = json.NewEncoder(w).Encode(map[string]interface{}{"Name": "host"})
				return
			}
			if r.URL.Path == "/containers/json" {
				w.WriteHeader(http.StatusInternalServerError)
				return
			}
		}))
		defer ts.Close()

		cfg := sdk.ProviderConfig{"tcp_url": ts.URL}
		if _, err := provider.Discover(context.Background(), cfg); err == nil {
			t.Error("expected error when /containers/json fails")
		}
	})

	t.Run("Discover Containers Invalid JSON", func(t *testing.T) {
		ts := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
			if r.URL.Path == "/info" {
				w.WriteHeader(http.StatusOK)
				_ = json.NewEncoder(w).Encode(map[string]interface{}{"Name": "host"})
				return
			}
			if r.URL.Path == "/containers/json" {
				w.WriteHeader(http.StatusOK)
				_, _ = w.Write([]byte("invalid json"))
				return
			}
		}))
		defer ts.Close()

		cfg := sdk.ProviderConfig{"tcp_url": ts.URL}
		if _, err := provider.Discover(context.Background(), cfg); err == nil {
			t.Error("expected error when /containers/json is invalid JSON")
		}
	})

	t.Run("TCP Transport with tcp:// Scheme", func(t *testing.T) {
		ts := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
			if r.URL.Path == "/_ping" {
				w.WriteHeader(http.StatusOK)
				return
			}
			w.WriteHeader(http.StatusNotFound)
		}))
		defer ts.Close()

		tcpAddr := "tcp://" + ts.Listener.Addr().String()
		cfg := sdk.ProviderConfig{"tcp_url": tcpAddr}

		if err := provider.HealthCheck(context.Background(), cfg); err != nil {
			t.Fatalf("expected tcp:// address to resolve and succeed, got %v", err)
		}
	})

	t.Run("TLS Transport with Certificates and CA", func(t *testing.T) {
		caCertPEM, caKeyPEM, certPEM, keyPEM := generateTestCertificates(t)

		tlsCert, err := tls.X509KeyPair(certPEM, keyPEM)
		if err != nil {
			t.Fatalf("failed to parse tls cert: %v", err)
		}

		ts := httptest.NewUnstartedServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
			if r.URL.Path == "/_ping" {
				w.WriteHeader(http.StatusOK)
				return
			}
			w.WriteHeader(http.StatusNotFound)
		}))
		ts.TLS = &tls.Config{
			Certificates: []tls.Certificate{tlsCert},
			MinVersion:   tls.VersionTLS12,
		}
		ts.StartTLS()
		defer ts.Close()

		cfg := sdk.ProviderConfig{
			"tcp_url":    "tcp://" + ts.Listener.Addr().String(),
			"tls_cert":   string(certPEM),
			"tls_key":    string(keyPEM),
			"tls_ca":     string(caCertPEM),
			"verify_ssl": true,
		}

		if err := provider.HealthCheck(context.Background(), cfg); err != nil {
			t.Fatalf("expected TLS health check with CA and client cert to succeed, got %v", err)
		}

		// Test invalid key pair error
		badKeyCfg := sdk.ProviderConfig{
			"tcp_url":  ts.URL,
			"tls_cert": "invalid-pem",
			"tls_key":  string(caKeyPEM),
		}
		if err := provider.HealthCheck(context.Background(), badKeyCfg); err == nil {
			t.Error("expected error when invalid TLS cert/key PEM is passed")
		}
	})

	t.Run("Unix Domain Socket Transport", func(t *testing.T) {
		socketPath := filepath.Join("/tmp", fmt.Sprintf("dockertest_%d.sock", time.Now().UnixNano()))
		_ = os.Remove(socketPath)
		defer func() { _ = os.Remove(socketPath) }()

		l, err := net.Listen("unix", socketPath)
		if err != nil {
			t.Fatalf("failed to listen on unix socket: %v", err)
		}
		defer func() { _ = l.Close() }()

		mux := http.NewServeMux()
		mux.HandleFunc("/_ping", func(w http.ResponseWriter, _ *http.Request) {
			w.WriteHeader(http.StatusOK)
		})
		mux.HandleFunc("/info", func(w http.ResponseWriter, _ *http.Request) {
			_ = json.NewEncoder(w).Encode(map[string]interface{}{"Name": "unix-host"})
		})
		mux.HandleFunc("/containers/json", func(w http.ResponseWriter, _ *http.Request) {
			_ = json.NewEncoder(w).Encode([]map[string]interface{}{
				{"Id": "u123456789012", "Names": []string{"/sock-app"}, "Image": "alpine", "State": "restarting"},
			})
		})

		server := &http.Server{Handler: mux}
		go func() { _ = server.Serve(l) }()
		defer func() { _ = server.Close() }()

		cfg := sdk.ProviderConfig{
			"socket_path": socketPath,
		}

		if err := provider.HealthCheck(context.Background(), cfg); err != nil {
			t.Fatalf("expected unix socket HealthCheck to succeed, got %v", err)
		}

		devices, err := provider.Discover(context.Background(), cfg)
		if err != nil {
			t.Fatalf("expected unix socket Discover to succeed, got %v", err)
		}
		if len(devices) != 2 {
			t.Fatalf("expected 2 devices from unix socket discovery, got %d", len(devices))
		}
		if devices[0].Hostname != "unix-host" || devices[1].Hostname != "sock-app" {
			t.Errorf("unexpected hostnames from unix socket discovery: %v", devices)
		}
		cDocker := devices[1].Metadata["docker"].(map[string]interface{})
		if cDocker["power_state"] != "running" {
			t.Errorf("expected restarting container power_state to be running, got %v", cDocker["power_state"])
		}

		// Also test unix:// prefix in api_url fallback
		prefixCfg := sdk.ProviderConfig{
			"api_url": "unix://" + socketPath,
		}
		if err := provider.HealthCheck(context.Background(), prefixCfg); err != nil {
			t.Errorf("expected unix:// in api_url to succeed, got %v", err)
		}
	})

	t.Run("Invalid Host Name and Control Characters", func(t *testing.T) {
		badCfg := sdk.ProviderConfig{"tcp_url": "http://example.com\r\n/injection"}
		if err := provider.HealthCheck(context.Background(), badCfg); err == nil {
			t.Error("expected error for URL with CRLF injection")
		}
	})
}

// generateTestCertificates creates an ephemeral self-signed CA and server certificate for testing.
func generateTestCertificates(t *testing.T) (caCertPEM, caKeyPEM, certPEM, keyPEM []byte) {
	t.Helper()

	caKey, err := rsa.GenerateKey(rand.Reader, 2048)
	if err != nil {
		t.Fatalf("failed to generate CA key: %v", err)
	}

	caTemplate := &x509.Certificate{
		SerialNumber: big.NewInt(1),
		Subject: pkix.Name{
			Organization: []string{"InfraMap Test CA"},
		},
		NotBefore:             time.Now().Add(-1 * time.Hour),
		NotAfter:              time.Now().Add(24 * time.Hour),
		IsCA:                  true,
		KeyUsage:              x509.KeyUsageCertSign | x509.KeyUsageDigitalSignature,
		BasicConstraintsValid: true,
	}

	caCertBytes, err := x509.CreateCertificate(rand.Reader, caTemplate, caTemplate, &caKey.PublicKey, caKey)
	if err != nil {
		t.Fatalf("failed to create CA cert: %v", err)
	}

	caCertPEM = pem.EncodeToMemory(&pem.Block{Type: "CERTIFICATE", Bytes: caCertBytes})
	caKeyPEM = pem.EncodeToMemory(&pem.Block{Type: "RSA PRIVATE KEY", Bytes: x509.MarshalPKCS1PrivateKey(caKey)})

	serverKey, err := rsa.GenerateKey(rand.Reader, 2048)
	if err != nil {
		t.Fatalf("failed to generate server key: %v", err)
	}

	serverTemplate := &x509.Certificate{
		SerialNumber: big.NewInt(2),
		Subject: pkix.Name{
			Organization: []string{"InfraMap Test Server"},
		},
		IPAddresses:           []net.IP{net.ParseIP("127.0.0.1")},
		NotBefore:             time.Now().Add(-1 * time.Hour),
		NotAfter:              time.Now().Add(24 * time.Hour),
		KeyUsage:              x509.KeyUsageDigitalSignature | x509.KeyUsageKeyEncipherment,
		ExtKeyUsage:           []x509.ExtKeyUsage{x509.ExtKeyUsageServerAuth, x509.ExtKeyUsageClientAuth},
		BasicConstraintsValid: true,
	}

	serverCertBytes, err := x509.CreateCertificate(rand.Reader, serverTemplate, caTemplate, &serverKey.PublicKey, caKey)
	if err != nil {
		t.Fatalf("failed to create server cert: %v", err)
	}

	certPEM = pem.EncodeToMemory(&pem.Block{Type: "CERTIFICATE", Bytes: serverCertBytes})
	keyPEM = pem.EncodeToMemory(&pem.Block{Type: "RSA PRIVATE KEY", Bytes: x509.MarshalPKCS1PrivateKey(serverKey)})

	return caCertPEM, caKeyPEM, certPEM, keyPEM
}

func TestDockerProvider_TLSVerificationIsOnByDefault(t *testing.T) {
	// The schema default and the runtime default must agree on "verify", so an absent or
	// mistyped verify_ssl can never be the reason a TCP daemon is trusted blindly
	// (CONTEXT.md guideline #174).
	provider := docker.NewProvider()

	var field *sdk.ConfigField
	fields := provider.ConfigSchema().Fields
	for i := range fields {
		if fields[i].Key == "verify_ssl" {
			field = &fields[i]
			break
		}
	}
	if field == nil {
		t.Fatal("expected a verify_ssl field in the config schema")
	}
	if field.Default != true {
		t.Errorf("verify_ssl default = %v, want true", field.Default)
	}

	ts := httptest.NewTLSServer(http.HandlerFunc(func(w http.ResponseWriter, _ *http.Request) {
		w.WriteHeader(http.StatusOK)
	}))
	defer ts.Close()

	cfg := sdk.ProviderConfig{"tcp_url": ts.URL}
	if err := provider.HealthCheck(context.Background(), cfg); err == nil {
		t.Error("expected the self-signed certificate to be rejected when verify_ssl is unset")
	}
}
